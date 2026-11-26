/*
 * Acceptance tests for the autopilot layer.
 *
 *   node --test tests/autopilot.test.mjs
 *
 * Everything here is local: no image model is called (a stub is injected), no
 * Telegram/Instagram request is made, and the publish guard is left in dry_run.
 * The point is to pin the safety rules down, because on a public channel an
 * automatic approval cannot be taken back.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { Autopilot, gatePolicy, normalizePolicy, policySummary } from '../server/autopilot.mjs';
import { buildPosterPrompt, extensionForMime, ImageProvider } from '../server/image-provider.mjs';
import { MediaStore } from '../server/media-store.mjs';
import { openSqliteD1 } from '../server/d1-sqlite.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SCHEMA = path.join(ROOT, 'schema.sql');

function tempDir(prefix = 'nasra-autopilot-') {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

const cleanContent = {
  id: 'content_clean',
  title: 'چک‌لیست بررسی پیام مشکوک',
  selected_format: 'post',
  selected_idea_index: 0,
  ideation_json: JSON.stringify({ ideas: [{ format: 'post', hook: 'h', angle: 'a', key_message: 'k', risk_flags: [], needs_info: [] }] }),
  fact_check_json: JSON.stringify({ overall: 'pass', claims: [{ claim: 'c', verdict: 'تأییدشده', sources: ['s'] }], risk_flags: [], needs_info: [] }),
  validator_report_json: JSON.stringify({ ok: true, issues: [] }),
  media_links_json: JSON.stringify([{ url: 'https://example.com/a.jpg', kind: 'image', format: 'jpg' }]),
  production_json: JSON.stringify({ poster_designer: { svg: '<svg/>' } })
};
const autoPolicy = { policy: 'auto_gates', enabled: true, platforms: ['telegram'] };

/* ------------------------------------------------------------------ policy */

test('policy normalisation refuses unknown values and unsafe defaults', () => {
  assert.equal(normalizePolicy({}).policy, 'off');
  assert.equal(normalizePolicy({ policy: 'yolo' }).policy, 'off');
  assert.deepEqual(normalizePolicy({}).platforms, ['telegram'], 'telegram is the only default target');
  assert.deepEqual(normalizePolicy({ daily_slots: ['99:99', '21:00'] }).daily_slots, ['21:00']);
  assert.equal(normalizePolicy({ max_new_per_day: 999 }).max_new_per_day, 10);
  const summary = policySummary({ policy: 'assisted' });
  assert.equal(summary.auto_approve_gates, false);
  assert.equal(policySummary({ policy: 'full_auto' }).auto_approve_gates, true);
});

test('gate 3 is never auto-approved without clean media, validator and sources', () => {
  const ok = gatePolicy({ gateNumber: 3, content: cleanContent, policy: autoPolicy });
  assert.equal(ok.allow, true, ok.reason);

  const noMedia = gatePolicy({ gateNumber: 3, content: { ...cleanContent, media_links_json: '[]' }, policy: autoPolicy });
  assert.equal(noMedia.allow, false);
  assert.equal(noMedia.reason, 'NO_MEDIA_ATTACHED');

  const badValidator = gatePolicy({ gateNumber: 3, content: { ...cleanContent, validator_report_json: JSON.stringify({ ok: false, issues: [{ status: 'fail', rule: 'x' }] }) }, policy: autoPolicy });
  assert.equal(badValidator.allow, false);
  assert.equal(badValidator.reason, 'VALIDATOR_FAILED');

  const failFact = gatePolicy({ gateNumber: 3, content: { ...cleanContent, fact_check_json: JSON.stringify({ overall: 'needs_changes', claims: [] }) }, policy: autoPolicy });
  assert.equal(failFact.allow, false);
});

test('a source-less fact check cannot pass gate 3 automatically', () => {
  /* This is the FACTCHECK_FAIL case: no sources means no verified claims. */
  const content = { ...cleanContent, fact_check_json: JSON.stringify({ overall: 'pass', claims: [], risk_flags: [], needs_info: [] }) };
  const verdict = gatePolicy({ gateNumber: 3, content, policy: autoPolicy });
  assert.equal(verdict.allow, false);
  assert.equal(verdict.reason, 'NO_SOURCED_CLAIMS');
});

