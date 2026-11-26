/*
 * Acceptance tests for Telegram-first control.
 *
 *   node --test tests/telegram-control.test.mjs
 *
 * Two claims are being pinned down, because both are easy to break silently and
 * both are expensive if they break:
 *
 *   1. The control surface can never publish. No command reaches a channel.
 *   2. Only the owner's own chat is obeyed, even with a correct command.
 *
 * Everything runs offline: handleCommand() is pure, and the pause flag is
 * exercised against a real in-memory SQLite database, not a mock.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  handleCommand,
  readPaused,
  writePaused,
  wakeCode,
  verifyWakeCode,
  writeHeartbeat,
  readHeartbeat,
  HEARTBEAT_STALE_MS,
  FORCE_TICK_TTL_MS,
  requestTick,
  consumeTickRequest
} from '../server/telegram-control.mjs';
import { mergeEnvText, parseDotEnv } from '../server/env.mjs';
import { openSqliteD1 } from '../server/d1-sqlite.mjs';
import { classifyPublishRequest } from '../server/publish-guard.mjs';
import worker from '../worker.js';
import { TelegramPoller } from '../server/telegram-poller.mjs';
import { createFakeTelegram } from './fake-telegram.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OWNER = '-1000000000001';
const STRANGER = '-1000000000999';

const fresh = { present: true, stale: false, age_ms: 1200, port: 8787 };
const dead = { present: false, stale: true };

/* ------------------------------------------------------------- ownership */

test('a stranger can never drive the studio, even with a valid command', () => {
  for (const text of ['/pause', '/resume', '/now', '/wake', '/status', '/ping']) {
    const result = handleCommand({ text, ownerChatId: OWNER, chatId: STRANGER, heartbeat: fresh });
    assert.equal(result.action, 'none', `${text} must do nothing for a stranger`);
    assert.equal(result.reply, '', `${text} must not leak studio state to a stranger`);
    assert.equal(result.ignored, 'not_owner');
  }
});

test('with no owner configured, every command is ignored', () => {
  const result = handleCommand({ text: '/pause', ownerChatId: '', chatId: OWNER, heartbeat: fresh });
  assert.equal(result.action, 'none');
  assert.equal(result.ignored, 'not_owner');
});

test('the owner can pause and resume, and a repeat is harmless', () => {
  const pause = handleCommand({ text: '/pause', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused: false });
  assert.equal(pause.action, 'pause');
  const again = handleCommand({ text: '/pause', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused: true });
  assert.equal(again.action, 'none', 'pausing twice must not fail');
  const resume = handleCommand({ text: '/resume', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused: true });
  assert.equal(resume.action, 'resume');
  const redundant = handleCommand({ text: '/resume', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused: false });
  assert.equal(redundant.action, 'none');
});

test('commands with a bot suffix still work (@name)', () => {
  const result = handleCommand({ text: '/pause@nasra_bot', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh });
  assert.equal(result.action, 'pause');
});

test('no command can publish; the strongest one only runs a local tick', () => {
  /* Two runs, because /pause and /resume are exclusive by design: each is a
     no-op when the state already matches. Every action any command can produce
     is collected, and 'publish' must never appear. */
  const actions = new Set();
  for (const paused of [false, true]) {
    for (const text of ['/start', '/help', '/ping', '/status', '/pause', '/resume', '/now', '/wake', '/whatever', 'hello']) {
      const result = handleCommand({ text, ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused });
      actions.add(result.action);
    }
  }
  assert.deepEqual([...actions].sort(), ['none', 'pause', 'resume', 'tick', 'wake'].sort());
  assert.equal(actions.has('publish'), false, 'the bot must have no publish verb at all');
});

/* ---------------------------------------------------------------- honesty */

test('/ping says the studio is off when there is no heartbeat', () => {
  const result = handleCommand({ text: '/ping', ownerChatId: OWNER, chatId: OWNER, heartbeat: dead });
  assert.match(result.reply, /خاموش/);
  assert.equal(result.action, 'none');
});

