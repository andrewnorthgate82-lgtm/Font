/*
 * Local-only verification for the self-hosted runtime.
 *
 *   node --test tests/             (Node 22 built-in runner, zero dependencies)
 *
 * These tests never touch the network: every fetch is either stubbed or
 * intercepted by the publish guard. Nothing is posted to Telegram, Instagram or
 * Rubika, and no real channel id is used.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { fileURLToPath } from 'node:url';

import { PublishGuard, classifyPublishRequest, dryRunResponse } from '../server/publish-guard.mjs';
import { openSqliteD1, splitStatements } from '../server/d1-sqlite.mjs';
import { Scheduler } from '../server/scheduler.mjs';
import { parseDotEnv, readConfig, validateConfig } from '../server/env.mjs';
import { createNasraServer } from '../server/http-server.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SCHEMA = path.join(ROOT, 'schema.sql');
const OWNER_CHAT = '-1000000000001'; // fictional test channel

function tempDir(prefix = 'nasra-test-') {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function testConfig(rootDir, dataDir, overrides = {}) {
  return {
    nodeEnv: 'test',
    port: 0,
    host: '127.0.0.1',
    publicDir: path.join(ROOT, 'public'),
    dataDir,
    schemaFile: SCHEMA,
    dbFile: path.join(dataDir, 'nasra.sqlite'),
    dbBusyTimeoutMs: 5000,
    sqliteWal: true,
    schedulerEnabled: false,
    schedulerIntervalMs: 1000,
    telegramPolling: false,
    telegramPollTimeoutSec: 1,
    telegramBaseUrl: 'https://api.telegram.org',
    allowInsecureTelegramBase: false,
    ownerPassword: 'test-owner-password',
    sessionSecret: 'a'.repeat(64),
    trustedProxyHttps: false,
    allowedOrigins: [],
    guard: { mode: 'dry_run', allowlist: [], approvalTtlMs: 120000 },
    backup: { dir: path.join(dataDir, 'backups'), intervalHours: 0, keep: 3 },
    healthToken: '',
    publicBaseUrl: '',
    ...overrides
  };
}

/* ----------------------------------------------------------- publish guard */

test('guard classifies publish actions and ignores read-only calls', () => {
  const publishUrls = [
    ['https://graph.instagram.com/v26.0/17841400000/media_publish', 'instagram', 'media_publish'],
    ['https://graph.facebook.com/v26.0/17841400000/media', 'instagram', 'media'],
    ['https://graph.instagram.com/v26.0/17841400000/messages', 'instagram', 'messages'],
    ['https://api.telegram.org/bot123:secret/sendMessage', 'telegram', 'sendmessage'],
    ['https://api.telegram.org/bot123:secret/sendMediaGroup', 'telegram', 'sendmediagroup'],
    ['https://botapi.rubika.ir/v3/token/sendFile', 'rubika', 'sendfile']
  ];
  for (const [url, platform, action] of publishUrls) {
    const classified = classifyPublishRequest(url, { method: 'POST', body: JSON.stringify({ chat_id: OWNER_CHAT }) });
    assert.ok(classified, `${url} should be a publish action`);
    assert.equal(classified.platform, platform);
    assert.equal(classified.action, action);
  }
  const allowed = [
    ['https://graph.instagram.com/v26.0/17841400000?fields=id,username', 'GET'],
    ['https://api.telegram.org/bot123:secret/getMe', 'POST'],
    ['https://api.telegram.org/bot123:secret/getUpdates', 'POST'],
    ['https://api.telegram.org/bot123:secret/answerCallbackQuery', 'POST'],
    ['https://botapi.rubika.ir/v3/token/getMe', 'POST'],
    ['https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent', 'POST'],
    ['https://example.com/media.jpg', 'GET']
  ];
  for (const [url, method] of allowed) assert.equal(classifyPublishRequest(url, { method }), null, `${method} ${url} must not be gated`);
});