test('risk flags and non-pass fact checks always escalate to the owner', () => {
  const risky = { ...cleanContent, ideation_json: JSON.stringify({ ideas: [{ format: 'post', risk_flags: ['ادعای حقوقی'] }] }) };
  assert.equal(gatePolicy({ gateNumber: 1, content: risky, policy: autoPolicy }).reason, 'RISK_FLAGS_PRESENT');

  const needsChanges = { ...cleanContent, fact_check_json: JSON.stringify({ overall: 'needs_changes', claims: [{ verdict: 'نیازمند منبع' }] }) };
  const verdict = gatePolicy({ gateNumber: 2, content: needsChanges, policy: autoPolicy });
  assert.equal(verdict.allow, false);
  assert.match(verdict.reason, /^FACT_OVERALL/);

  const unsupported = { ...cleanContent, fact_check_json: JSON.stringify({ overall: 'pass', claims: [{ verdict: 'نیازمند منبع' }] }) };
  assert.equal(gatePolicy({ gateNumber: 2, content: unsupported, policy: autoPolicy }).reason, 'CLAIMS_UNSUPPORTED');
});

test('assisted and off policies never approve anything', () => {
  assert.equal(gatePolicy({ gateNumber: 1, content: cleanContent, policy: { policy: 'assisted' } }).reason, 'POLICY_ASSISTED_ONLY');
  assert.equal(gatePolicy({ gateNumber: 1, content: cleanContent, policy: { policy: 'off' } }).allow, false);
  assert.equal(gatePolicy({ gateNumber: 1, content: cleanContent, policy: { policy: 'auto_gates', enabled: false } }).allow, false);
});

/* ------------------------------------------------------------------- image */

test('the poster prompt copies text verbatim and never invents copy', () => {
  const prompt = buildPosterPrompt({
    brand: { primary_color: '#0F4C5C', watermark_text: 'نسرا اصفهان', logo_url: 'https://example.com/logo.png' },
    format: 'post',
    textBlocks: [{ role: 'headline', text: 'سه نشانه پیامک فیشینگ' }, { role: 'body', text: 'منبع را بررسی کن' }]
  });
  assert.match(prompt, /سه نشانه پیامک فیشینگ/);
  assert.match(prompt, /منبع را بررسی کن/);
  assert.match(prompt, /Do not translate, paraphrase, add, or invent/);
  assert.match(prompt, /4:5/);
  assert.match(prompt, /#0F4C5C/);
  /* an invalid colour must never reach the prompt */
  assert.doesNotMatch(buildPosterPrompt({ brand: { primary_color: 'javascript:alert(1)' } }), /javascript:/);
  /* a logo url is never used as an instruction to draw a logo */
  assert.doesNotMatch(prompt, /example\.com\/logo/);
});

test('the image provider is inert without a key and reports itself honestly', async () => {
  const provider = new ImageProvider({ provider: 'gemini', apiKey: '' });
  assert.equal(provider.configured, false);
  assert.equal(provider.status().configured, false);
  await assert.rejects(() => provider.generate('x'), /NOT_CONFIGURED|تنظیم نشده/);
});

test('the image provider falls back across model ids and remembers the winner', async () => {
  const dir = tempDir();
  const stateFile = path.join(dir, 'image-model.json');
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(url);
    if (url.includes('retired-model')) return new Response(JSON.stringify({ error: { message: 'not found' } }), { status: 404 });
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ inlineData: { mimeType: 'image/png', data: Buffer.from('fake-png').toString('base64') } }] } }] }), { status: 200 });
  };
  const provider = new ImageProvider({ provider: 'gemini', apiKey: 'k', models: ['retired-model', 'good-model'], stateFile, fetchImpl, logger: { warn() {} } });
  const result = await provider.generate('poster');
  assert.equal(result.model, 'good-model');
  assert.equal(result.buffer.toString(), 'fake-png');
  assert.equal(extensionForMime(result.mime), 'png');
  assert.equal(JSON.parse(fs.readFileSync(stateFile, 'utf8')).model, 'good-model');
  /* the remembered model is tried first next time */
  await provider.generate('poster');
  assert.equal(calls[calls.length - 1].includes('good-model'), true);
});