test('/ping reports a stale heartbeat as unresponsive, not alive', () => {
  const stale = { present: true, stale: true, age_ms: HEARTBEAT_STALE_MS + 60_000 };
  const result = handleCommand({ text: '/ping', ownerChatId: OWNER, chatId: OWNER, heartbeat: stale });
  assert.match(result.reply, /پاسخ نمی‌دهد/);
});

test('/ping includes the pause state so one message answers both questions', () => {
  const result = handleCommand({ text: '/ping', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh, paused: true });
  assert.match(result.reply, /متوقف/);
});

test('/now refuses to claim a tick ran when the studio is down', () => {
  const result = handleCommand({ text: '/now', ownerChatId: OWNER, chatId: OWNER, heartbeat: dead });
  assert.equal(result.action, 'none');
  assert.match(result.reply, /خاموش/);
});

test('/wake never claims the machine was switched on', () => {
  const result = handleCommand({ text: '/wake', ownerChatId: OWNER, chatId: OWNER, heartbeat: dead });
  assert.equal(result.action, 'wake', 'the request must still be recorded');
  assert.match(result.reply, /نمی‌تواند/, 'the reply must say Telegram cannot power on a device');
  assert.doesNotMatch(result.reply, /روشن شد\./);
});

/* ------------------------------------------------------------------- wake */

test('wake codes are per-day and refuse yesterday’s code', () => {
  const secret = 'wake-secret-for-tests';
  const now = Date.parse('2026-11-06T10:00:00Z');
  const today = wakeCode(secret, now);
  const yesterday = wakeCode(secret, now - 24 * 3600 * 1000);
  assert.notEqual(today, yesterday);
  assert.equal(verifyWakeCode(secret, today, now), true);
  assert.equal(verifyWakeCode(secret, yesterday, now), false, 'an old screenshot must not work');
  assert.equal(verifyWakeCode('', today, now), false, 'no secret means no remote start');
});

test('the heartbeat file is the single source of truth for liveness', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-heartbeat-'));
  assert.equal(readHeartbeat(dir).present, false);

  const now = Date.now();
  writeHeartbeat(dir, { port: 8787 }, now);
  const freshBeat = readHeartbeat(dir, now + 1000);
  assert.equal(freshBeat.present, true);
  assert.equal(freshBeat.stale, false);
  assert.equal(freshBeat.port, 8787);

  const old = readHeartbeat(dir, now + HEARTBEAT_STALE_MS + 1000);
  assert.equal(old.stale, true, 'a heartbeat older than five minutes is not alive');
});

/* -------------------------------------------------------------- the brake */

function database() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-control-db-'));
  const db = openSqliteD1({ file: path.join(dir, 'nasra.sqlite'), schemaFile: path.join(ROOT, 'schema.sql') });
  return { db, dir };
}

test('the pause flag survives a restart and defaults to not-paused', async () => {
  const { db, dir } = database();
  try {
    assert.equal(await readPaused(db), false, 'default must be publishing allowed');
    await writePaused(db, true, 'owner_telegram');
    assert.equal(await readPaused(db), true);

    db.close();
    const reopened = openSqliteD1({ file: path.join(dir, 'nasra.sqlite'), schemaFile: path.join(ROOT, 'schema.sql') });
    assert.equal(await readPaused(reopened), true, 'a restart must not clear the brake');
    await writePaused(reopened, false, 'owner_telegram');
    assert.equal(await readPaused(reopened), false);
    reopened.close();
  } finally {
    try { db.close(); } catch { /* already closed */ }
  }
});