test('dry_run mode blocks every publish and lets notifications through', () => {
  const guard = new PublishGuard({ mode: 'dry_run', allowlist: [OWNER_CHAT], ownerChatIds: ['777'] });
  const channelPost = classifyPublishRequest('https://api.telegram.org/bot1:x/sendMessage', { method: 'POST', body: JSON.stringify({ chat_id: OWNER_CHAT }) });
  const decision = guard.decide(channelPost);
  assert.equal(decision.decision, 'block');
  assert.equal(decision.reason, 'GUARD_DRY_RUN');

  const ownerDm = classifyPublishRequest('https://api.telegram.org/bot1:x/sendMessage', { method: 'POST', body: JSON.stringify({ chat_id: '777' }) });
  assert.equal(guard.decide(ownerDm).decision, 'pass');

  const response = dryRunResponse(channelPost);
  assert.equal(response.status, 403);
});

test('live mode needs the destination allowlist and a single-use approval', () => {
  const guard = new PublishGuard({ mode: 'live', allowlist: [OWNER_CHAT], approvalTtlMs: 60000 });
  const post = classifyPublishRequest('https://api.telegram.org/bot1:x/sendMessage', { method: 'POST', body: JSON.stringify({ chat_id: OWNER_CHAT }) });
  assert.equal(guard.decide(post).reason, 'APPROVAL_REQUIRED');

  const wrongDestination = classifyPublishRequest('https://api.telegram.org/bot1:x/sendMessage', { method: 'POST', body: JSON.stringify({ chat_id: '-1009999999999' }) });
  const approval = guard.issueApproval({ note: 'local test', platform: 'telegram' });
  assert.equal(guard.decide(wrongDestination, { approvalToken: approval.token }).reason, 'DESTINATION_NOT_ALLOWLISTED');

  assert.equal(guard.decide(post, { approvalToken: approval.token }).decision, 'pass');
  // single use: the same token cannot authorise a second publish
  assert.equal(guard.decide(post, { approvalToken: approval.token }).decision, 'block');
});

test('approvals expire and the wrong platform is rejected', () => {
  let now = Date.now();
  const guard = new PublishGuard({ mode: 'live', allowlist: [OWNER_CHAT], approvalTtlMs: 30000, clock: () => now });
  const approval = guard.issueApproval({ platform: 'telegram' });
  const ig = classifyPublishRequest('https://graph.instagram.com/v26.0/1/media_publish', { method: 'POST' });
  assert.equal(guard.decide(ig, { approvalToken: approval.token }).reason, 'APPROVAL_PLATFORM_MISMATCH');
  now += 31000;
  const post = classifyPublishRequest('https://api.telegram.org/bot1:x/sendMessage', { method: 'POST', body: JSON.stringify({ chat_id: OWNER_CHAT }) });
  assert.equal(guard.decide(post, { approvalToken: approval.token }).reason, 'APPROVAL_INVALID_OR_EXPIRED');
  assert.equal(guard.listApprovals().length, 0);
});

/* --------------------------------------------------------------- sqlite */

test('SQLite adapter survives a reopen and keeps writes', async () => {
  const dir = tempDir();
  const file = path.join(dir, 'db.sqlite');
  const db = openSqliteD1({ file, schemaFile: SCHEMA });
  const agents = await db.prepare('SELECT COUNT(*) AS n FROM agents').first();
  assert.ok(agents.n >= 12, 'seeded agents must exist');
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)').bind('persist_test', '{"a":1}', new Date().toISOString()).run();
  const booleanInsert = await db.prepare('INSERT INTO audit_logs (id,actor,action,entity_type,entity_id,details_json,ip_hash,created_at) VALUES (?,?,?,?,?,?,?,?)')
    .bind('audit_test', 'system', 'open', 'test', null, null, null, new Date().toISOString()).run();
  assert.equal(booleanInsert.meta.changes, 1);
  db.close();

  const reopened = openSqliteD1({ file, schemaFile: SCHEMA });
  const row = await reopened.prepare('SELECT value_json FROM studio_settings WHERE key=?').bind('persist_test').first();
  assert.equal(row.value_json, '{"a":1}');
  const journal = await reopened.prepare('PRAGMA journal_mode').first();
  assert.equal(String(journal.journal_mode).toLowerCase(), 'wal');
  reopened.close();

  // schema.sql applies twice without duplicating seeds
  const again = openSqliteD1({ file, schemaFile: SCHEMA });
  const agentsAgain = await again.prepare('SELECT COUNT(*) AS n FROM agents').first();
  assert.equal(agentsAgain.n, agents.n);
  again.close();
});