test('an auth failure stops the model loop instead of burning every id', async () => {
  let calls = 0;
  const provider = new ImageProvider({
    provider: 'gemini',
    apiKey: 'bad',
    models: ['a', 'b', 'c'],
    fetchImpl: async () => { calls += 1; return new Response(JSON.stringify({ error: { message: 'invalid key' } }), { status: 403 }); },
    logger: { warn() {} }
  });
  await assert.rejects(() => provider.generate('x'), /Gemini Image 403|تولید تصویر ناموفق/);
  assert.equal(calls, 1);
});

/* ------------------------------------------------------------------- media */

test('the media store only ever serves files it wrote', () => {
  const dir = tempDir();
  const store = new MediaStore({ dir, publicBaseUrl: 'https://example.test', logger: { warn() {} } });
  const saved = store.save(Buffer.from('jpeg-bytes'), { ext: 'jpg', slug: 'poster x/y' });
  assert.match(saved.name, /^poster-x-y-[a-z0-9]+-[a-f0-9]+\.jpg$/);
  assert.equal(saved.url, `https://example.test/media/${saved.name}`);
  assert.ok(store.resolve(saved.name));
  assert.equal(store.resolve('../../etc/passwd'), null);
  assert.equal(store.resolve('evil.html'), null);
  assert.equal(store.resolve('nope.jpg'), null);
  assert.equal(store.list().length, 1);
});

test('the media store handles missing PUBLIC_BASE_URL without inventing a URL', () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '' });
  const saved = store.save(Buffer.from('x'), { ext: 'png' });
  assert.equal(saved.url, '');
  assert.equal(store.configured, false);
});

/* ----------------------------------------------------------------- runtime */

test('autopilot status is safe to expose and defaults to policy off', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const autopilot = new Autopilot({ db, worker, env: {}, config: {}, logger: { log() {}, warn() {}, error() {} } });
  const status = await autopilot.status();
  assert.equal(status.policy.policy, 'off');
  assert.equal(status.policy.enabled, false);
  assert.equal(status.policy.auto_approve_gates, false);
  const tick = await autopilot.tick();
  assert.equal(tick.skipped, 'policy_off');
  db.close();
});

test('the internal autopilot session is not reachable from an HTTP request', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  /* Even with the env flag on, a request that merely carries the header must fail:
   * http-server strips it, and worker.js only honours it together with the env. */
  const request = new Request('http://127.0.0.1/api/dashboard', { method: 'GET', headers: { 'X-Nasra-Autopilot': '1' } });
  const response = await worker.fetch(request, { DB: db, SESSION_SECRET: 'a'.repeat(64), OWNER_PASSWORD: 'x'.repeat(12) }, { waitUntil() {}, passThroughOnException() {} });
  assert.equal(response.status, 401);

  const internal = new Request('http://127.0.0.1/api/dashboard', { method: 'GET', headers: { 'X-Nasra-Autopilot': '1' } });
  const allowed = await worker.fetch(internal, { DB: db, SESSION_SECRET: 'a'.repeat(64), OWNER_PASSWORD: 'x'.repeat(12), NASRA_AUTOPILOT: '1' }, { waitUntil() {}, passThroughOnException() {} });
  assert.equal(allowed.status, 200);
  db.close();
});

