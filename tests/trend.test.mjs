/*
 * Trend pipeline: ideas taken from what is already viral.
 *
 * The rule this file defends: a trend claim needs a number the owner pasted or
 * the publisher stated. No number, no "viral". Everything else is a hypothesis.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { openSqliteD1 } from '../server/d1-sqlite.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(import.meta.dirname, '..');
const SCHEMA = path.join(ROOT, 'schema.sql');

function freshDb() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-trend-'));
  return openSqliteD1({ file: path.join(dir, 'n.sqlite'), schemaFile: SCHEMA });
}

/* Build a worker env around a db, the way the tests for the rest of the app do. */
async function makeEnv(db) {
  const { readConfig } = await import('../server/env.mjs');
  const config = readConfig({ OWNER_PASSWORD: 'x'.repeat(20), SESSION_SECRET: 'y'.repeat(40), MOCK_AI: '1' }, { rootDir: ROOT });
  return { DB: db, ...config, env: { DB: db } };
}

test('the schema seeds trend sources switched off', async () => {
  const db = freshDb();
  const rows = await db.prepare("SELECT id,enabled,is_template,identifier FROM sources WHERE source_type='trend'").all();
  const list = rows.results || rows;
  assert.equal(list.length, 2, 'a Google Trends template and a manual one');
  for (const row of list) {
    assert.equal(row.enabled, 0, `${row.id} must not fetch anything until the owner turns it on`);
    assert.equal(row.is_template, 1);
  }
  const g = list.find(r => r.id === 'src_template_gtrends_ir');
  assert.match(g.identifier, /trends\.google\.com\/trending\/rss\?geo=IR/);
  db.close();
});

test('the trend agent exists and forbids invented numbers', async () => {
  const db = freshDb();
  const agent = await db.prepare("SELECT * FROM agents WHERE id='trend_hunter'").first();
  assert.ok(agent, 'trend_hunter is seeded');
  assert.equal(agent.enabled, 1);
  assert.match(agent.system_prompt, /هیچ عددی نساز/);
  assert.match(agent.system_prompt, /وایرال/);
  const schema = JSON.parse(agent.output_schema_json);
  assert.deepEqual(schema.required, ['opportunities', 'risk_flags', 'needs_info']);
  const opp = schema.properties.opportunities.items.required;
  for (const field of ['engagement_evidence', 'confidence', 'reuse_verdict', 'do_not_copy', 'format']) {
    assert.ok(opp.includes(field), `${field} is required of every opportunity`);
  }
  db.close();
});

test('an old database gains the engagement column without losing rows', async () => {
  const { DatabaseSync } = await import('node:sqlite');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-mig-'));
  const file = path.join(dir, 'old.sqlite');
  const raw = new DatabaseSync(file);
  raw.exec(`CREATE TABLE source_items (id TEXT PRIMARY KEY, source_id TEXT NOT NULL, external_ref TEXT,
    title TEXT NOT NULL, raw_text TEXT NOT NULL, published_at TEXT, pillar_hint TEXT,
    used_count INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)`);
  raw.prepare('INSERT INTO source_items (id,source_id,title,raw_text,created_at) VALUES (?,?,?,?,?)')
    .run('old', 's', 'قبل از آپدیت', 'متن', '2026-01-01');
  raw.close();

  const db = openSqliteD1({ file, schemaFile: SCHEMA });
  const row = await db.prepare("SELECT id,title,engagement_json FROM source_items WHERE id='old'").first();
  assert.equal(row.title, 'قبل از آپدیت', 'the existing row survives the migration');
  assert.equal(row.engagement_json, '{}', 'unknown, not zero');
  db.close();
});

test('engagement survives the API parser and rejects junk', async () => {
  const source = fs.readFileSync(path.join(ROOT, 'worker.js'), 'utf8');
  /* engagementOf is module-private; check its contract by evaluating the block
     that defines it, so the test breaks if the validation is loosened. */
  const start = source.indexOf('const ENGAGEMENT_KEYS');
  const end = source.indexOf('function safeFeedUrl');
  assert.ok(start > 0 && end > start, 'engagementOf is present in worker.js');
  const block = source.slice(start, end);
  const fn = new Function(`const str = (v, n) => String(v ?? '').slice(0, n); ${block}; return engagementOf;`)();

  assert.deepEqual(fn({ engagement: { views: 120000, likes: '1.2M' } }), { views: 120000, likes: '1.2M' });
  assert.deepEqual(fn({ views: '85000' }), { views: '85000' }, 'flat body still works');
  assert.deepEqual(fn({ engagement: { views: '' } }), {}, 'empty means unknown');
  assert.deepEqual(fn({ engagement: { views: 'خیلی زیاد' } }), {}, 'a non-number is dropped, never zeroed');
  assert.deepEqual(fn({ engagement: { views: -5 } }), {}, 'negative is not a measurement');
  assert.deepEqual(fn({ engagement: { views: 10, secret_payload: '<script>' } }), { views: 10 }, 'only known keys survive');
});

test('the daily slot hunts trends only when a trend source is on', async () => {
  const source = fs.readFileSync(path.join(ROOT, 'worker.js'), 'utf8');
  const block = source.slice(source.indexOf("if (p.hour === 7 && await cronOnce(db, 'daily_trend', day))"), source.indexOf('if (p.hour === 8'));
  assert.match(block, /agentId: 'trend_hunter'/, 'the daily slot asks the trend question');
  assert.match(block, /source_type='trend'/, 'and only when a trend source is enabled');
  assert.match(block, /if \(asInt\(trendSources\?\.n, 0\) > 0\)/, 'guarded by the count, not called blindly');
});