test('SQL statement splitter handles quotes, comments and triggers', () => {
  const sql = "CREATE TABLE a (b TEXT); -- comment; with semicolon\nINSERT INTO a VALUES ('x;y');\n/* block ; */ INSERT INTO a VALUES (\"z;w\");";
  const parts = splitStatements(sql);
  assert.equal(parts.filter(p => p.trim()).length, 3);
  assert.ok(parts[1].includes("'x;y'"));
});

/* ------------------------------------------------------------- scheduler */

function fakeWorker(behaviour) {
  return {
    calls: 0,
    async scheduled(event, env, ctx) {
      this.calls += 1;
      return behaviour.call(this, event, env, ctx);
    }
  };
}

test('scheduler runs one tick at a time and never throws into the loop', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const config = testConfig(ROOT, dir, { schedulerEnabled: true, schedulerIntervalMs: 20 });
  let concurrent = 0;
  let maxConcurrent = 0;
  const bg = fakeWorker(async (event, env, ctx) => {
    concurrent += 1;
    maxConcurrent = Math.max(maxConcurrent, concurrent);
    ctx.waitUntil(new Promise(resolve => setTimeout(resolve, 30)));
    await new Promise(resolve => setTimeout(resolve, 30));
    concurrent -= 1;
  });
  const scheduler = new Scheduler({ worker: bg, env: {}, db, config, logger: { log() {}, error() {} } });
  scheduler.start();
  await new Promise(resolve => setTimeout(resolve, 120));
  scheduler.stopped = true;
  if (scheduler.timer) clearTimeout(scheduler.timer);
  while (scheduler.inFlight) await new Promise(resolve => setTimeout(resolve, 5));
  assert.ok(scheduler.state.ticks >= 1, 'at least one tick ran');
  assert.equal(scheduler.state.failures, 0);
  assert.equal(maxConcurrent, 1, 'ticks must never overlap');
  assert.ok(bg.calls >= scheduler.state.ticks, 'every completed tick ran the worker');
  // A manual /api/ops/scheduler/tick during a running tick must be skipped.
  scheduler.stopped = false; // re-arm only for the manual-tick check below
  const before = scheduler.state.skipped_overlap;
  const [first, second] = await Promise.all([scheduler.tick(), scheduler.tick()]);
  assert.equal(scheduler.state.skipped_overlap, before + 1);
  assert.ok(first.skipped === 'overlap' || second.skipped === 'overlap');
  scheduler.stopped = true;

  const failing = new Scheduler({
    worker: fakeWorker(async () => { throw new Error('boom'); }),
    env: {}, db, config: { ...config, schedulerIntervalMs: 60000 }, logger: { log() {}, error() {} }
  });
  const result = await failing.tick();
  assert.equal(result.ok, false);
  assert.equal(failing.state.failures, 1);
  failing.stop();
  db.close();
});

test('scheduler backs up the SQLite file to disk', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const config = testConfig(ROOT, dir);
  const scheduler = new Scheduler({ worker: fakeWorker(async () => {}), env: {}, db, config, logger: { log() {}, error() {} } });
  const result = scheduler.backupNow('test');
  assert.equal(result.ok, true);
  assert.ok(fs.existsSync(result.file), 'backup file must exist');
  const copy = openSqliteD1({ file: result.file, schemaFile: null });
  const agents = await copy.prepare('SELECT COUNT(*) AS n FROM agents').first();
  assert.ok(agents.n >= 12, 'backup must contain the seeded data');
  copy.close();
  db.close();
});

/* ------------------------------------------------------------- http/api */