test('autopilot creates nothing when no topic exists and never invents a title', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)')
    .bind('autopilot', JSON.stringify({ policy: 'auto_gates', enabled: true, daily_slots: [] }), new Date().toISOString()).run();
  const autopilot = new Autopilot({ db, worker, env: { DB: db, SESSION_SECRET: 'a'.repeat(64) }, config: {}, logger: { log() {}, warn() {}, error() {} } });
  const summary = await autopilot.tick();
  assert.equal(summary.created, 0);
  const count = await db.prepare('SELECT COUNT(*) AS n FROM content_items').first();
  assert.equal(count.n, 0);
  db.close();
});

/* ------------------------------------------------- integration: full loop */

test('autopilot drives a whole item from topic to scheduled publish', async t => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });

  /* Stub model: the same deterministic answers as MOCK_AI, except fact_checker
   * gets one verified claim so the source rule can actually be satisfied. */
  const { default: mockAgentOutput } = await import('../server/mock-ai.mjs');
  const env = {
    DB: db,
    SESSION_SECRET: 'a'.repeat(64),
    OWNER_PASSWORD: 'test-owner-password',
    AI_PROVIDER: 'workers_ai',
    AI: {
      async run(_model, args = {}) {
        const system = args.messages?.[0]?.content || '';
        const user = args.messages?.[1]?.content || '';
        const output = mockAgentOutput(system, user);
        if (/##\s*fact_checker/.test(system)) {
          return { response: JSON.stringify({ ...output, claims: [{ claim: 'منبع رسمی موجود است', verdict: 'تأییدشده', sources: ['راهنمای امنیت سایبری'], confidence: 'زیاد', fix_suggestion: '' }] }) };
        }
        return { response: JSON.stringify(output) };
      }
    }
  };
  const ctx = { waitUntil() {}, passThroughOnException() {} };

  /* One manual topic is the only human input; everything else is automatic. */
  await db.prepare('INSERT INTO topic_candidates (id,title,why_now,pillar,status,created_at) VALUES (?,?,?,?,?,?)')
    .bind('topic_1', 'سه نشانه پیامک فیشینگ', 'آزمون محلی', 'امنیت و حریم خصوصی', 'NEW', new Date().toISOString()).run();

  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)')
    .bind('autopilot', JSON.stringify({ policy: 'full_auto', enabled: true, platforms: ['telegram'], daily_slots: [], max_new_per_day: 2 }), new Date().toISOString()).run();
  /* A configured channel id makes the publish path live-ready; the guard is still dry_run. */
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json')
    .bind('general', JSON.stringify({ timezone: 'Asia/Tehran', tg_channel_id: '-1000000000001', global_daily_token_cap: 5000000, global_daily_request_cap: 5000, carousel_strict_no_mixed: true }), new Date().toISOString()).run();

  const imageProvider = {
    configured: true,
    status: () => ({ configured: true }),
    async generate() { return { buffer: Buffer.from('stub-jpeg'), mime: 'image/jpeg', model: 'stub-image' }; }
  };
  /* HTTPS is required: worker.js rejects media links that platforms cannot fetch. */
  const mediaStore = new MediaStore({ dir: path.join(dir, 'media'), publicBaseUrl: 'https://example.test' });
  const autopilot = new Autopilot({ db, worker, env, config: {}, mediaStore, imageProvider, logger: { log() {}, warn() {}, error() {} } });
  const { Scheduler } = await import('../server/scheduler.mjs');
  const scheduler = new Scheduler({ worker, env, db, config: { schedulerEnabled: false, schedulerIntervalMs: 1000, backup: { dir, keep: 1 } }, logger: { log() {}, warn() {}, error() {} } });

  const seen = new Set();
  for (let round = 0; round < 30; round++) {
    await autopilot.tick();
    await scheduler.tick();
    const row = await db.prepare('SELECT state FROM content_items ORDER BY created_at LIMIT 1').first();
    if (row?.state) seen.add(row.state);
  }

  const content = await db.prepare('SELECT * FROM content_items ORDER BY created_at LIMIT 1').first();
  assert.ok(content, 'autopilot must have created one content item from the topic');
  assert.equal(content.title, 'سه نشانه پیامک فیشینگ');
  assert.equal(content.current_version, 1);
  assert.ok(['SCHEDULED', 'PUBLISHING', 'PUBLISHED'].includes(content.state), `pipeline stalled at ${content.state}; seen=${[...seen].join(',')}`);

  /* The poster must have been generated and attached by the runtime, not by hand. */
  const media = JSON.parse(content.media_links_json || '[]');
  assert.equal(media.length, 1);
  assert.equal(media[0].source, 'autopilot:image-model');
  assert.match(media[0].url, /^https:\/\/example\.test\/media\/poster-/);
  /* The stub returns JPEG; the file on disk must be a real one. */
  assert.equal(media[0].format, 'jpg');
  assert.ok(fs.existsSync(path.join(dir, 'media', path.basename(media[0].url))));

  const gates = await db.prepare('SELECT gate_number,status,decided_by FROM gate_decisions ORDER BY gate_number').all();
  assert.deepEqual(gates.results.map(g => g.status), ['APPROVED', 'APPROVED', 'APPROVED']);

  const jobs = await db.prepare('SELECT * FROM publish_jobs').all();
  assert.equal(jobs.results.length, 1, 'one publish job per target platform');
  assert.equal(jobs.results[0].platform, 'telegram');
  assert.notEqual(jobs.results[0].status, 'PUBLISHED', 'nothing may leave the process without the guard approval');

  /* Every automatic decision is attributable in the audit log. */
  const actions = await db.prepare("SELECT action FROM audit_logs WHERE actor='system:autopilot'").all();
  const names = new Set(actions.results.map(r => r.action));
  assert.ok(names.has('autopilot_content_created'));
  assert.ok(names.has('autopilot_gate_approved'));
  assert.ok(names.has('autopilot_media_attached'));

  /* One slot must never create twice, even if the loop runs again in the same window. */
  const before = await db.prepare('SELECT COUNT(*) AS n FROM content_items').first();
  for (let i = 0; i < 3; i++) { await autopilot.tick(); await scheduler.tick(); }
  const after = await db.prepare('SELECT COUNT(*) AS n FROM content_items').first();
  assert.equal(after.n, before.n, 'repeated ticks must not create more content');
  db.close();
});