test('/now leaves a request the loop consumes exactly once, and never when stale', async () => {
  const { db } = database();
  try {
    assert.equal((await consumeTickRequest(db)).requested, false, 'nothing pending by default');

    await requestTick(db, 'owner_telegram');
    const first = await consumeTickRequest(db);
    assert.equal(first.requested, true);
    const second = await consumeTickRequest(db);
    assert.equal(second.requested, false, 'a request must not run twice');

    await requestTick(db, 'owner_telegram');
    const late = await consumeTickRequest(db, Date.now() + FORCE_TICK_TTL_MS + 1000);
    assert.equal(late.requested, false, 'a stale request must be dropped, not replayed');
    assert.equal(late.reason, 'expired');
  } finally {
    db.close();
  }
});

test('the /now reply does not claim the cycle already ran', () => {
  const result = handleCommand({ text: '/now', ownerChatId: OWNER, chatId: OWNER, heartbeat: fresh });
  assert.equal(result.action, 'tick');
  assert.doesNotMatch(result.reply, /اجرا شد/, 'the worker only queues the request; the loop runs it');
  assert.match(result.reply, /حداکثر/);
});

test('while paused, the cron path publishes nothing and says why', async () => {
  const { db } = database();
  try {
    await writePaused(db, true, 'owner_telegram');
    const calls = [];
    const ctx = { waitUntil(promise) { calls.push(promise); }, passThroughOnException() {} };
    await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() }, { DB: db }, ctx);
    await Promise.allSettled(calls);
    const rows = await db.prepare("SELECT COUNT(*) AS n FROM publish_jobs WHERE status='PUBLISHED'").bind().first();
    assert.equal(Number(rows.n), 0, 'a paused studio must not complete a publish');
  } finally {
    db.close();
  }
});

/* ------------------------------------------------------------- no surprises */