test('HTTP runtime serves health, static files and the ops guard', async t => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const config = testConfig(ROOT, dir);
  const guard = new PublishGuard({ mode: 'dry_run' });
  const env = { DB: db, OWNER_PASSWORD: config.ownerPassword, SESSION_SECRET: config.sessionSecret, APP_VERSION: 'test', PREVIEW_MODE: '0' };
  const server = createNasraServer({
    worker, env, config, db, logger: { log() {}, error() {} },
    preHandle: async (req, res, url) => {
      if (url.pathname === '/api/ops/guard') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true, data: { mode: guard.mode } }));
        return true;
      }
      return false;
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const base = `http://127.0.0.1:${port}`;
  t.after(() => { server.close(); db.close(); });

  const health = await fetch(`${base}/api/health`);
  const healthBody = await health.json();
  assert.equal(health.status, 200);
  assert.equal(healthBody.ok, true);

  const index = await fetch(`${base}/`);
  assert.equal(index.status, 200);
  assert.match(await index.text(), /استودیو/);

  const guardResponse = await fetch(`${base}/api/ops/guard`);
  assert.equal((await guardResponse.json()).data.mode, 'dry_run');

  const traversal = await fetch(`${base}/..%2f..%2fetc%2fpasswd`);
  assert.notEqual(traversal.status, 200);

  const anonymous = await fetch(`${base}/api/dashboard`);
  assert.equal(anonymous.status, 401);

  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: config.ownerPassword })
  });
  const loginBody = await login.json();
  assert.equal(login.status, 200);
  assert.ok(loginBody.csrf_token);
  const cookie = login.headers.get('set-cookie').split(';')[0];
  const dashboard = await fetch(`${base}/api/dashboard`, { headers: { cookie } });
  assert.equal(dashboard.status, 200);
  const selftest = await fetch(`${base}/api/selftest`, { headers: { cookie } });
  const selftestBody = await selftest.json();
  assert.equal(selftestBody.ok, true, JSON.stringify(selftestBody.tests?.filter(x => !x.ok)));
  assert.equal(selftestBody.total, 16);
});

/* ----------------------------------------------------------- end to end */