test('posters are skipped entirely when the media base URL is not HTTPS', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  let generated = 0;
  const mediaStore = new MediaStore({ dir: path.join(dir, 'media'), publicBaseUrl: 'http://insecure.test' });
  assert.equal(mediaStore.configured, true);
  assert.equal(mediaStore.fetchable, false);
  const autopilot = new Autopilot({
    db, worker, env: { DB: db, SESSION_SECRET: 'a'.repeat(64) }, config: {},
    mediaStore,
    imageProvider: { configured: true, status: () => ({ configured: true }), async generate() { generated += 1; return { buffer: Buffer.from('x'), mime: 'image/png', model: 'stub' }; } },
    logger: { log() {}, warn() {}, error() {} }
  });
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)')
    .bind('autopilot', JSON.stringify({ policy: 'assisted', enabled: true }), new Date().toISOString()).run();
  await db.prepare(`INSERT INTO content_items (id,title,pillar,state,state_version,current_version,selected_format,production_json,media_links_json,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?)`).bind('c_insecure', 't', 'p', 'MEDIA_PENDING', 1, 1, 'post',
    JSON.stringify({ poster_designer: { text_blocks: [{ role: 'headline', text: 'سلام' }] } }), '[]', new Date().toISOString(), new Date().toISOString()).run();
  const summary = await autopilot.tick();
  assert.equal(summary.posters, 0);
  assert.equal(generated, 0, 'no money may be spent on an image that cannot be delivered');
  db.close();
});

/* -------------------------------------------------------- jpeg conversion */