test('the poller never deletes a webhook unless the owner explicitly allowed it', async () => {
  const fake = createFakeTelegram();
  const port = await fake.listen(0);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-poller-'));
  const db = openSqliteD1({ file: path.join(dir, 'nasra.sqlite'), schemaFile: path.join(ROOT, 'schema.sql') });
  const savedEnv = { del: process.env.TELEGRAM_DELETE_WEBHOOK, base: process.env.TELEGRAM_BASE_URL, insecure: process.env.ALLOW_INSECURE_TELEGRAM_BASE };
  try {
    delete process.env.TELEGRAM_DELETE_WEBHOOK;
    process.env.TELEGRAM_BASE_URL = `http://127.0.0.1:${port}`;
    process.env.ALLOW_INSECURE_TELEGRAM_BASE = '1';
    fake.state.webhookUrl = 'https://example.com/webhooks/telegram';

    /* A webhook is already set: the poller must look, report, and stop. */
    const blocked = new TelegramPoller({
      config: { telegramBaseUrl: `http://127.0.0.1:${port}`, telegramPollTimeoutSec: 1, telegramPolling: true, port: 0 },
      env: { TG_BOT_TOKEN: '1:test', TG_OWNER_CHAT_ID: OWNER },
      db, worker, logger: { log() {}, warn() {}, error() {} }
    });
    await blocked.start();
    assert.equal(fake.state.webhookDeleted, 0, 'an existing webhook must survive untouched');
    assert.equal(blocked.state.mode, 'webhook-present');
    assert.equal(blocked.running, false, 'polling must not fight the webhook');

    /* Explicit consent is the only path that deletes it. */
    process.env.TELEGRAM_DELETE_WEBHOOK = '1';
    /* A webhook really exists here: consent must be what removes it, and the
       removal must let polling take over. */
    fake.state.webhookUrl = 'https://example.com/webhooks/telegram';
    const consented = new TelegramPoller({
      config: { telegramBaseUrl: `http://127.0.0.1:${port}`, telegramPollTimeoutSec: 1, telegramPolling: true, port: 0 },
      env: { TG_BOT_TOKEN: '1:test', TG_OWNER_CHAT_ID: OWNER },
      db, worker, logger: { log() {}, warn() {}, error() {} }
    });
    await consented.start();
    assert.equal(fake.state.webhookDeleted, 1, 'TELEGRAM_DELETE_WEBHOOK=1 is explicit consent');
    assert.equal(consented.running, true, 'with consent, polling must start after the removal');
    consented.stop();
    delete process.env.TELEGRAM_DELETE_WEBHOOK;

    /* No webhook at all: nothing to remove, and nothing alarming to log. */
    fake.state.webhookUrl = '';
    const warned = [];
    const quiet = new TelegramPoller({
      config: { telegramBaseUrl: `http://127.0.0.1:${port}`, telegramPollTimeoutSec: 1, telegramPolling: true, port: 0 },
      env: { TG_BOT_TOKEN: '1:test', TG_OWNER_CHAT_ID: OWNER },
      db, worker, logger: { log() {}, warn: (...a) => warned.push(a.join(' ')), error() {} }
    });
    await quiet.start();
    assert.equal(fake.state.webhookDeleted, 1, 'a poller must not delete a webhook that does not exist');
    assert.equal(warned.filter(line => /webhook is left untouched/.test(line)).length, 0, 'no warning about a webhook that is not there');
    quiet.stop();

    /* With no webhook set, polling is allowed and consumes updates. */
    fake.state.webhookUrl = '';
    const poller = new TelegramPoller({
      config: { telegramBaseUrl: `http://127.0.0.1:${port}`, telegramPollTimeoutSec: 1, telegramPolling: true, port: 0 },
      env: { TG_BOT_TOKEN: '1:test', TG_OWNER_CHAT_ID: OWNER },
      db, worker, logger: { log() {}, warn() {}, error() {} }
    });
    fake.pushMessage({ chatId: OWNER, text: '/ping' });
    await poller.start();
    for (let i = 0; i < 40 && poller.state.updates === 0; i++) await new Promise(r => setTimeout(r, 50));
    assert.ok(poller.state.updates >= 1, 'an update must be consumed');
    poller.stop();
    assert.equal(fake.state.webhookDeleted, 1, 'only the consented delete happened, once');
  } finally {
    if (savedEnv.del === undefined) delete process.env.TELEGRAM_DELETE_WEBHOOK; else process.env.TELEGRAM_DELETE_WEBHOOK = savedEnv.del;
    if (savedEnv.base === undefined) delete process.env.TELEGRAM_BASE_URL; else process.env.TELEGRAM_BASE_URL = savedEnv.base;
    if (savedEnv.insecure === undefined) delete process.env.ALLOW_INSECURE_TELEGRAM_BASE; else process.env.ALLOW_INSECURE_TELEGRAM_BASE = savedEnv.insecure;
    db.close();
    await fake.close();
  }
});

/* --------------------------------------------------------- control surface */

test('a real channel send is still recognised as a publish action', () => {
  /* Sanity check on the guard the pause flag relies on: this is what a publish
     looks like, and the control module has no way to produce one. */
  const action = classifyPublishRequest('https://api.telegram.org/bot123:abc/sendMessage', {
    method: 'POST',
    body: JSON.stringify({ chat_id: OWNER, text: 'x' })
  });
  assert.equal(action.platform, 'telegram');
  assert.equal(action.action, 'sendmessage');
});

test('env merging keeps comments and unrelated keys', () => {
  const source = '# note\nOWNER_PASSWORD=\nKEEP=1\n';
  const { text, touched } = mergeEnvText(source, { OWNER_PASSWORD: 'pw', NEW_KEY: 'v' });
  assert.equal(touched, 2);
  assert.match(text, /^# note$/m);
  assert.match(text, /^KEEP=1$/m);
  const parsed = parseDotEnv(text);
  assert.equal(parsed.OWNER_PASSWORD, 'pw');
  assert.equal(parsed.NEW_KEY, 'v');
  assert.equal(parsed.KEEP, '1');
});