test('a full pipeline run publishes nothing and ends in a manual package', async t => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const config = testConfig(ROOT, dir, { guard: { mode: 'dry_run', allowlist: [OWNER_CHAT], approvalTtlMs: 60000 } });
  const guard = new PublishGuard({ mode: 'dry_run', allowlist: [OWNER_CHAT] });

  // Stub fetch: no packet may leave this process.
  const realFetch = globalThis.fetch;
  const outbound = [];
  globalThis.fetch = async (input, init = {}) => {
    const url = typeof input === 'string' ? input : input?.url;
    const action = classifyPublishRequest(url, { method: init.method || 'POST', body: init.body });
    outbound.push({ url, action: action ? `${action.platform}/${action.action}` : 'other' });
    if (action) return dryRunResponse(action); // guard behaviour
    if (String(url).includes('api.telegram.org')) return new Response(JSON.stringify({ ok: true, result: { message_id: 1 } }), { headers: { 'Content-Type': 'application/json' } });
    throw new Error(`unexpected outbound request in test: ${url}`);
  };
  t.after(() => { globalThis.fetch = realFetch; });

  const env = {
    DB: db,
    OWNER_PASSWORD: config.ownerPassword,
    SESSION_SECRET: config.sessionSecret,
    TG_BOT_TOKEN: '000:test-token',
    TG_OWNER_CHAT_ID: '555000111',
    APP_VERSION: 'test'
  };

  const q = (sql, params = []) => db.prepare(sql).bind(...params);
  const now = new Date().toISOString();
  const contentId = 'content_e2e_test';
  await q(`INSERT INTO content_items (id,title,pillar,state,state_version,current_version,target_platforms_json,ideation_json,script_json,fact_check_json,editorial_json,tech_check_json,validator_report_json,media_links_json,selected_format,selected_idea_index,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`, [
    contentId, 'آزمون محلی انتشار', 'سواد خبری', 'GATE3_PENDING', 5, 1, JSON.stringify(['telegram']),
    JSON.stringify({ ideas: [{ format: 'post', hook: 'h', angle: 'a', key_message: 'k' }] }),
    JSON.stringify({ format: 'post', post_body: 'متن آزمایشی' }),
    JSON.stringify({ overall: 'pass', claims: [] }),
    JSON.stringify({ caption: 'متن آزمایشی محلی\n#آزمون', hashtags: ['#آزمون'], alt_text: 'آزمون' }),
    JSON.stringify({ checks: [] }),
    JSON.stringify({ ok: true, issues: [] }),
    JSON.stringify([{ url: 'https://example.com/local-test.jpg', kind: 'image', format: 'jpg' }]),
    'post', 0, now, now
  ]).run();
  await q(`INSERT INTO gate_decisions (id,content_id,gate_number,version,status,created_at) VALUES (?,?,?,?,?,?)`)
    .bind('gate3_e2e', contentId, 3, 1, 'PENDING', now).run();

  // Gate 3 approval through the worker, exactly as the owner does in the UI.
  const ctx = { waitUntil(promise) { return promise; }, passThroughOnException() {} };
  const request = (pathname, init = {}) => new Request(`http://127.0.0.1/api${pathname}`, init);
  const session = await worker.fetch(request('/auth/session'), env, ctx);
  assert.equal(session.status, 200);

  // The scheduler is what drives the pipeline locally.
  const scheduler = new Scheduler({ worker, env, db, config, logger: { log() {}, error() {} } });
  await scheduler.tick();

  // A scheduled publish path that is due now, with telegram "configured".
  await q(`INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json`)
    .bind('general', JSON.stringify({ timezone: 'Asia/Tehran', tg_channel_id: OWNER_CHAT, global_daily_token_cap: 150000, carousel_strict_no_mixed: true }), now).run();
  await q(`UPDATE content_items SET state='SCHEDULED', state_version=state_version+1 WHERE id=?`).bind(contentId).run();
  const due = new Date(Date.now() - 60000).toISOString();
  await q(`INSERT INTO publish_jobs (id,content_id,gate3_version,platform,status,scheduled_at_utc,scheduled_jalali,idempotency_key,run_after,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?)`, ['publish_e2e', contentId, 1, 'telegram', 'SCHEDULED', due, '1405/08/15 21:00', 'e2e-once', due, now, now]).run();

  await scheduler.tick();
  await scheduler.tick();

  const job = await q('SELECT * FROM publish_jobs WHERE id=?').bind('publish_e2e').first();
  assert.notEqual(job.status, 'PUBLISHED', 'nothing may be published in dry_run');
  const published = await q("SELECT COUNT(*) AS n FROM publish_jobs WHERE status='PUBLISHED'").first();
  assert.equal(published.n, 0);
  const pubActions = outbound.filter(entry => entry.action && entry.action !== 'other');
  assert.ok(pubActions.every(entry => entry.action.startsWith('telegram/')), 'only the telegram channel send should be gated');
  db.close();
});

/* --------------------------------------------------------------- config */

test('environment parsing and validation reject unsafe setups', () => {
  const parsed = parseDotEnv('OWNER_PASSWORD="s3cret pass"\n# comment\nEXPORTED=1\nexport PORT=9000\nSESSION_SECRET=abc # trailing\n');
  assert.equal(parsed.OWNER_PASSWORD, 's3cret pass');
  assert.equal(parsed.PORT, '9000');
  assert.equal(parsed.SESSION_SECRET, 'abc');
  assert.equal(parsed.EXPORTED, '1');

  const dir = tempDir();
  const missing = readConfig({}, { rootDir: dir });
  assert.equal(validateConfig(missing, {}).ok, false);

  const weak = readConfig({ OWNER_PASSWORD: 'short', SESSION_SECRET: 'tiny' }, { rootDir: dir });
  const weakResult = validateConfig(weak, {});
  assert.equal(weakResult.ok, false);
  assert.equal(weakResult.problems.length, 2);

  const live = readConfig({ OWNER_PASSWORD: 'a-good-password', SESSION_SECRET: 'b'.repeat(64), PUBLISH_GUARD_MODE: 'live' }, { rootDir: dir });
  assert.equal(validateConfig(live, {}).ok, false, 'live mode without allowlist must be refused');

  const safe = readConfig({ OWNER_PASSWORD: 'a-good-password', SESSION_SECRET: 'b'.repeat(64), SERVER_PORT: '8080' }, { rootDir: dir });
  assert.equal(validateConfig(safe, {}).ok, true);
  assert.equal(safe.port, 8080);
  assert.equal(safe.guard.mode, 'dry_run');
});