test('PNG output from the image model is converted to JPEG before publishing', async () => {
  const { pngToJpeg, isJpeg, decodePng, encodeJpeg } = await import('../server/png-to-jpeg.mjs');
  const zlib = await import('node:zlib');

  /* Build a 32x32 RGB PNG by hand: closed red square on white. */
  const width = 32, height = 32;
  const rows = [];
  for (let y = 0; y < height; y += 1) {
    const row = [0];
    for (let x = 0; x < width; x += 1) {
      const red = x >= 8 && x < 24 && y >= 8 && y < 24;
      row.push(...(red ? [220, 30, 40] : [255, 255, 255]));
    }
    rows.push(Buffer.from(row));
  }
  const crc = buf => {
    let c = ~0;
    for (const byte of buf) {
      c ^= byte;
      for (let k = 0; k < 8; k += 1) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
    }
    return (~c) >>> 0;
  };
  const chunk = (type, data) => {
    const head = Buffer.alloc(8);
    head.writeUInt32BE(data.length, 0);
    head.write(type, 4, 'ascii');
    const crcBuf = Buffer.alloc(4);
    crcBuf.writeUInt32BE(crc(Buffer.concat([Buffer.from(type, 'ascii'), data])), 0);
    return Buffer.concat([head, data, crcBuf]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(Buffer.concat(rows), { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);

  /* The decoder must reproduce the pixels exactly. */
  const decoded = decodePng(png);
  assert.equal(decoded.width, 32);
  assert.equal(decoded.height, 32);
  const pixel = (x, y) => [...decoded.data.subarray((y * 32 + x) * 4, (y * 32 + x) * 4 + 3)];
  assert.deepEqual(pixel(16, 16), [220, 30, 40]);
  assert.deepEqual(pixel(0, 0), [255, 255, 255]);

  const jpeg = pngToJpeg(png, { quality: 95 });
  assert.equal(isJpeg(jpeg), true);
  /* SOI ... EOI, with no stray bytes in between. */
  assert.equal(jpeg.readUInt16BE(jpeg.length - 2), 0xffd9);
  assert.ok(jpeg.length > 200);

  /* An unsupported PNG must fail loudly rather than produce garbage. */
  const broken = Buffer.from(png);
  broken[28] = 1; // interlace flag
  assert.throws(() => decodePng(broken), /interlaced|PNG/);
  assert.equal(isJpeg(Buffer.from('not a jpeg')), false);
  assert.ok(encodeJpeg({ width: 1, height: 1, data: Buffer.from([1, 2, 3, 255]) }).length > 100);
});

/* ------------------------------------------------------ forced tick (/now) */

test('/now creates content outside the calendar slots, and says why when it cannot', async () => {
  const dir = tempDir();
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: SCHEMA });
  const now = new Date().toISOString();
  /* Slots that are definitely not due right now: an unforced tick must stay idle. */
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)')
    .bind('autopilot', JSON.stringify({ policy: 'assisted', enabled: true, daily_slots: ['03:17'], platforms: ['telegram'] }), now).run();
  const autopilot = new Autopilot({ db, worker, env: { DB: db, SESSION_SECRET: 'a'.repeat(64) }, config: {}, logger: { log() {}, warn() {}, error() {} } });

  /* 1. Nothing to write about: a forced tick must explain that, not stay silent. */
  const empty = await autopilot.tick({ force: true });
  assert.equal(empty.created, 0);
  assert.match(String(empty.reason), /سوژه/, 'the owner is told the topic queue is empty');

  /* 2. A topic exists: the forced tick takes it into the pipeline even though no
        calendar slot is due. Without this /now at 15:00 does nothing. */
  await db.prepare('INSERT INTO topic_candidates (id,title,why_now,pillar,source_refs_json,sensitivity,urgency,risk_flags_json,needs_info_json,status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)')
    .bind('topic_now', 'چک‌لیست بررسی پیام مشکوک', 'برای آزمون', 'سواد خبری', '[]', 'کم', 'این‌هفته', '[]', '[]', 'NEW', now).run();
  const forced = await autopilot.tick({ force: true });
  assert.equal(forced.created, 1, 'a forced cycle must produce the item the owner asked for');
  const rows = await db.prepare('SELECT COUNT(*) AS n FROM content_items').first();
  assert.equal(rows.n, 1);
  db.close();
});
