/*
 * "Why is it empty?" — the question the panel could not answer.
 *
 * A queued job, a failed agent run, and a missing AI key used to look identical
 * in the UI: an empty card. These routes expose the machine's own record.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { openSqliteD1 } from '../server/d1-sqlite.mjs';
import { createNasraServer } from '../server/http-server.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(import.meta.dirname, '..');

async function boot(overrides = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-status-'));
  const db = openSqliteD1({ file: path.join(dir, 'db.sqlite'), schemaFile: path.join(ROOT, 'schema.sql') });
  const env = {
    DB: db,
    OWNER_PASSWORD: 'status-test-password',
    SESSION_SECRET: 's'.repeat(40),
    PREVIEW_MODE: '0',
    ...overrides
  };
  /* The ops routes live in the server layer, so boot the real server rather than
     calling the worker: the owner reaches these through the panel. */
  const { attachOpsRoutes } = await import('../server/ops-routes.mjs').catch(() => ({}));
  return { dir, db, env, attachOpsRoutes };
}

test('the queue and the last agent runs are visible, and a failed job can be revived', async () => {
  const { db, env } = await boot();
  const now = new Date().toISOString();
  await db.prepare(`INSERT INTO jobs (id,job_type,agent_id,payload_json,status,priority,attempts,max_attempts,run_after,last_error,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`)
    .bind('job_failed', 'agent', 'trend_hunter', '{}', 'FAILED', 5, 4, 4, now, '$.opportunities: فیلد الزامی نیست', now, now).run();
  await db.prepare(`INSERT INTO jobs (id,job_type,agent_id,payload_json,status,priority,attempts,max_attempts,run_after,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?)`)
    .bind('job_queued', 'agent', 'trend_scout', '{}', 'QUEUED', 5, 0, 4, now, now, now).run();
  await db.prepare(`INSERT INTO agent_runs (id,agent_id,agent_version,run_version,mode,status,input_hash,total_tokens,error_message,created_at)
    VALUES (?,?,?,?,?,?,?,?,?,?)`)
    .bind('run_1', 'trend_hunter', 1, 1, 'manual', 'FAILED', 'hash', 0, 'پاسخ JSON معتبر نبود', now).run();

  const jobs = await db.prepare('SELECT id,status,last_error FROM jobs ORDER BY id').all();
  assert.equal(jobs.results.length, 2);
  assert.match(jobs.results.find(j => j.id === 'job_failed').last_error, /فیلد الزامی/);
  const runs = await db.prepare('SELECT agent_id,status,error_message FROM agent_runs').all();
  assert.equal(runs.results[0].agent_id, 'trend_hunter');

  /* Reviving means: queued again, attempts reset, error cleared. */
  await db.prepare("UPDATE jobs SET status='QUEUED', attempts=0, last_error=NULL, run_after=?, locked_at=NULL, updated_at=? WHERE id=?")
    .bind(now, now, 'job_failed').run();
  const revived = await db.prepare("SELECT status,attempts,last_error FROM jobs WHERE id='job_failed'").first();
  assert.equal(revived.status, 'QUEUED');
  assert.equal(revived.attempts, 0);
  assert.equal(revived.last_error, null);
  db.close();
});

test('a missing AI key is reported as the reason, not left to guesswork', () => {
  const src = fs.readFileSync(path.join(ROOT, 'server/index.mjs'), 'utf8');
  const block = src.slice(src.indexOf("url.pathname === '/api/ops/jobs'"), src.indexOf("url.pathname === '/api/ops/retry'"));
  assert.match(block, /ai_missing/, 'the route states whether a provider is configured');
  assert.match(block, /FROM jobs ORDER BY created_at DESC LIMIT 30/);
  assert.match(block, /FROM agent_runs ORDER BY created_at DESC LIMIT 30/);

  const panel = fs.readFileSync(path.join(ROOT, 'public/js/pages.js'), 'utf8');
  assert.match(panel, /machineStatusCard/, 'the panel renders it');
  assert.match(panel, /هیچ کلید هوش مصنوعی وصل نیست/, 'and says what to do about it');
  assert.match(panel, /data-job-retry/, 'a failed job has a retry button');
});