/* ------------------------------------------------- Google Trends, end to end */


/* Authenticate against the worker the way the panel does, and keep the cookie
   plus the CSRF token the mutating routes require. */
async function signedIn(db, env) {
  const login = await worker.fetch(new Request('http://localhost/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: env.OWNER_PASSWORD })
  }), env, {});
  const body = await login.json();
  if (login.status !== 200) throw new Error(`login failed: ${login.status} ${JSON.stringify(body)}`);
  const cookie = login.headers.get('set-cookie').split(';')[0];
  return { cookie, csrf: body.csrf_token };
}

const GOOGLE_FEED = `<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:ht="https://trends.google.com/trending/rss" version="2.0">
  <channel>
    <title>Daily Search Trends</title>
    <item>
      <title>سواد رسانه‌ای</title>
      <ht:approx_traffic>20000+</ht:approx_traffic>
      <pubDate>Thu, 06 Nov 2026 06:00:00 +0000</pubDate>
      <description>Searches spiked after a news report.</description>
    </item>
    <item>
      <title>کلاس عکاسی اصفهان</title>
      <ht:approx_traffic>5000+</ht:approx_traffic>
      <pubDate>Thu, 06 Nov 2026 05:00:00 +0000</pubDate>
      <description>Steady rise this week.</description>
    </item>
    <item>
      <title>بدون حجم جستجو</title>
      <pubDate>Thu, 06 Nov 2026 04:00:00 +0000</pubDate>
      <description>No volume element at all.</description>
    </item>
  </channel>
</rss>`;

test('a Google Trends feed becomes items with the publisher\'s own search volume', async () => {
  const db = freshDb();
  const env = { DB: db, OWNER_PASSWORD: 'trend-test-password', SESSION_SECRET: 'z'.repeat(40), TG_BOT_TOKEN: '', TG_OWNER_CHAT_ID: '' };
  /* The feed is served locally: the parser, not the internet, is what is tested. */
  const realFetch = globalThis.fetch;
  const requested = [];
  globalThis.fetch = async (target, init) => {
    const url = String(target);
    requested.push(url);
    if (url.includes('trends.google.com')) {
      return new Response(GOOGLE_FEED, { status: 200, headers: { 'Content-Type': 'application/rss+xml' } });
    }
    return realFetch(target, init);
  };
  try {
    await db.prepare("UPDATE sources SET enabled=1 WHERE id='src_template_gtrends_ir'").run();
    const ctx = {};
    const { cookie, csrf } = await signedIn(db, env);
    const fetchRoute = () => worker.fetch(new Request('http://localhost/api/sources/fetch', {
      method: 'POST', headers: { 'Content-Type': 'application/json', cookie, 'X-CSRF-Token': csrf }, body: '{}'
    }), env, ctx);
    const response = await fetchRoute();
    assert.equal(response.status, 200);
    const payload = await response.json();
    const summary = payload.data.find(x => x.source_id === 'src_template_gtrends_ir');
    assert.equal(summary.ok, true, JSON.stringify(payload.data));
    assert.equal(summary.imported, 3);

    const rows = await db.prepare("SELECT title,engagement_json,pillar_hint FROM source_items WHERE source_id='src_template_gtrends_ir' ORDER BY title").all();
    const items = rows.results || rows;
    const byTitle = Object.fromEntries(items.map(r => [r.title, r]));
    assert.equal(JSON.parse(byTitle['سواد رسانه‌ای'].engagement_json).approx_traffic, '20000+', 'the publisher number is kept verbatim');
    assert.equal(JSON.parse(byTitle['کلاس عکاسی اصفهان'].engagement_json).approx_traffic, '5000+');
    assert.deepEqual(JSON.parse(byTitle['بدون حجم جستجو'].engagement_json), {}, 'no volume stays unknown, never zero');
    assert.equal(byTitle['سواد رسانه‌ای'].pillar_hint, 'ترند روز');

    /* fetching twice must not duplicate */
    await fetchRoute();
    const again = await db.prepare("SELECT COUNT(*) AS n FROM source_items WHERE source_id='src_template_gtrends_ir'").first();
    assert.equal(again.n, 3, 'the same feed does not insert duplicates');
  } finally {
    globalThis.fetch = realFetch;
    db.close();
  }
});

test('a private or localhost feed address is refused before any request', async () => {
  const db = freshDb();
  const env = { DB: db, OWNER_PASSWORD: 'trend-test-password', SESSION_SECRET: 'z'.repeat(40) };
  const realFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => { called = true; return new Response('nope'); };
  try {
    await db.prepare("UPDATE sources SET enabled=1, identifier='http://127.0.0.1:8787/feed' WHERE id='src_template_gtrends_ir'").run();
    const { cookie, csrf } = await signedIn(db, env);
    const response = await worker.fetch(new Request('http://localhost/api/sources/fetch', {
      method: 'POST', headers: { 'Content-Type': 'application/json', cookie, 'X-CSRF-Token': csrf }, body: '{}'
    }), env, {});
    const payload = await response.json();
    assert.equal(payload.data[0].ok, false);
    assert.equal(payload.data[0].error, 'invalid_or_private_feed_url');
    assert.equal(called, false, 'SSRF guard runs before the fetch');
  } finally {
    globalThis.fetch = realFetch;
    db.close();
  }
});
