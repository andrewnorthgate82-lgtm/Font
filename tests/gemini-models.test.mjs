/*
 * Gemini model selection.
 *
 * The failure this file exists for: a hardcoded model name ("gemini-2.0-flash")
 * answered HTTP 404 "no longer available" and the whole pipeline stopped, with a
 * raw English error in the panel. The fix must (a) discover the live model list,
 * (b) retry once with a live model, (c) never silently pick an image or
 * embedding model for text work.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { openSqliteD1 } from '../server/d1-sqlite.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(import.meta.dirname, '..');

/* Google's list shape, trimmed to what matters for the ranking. */
const MODEL_LIST = {
  models: [
    { name: 'models/gemini-2.0-flash', supportedGenerationMethods: ['generateContent'] },
    { name: 'models/gemini-3.8-flash', supportedGenerationMethods: ['generateContent'] },
    { name: 'models/gemini-3.8-pro', supportedGenerationMethods: ['generateContent'] },
    { name: 'models/gemini-3.8-flash-preview', supportedGenerationMethods: ['generateContent'] },
    { name: 'models/gemini-embedding-001', supportedGenerationMethods: ['embedContent'] },
    { name: 'models/imagen-4.0-generate', supportedGenerationMethods: ['predict'] },
    { name: 'models/gemini-2.5-flash-image', supportedGenerationMethods: ['generateContent'] },
    { name: 'models/gemma-3-27b-it', supportedGenerationMethods: ['generateContent'] }
  ]
};

function freshDb() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-gem-'));
  return openSqliteD1({ file: path.join(dir, 'n.sqlite'), schemaFile: path.join(ROOT, 'schema.sql') });
}

test('the posted model is discovered from the live list, not hardcoded', async () => {
  const db = freshDb();
  const env = { DB: db, AI_PROVIDER: 'gemini', AI_API_KEY: 'k-test', OWNER_PASSWORD: 'x'.repeat(12), SESSION_SECRET: 'y'.repeat(32) };
  const calls = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (target, init = {}) => {
    const url = String(target);
    if (url.endsWith('/v1beta/models')) return new Response(JSON.stringify(MODEL_LIST), { status: 200, headers: { 'Content-Type': 'application/json' } });
    if (url.includes(':generateContent')) {
      calls.push(url);
      return new Response(JSON.stringify({
        candidates: [{ content: { parts: [{ text: JSON.stringify({ opportunities: [], risk_flags: [], needs_info: [] }) }] } }],
        usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 5 }
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return realFetch(target, init);
  };
  try {
    /* runAgent is private; drive it the way the queue does - a job row and a tick. */
    await db.prepare(`INSERT INTO jobs (id,job_type,agent_id,payload_json,status,priority,attempts,max_attempts,run_after,created_at,updated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?)`).bind('j1', 'agent', 'trend_hunter', JSON.stringify({ agentId: 'trend_hunter', contentId: null, mode: 'manual' }), 'QUEUED', 5, 0, 4, new Date().toISOString(), new Date().toISOString(), new Date().toISOString()).run();
    const { Scheduler } = await import('../server/scheduler.mjs');
    const scheduler = new Scheduler({ worker, env, db, config: { schedulerEnabled: false }, logger: { log() {}, error() {} } });
    await scheduler.tick();
    const job = await db.prepare("SELECT status,last_error FROM jobs WHERE id='j1'").first();
    assert.equal(job.status, 'DONE', `job failed: ${job.last_error}`);
    assert.equal(calls.length, 1);
    const chosen = decodeURIComponent(calls[0].match(/models\/(.+):generateContent/)[1]);
    assert.equal(chosen, 'gemini-3.8-flash', 'flash, newest, stable — not 2.0, not preview, not pro, never an image or embedding model');
  } finally {
    globalThis.fetch = realFetch;
    db.close();
  }
});

test('a retired model is retried against a live one instead of failing', async () => {
  const db = freshDb();
  /* The owner's real situation: .env pins an old model. */
  const env = { DB: db, AI_PROVIDER: 'gemini', AI_API_KEY: 'k-test', AI_MODEL: 'gemini-2.0-flash', OWNER_PASSWORD: 'x'.repeat(12), SESSION_SECRET: 'y'.repeat(32) };
  const tried = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (target, init = {}) => {
    const url = String(target);
    if (url.endsWith('/v1beta/models')) return new Response(JSON.stringify(MODEL_LIST), { status: 200, headers: { 'Content-Type': 'application/json' } });
    if (url.includes(':generateContent')) {
      const name = decodeURIComponent(url.match(/models\/(.+):generateContent/)[1]);
      tried.push(name);
      if (name === 'gemini-2.0-flash') {
        return new Response(JSON.stringify({ error: { code: 404, message: 'This model models/gemini-2.0-flash is no longer available. Please update your code to use models/gemini-3.8-flash' } }), { status: 404, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({
        candidates: [{ content: { parts: [{ text: JSON.stringify({ opportunities: [], risk_flags: [], needs_info: [] }) }] } }],
        usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 5 }
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return realFetch(target, init);
  };
  try {
    const now = new Date().toISOString();
    await db.prepare(`INSERT INTO jobs (id,job_type,agent_id,payload_json,status,priority,attempts,max_attempts,run_after,created_at,updated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?)`).bind('j2', 'agent', 'trend_hunter', JSON.stringify({ agentId: 'trend_hunter', contentId: null, mode: 'manual' }), 'QUEUED', 5, 0, 4, now, now, now).run();
    const { Scheduler } = await import('../server/scheduler.mjs');
    const scheduler = new Scheduler({ worker, env, db, config: { schedulerEnabled: false }, logger: { log() {}, error() {} } });
    await scheduler.tick();
    const job = await db.prepare("SELECT status,last_error,attempts FROM jobs WHERE id='j2'").first();
    assert.equal(job.status, 'DONE', `job failed: ${job.last_error}`);
    assert.deepEqual(tried, ['gemini-2.0-flash', 'gemini-3.8-flash'], 'one honest retry with a model that exists');
  } finally {
    globalThis.fetch = realFetch;
    db.close();
  }
});

test('a seeded old model name is reset, so no install keeps a dead version', async () => {
  const db = freshDb();
  await db.prepare("UPDATE agents SET model='gemini-2.0-flash' WHERE id='ideation'").run();
  db.close();
  /* Re-applying the schema (every boot) performs the forward migration. */
  const dir2 = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-gem2-'));
  const file = path.join(dir2, 'n.sqlite');
  const first = openSqliteD1({ file, schemaFile: path.join(ROOT, 'schema.sql') });
  await first.prepare("UPDATE agents SET model='gemini-2.0-flash' WHERE id='ideation'").run();
  first.close();
  const second = openSqliteD1({ file, schemaFile: path.join(ROOT, 'schema.sql') });
  const row = await second.prepare("SELECT model FROM agents WHERE id='ideation'").first();
  assert.notEqual(row.model, 'gemini-2.0-flash', 'a retired name never survives a reboot');
  second.close();
});