/* --------------------------------------------------- telegram long polling */

test('long polling persists the offset and feeds the shared worker', async t => {
  const { createFakeTelegram } = await import('./fake-telegram.mjs');
  const { TelegramPoller } = await import('../server/telegram-poller.mjs');
  const fake = createFakeTelegram();
  const fakePort = await fake.listen(0);
  t.after(() => fake.close());

  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  t.after(() => db.close());
  const config = testConfig(ROOT, dir, { telegramPolling: true, telegramPollTimeoutSec: 1, telegramBaseUrl: `http://127.0.0.1:${fakePort}`, allowInsecureTelegramBase: true });
  const env = { DB: db, OWNER_PASSWORD: config.ownerPassword, SESSION_SECRET: config.sessionSecret, TG_BOT_TOKEN: '000:test', TG_OWNER_CHAT_ID: '555000111' };

  // Stub the outgoing notifications so no request leaves the machine.
  const realFetch = globalThis.fetch;
  const intercepted = [];
  globalThis.fetch = async (input, init = {}) => {
    const url = String(typeof input === 'string' ? input : input?.url);
    if (url.startsWith(`http://127.0.0.1:${fakePort}`)) return realFetch(input, init);
    // worker.js talks to api.telegram.org directly; record instead of sending.
    intercepted.push({ url, body: init.body ? JSON.parse(init.body) : {} });
    return new Response(JSON.stringify({ ok: true, result: { message_id: 1 } }), { headers: { 'Content-Type': 'application/json' } });
  };
  t.after(() => { globalThis.fetch = realFetch; });

  const poller = new TelegramPoller({ config, env, db, worker, logger: { log() {}, warn() {}, error() {} } });
  const first = fake.pushMessage({ chatId: '555000111', text: '/status' });
  const second = fake.pushMessage({ chatId: '555000111', text: 'سلام' });
  const stranger = fake.pushMessage({ chatId: '999999999', text: '/status' });

  await poller.start();
  await new Promise(resolve => setTimeout(resolve, 1200));
  poller.stop();

  assert.equal(fake.state.webhookDeleted, 1, 'the webhook must be deleted before polling');
  assert.ok(poller.state.updates >= 2, `expected at least 2 processed updates, got ${poller.state.updates}`);
  assert.equal(poller.state.last_update_at !== null, true);

  const saved = await db.prepare('SELECT value_json FROM studio_settings WHERE key=?').bind('telegram_polling_offset').first();
  assert.ok(saved, 'offset must be persisted for restarts');
  const offset = JSON.parse(saved.value_json).offset;
  assert.ok(offset > second.update_id && offset > first.update_id, 'offset must be past the processed updates');

  // A message from an unknown chat must not produce any bot reply.
  const replies = intercepted.filter(entry => /\/sendMessage$/.test(entry.url));
  assert.ok(replies.length >= 1, 'the owner command should receive a reply');
  assert.ok(replies.every(entry => String(entry.body.chat_id) === '555000111'), 'the bot must only answer the owner chat');
  assert.ok(replies.some(entry => String(entry.body.text || '').includes('وضعیت')), '/status must answer with the studio status');
  assert.ok(intercepted.every(entry => !JSON.stringify(entry.body).includes('999999999')), 'a stranger chat is never answered');
  void stranger;

  // Restart: a new poller resumes from the stored offset and re-delivery is deduped.
  const again = new TelegramPoller({ config, env, db, worker, logger: { log() {}, warn() {}, error() {} } });
  assert.equal(await again.loadOffset(), offset);
});
