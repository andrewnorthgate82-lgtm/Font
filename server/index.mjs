#!/usr/bin/env node
/*
 * Nasra Esfahan Media Studio — self-hosted entry point (SillyDev / Node 22).
 *
 *   node server/index.mjs
 *
 * Everything is configured through environment variables (SERVER_PORT, DATA_DIR,
 * OWNER_PASSWORD, SESSION_SECRET, TG_BOT_TOKEN, ...). See .env.example and
 * SILLYDEV-DEPLOY.fa.md.
 *
 * Safety properties of this runtime:
 *   - PUBLISH_GUARD_MODE defaults to dry_run: worker.js is used unchanged, but
 *     every outgoing publish call is intercepted before it reaches the network.
 *   - A live publish additionally needs a separate, single-use owner
 *     confirmation issued through POST /api/ops/approvals.
 *   - Telegram works through long polling; no public webhook is required.
 *   - SQLite runs in WAL mode with an automatic backup every N hours.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { readConfig, validateConfig, configSummary, workerEnv, loadEnvFiles } from './env.mjs';
import { openSqliteD1 } from './d1-sqlite.mjs';
import { PublishGuard, classifyPublishRequest, dryRunResponse, guardedFetch } from './publish-guard.mjs';
import { Scheduler } from './scheduler.mjs';
import { TelegramPoller } from './telegram-poller.mjs';
import { createNasraServer } from './http-server.mjs';
import { MediaStore } from './media-store.mjs';
import { ImageProvider } from './image-provider.mjs';
import { Autopilot, normalizePolicy, policySummary } from './autopilot.mjs';
import { readHeartbeat, writeHeartbeat, consumeTickRequest } from './telegram-control.mjs';
import { MAX_UPLOAD_BYTES, storeUpload, readRawBody, slugFrom } from './media-upload.mjs';
import worker from '../worker.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const startedAt = new Date();
const APP_VERSION = '1.1.0-sillydev';

if (process.env.NODE_ENV === 'production' && !process.env.NODE_NO_WARNINGS) process.env.NODE_NO_WARNINGS = '1';
if (process.env.NASRA_ENV_FILE !== '0') loadEnvFiles(ROOT, process.env.NASRA_ENV_FILE || '.env');

const config = readConfig(process.env, { rootDir: ROOT });
const validation = validateConfig(config, process.env);

if (process.env.NASRA_QUIET !== '1') {
  console.log('نسرا اصفهان — Nasra Esfahan Media Studio (self-hosted / Node 22)');
  console.log(`node ${process.version} · data ${path.relative(ROOT, config.dataDir) || '.'} · port ${config.port}`);
  console.log(JSON.stringify(configSummary(config), null, 2));
}

if (!validation.ok) {
  console.error('[nasra] configuration is incomplete:');
  for (const problem of validation.problems) console.error(`  ✗ ${problem}`);
  console.error('\n[nasra] nothing was started. See .env.example for the required values.');
  process.exit(1);
}
for (const warning of validation.warnings) console.warn(`[nasra] warning: ${warning}`);

fs.mkdirSync(config.dataDir, { recursive: true });
fs.mkdirSync(config.backup.dir, { recursive: true });

let db;
try {
  db = openSqliteD1({
    file: config.dbFile,
    schemaFile: config.schemaFile,
    busyTimeoutMs: config.dbBusyTimeoutMs,
    wal: config.sqliteWal
  });
} catch (error) {
  console.error(`[nasra] could not open the database: ${error.message}`);
  process.exit(1);
}
console.log(`[nasra] sqlite ready: ${path.relative(ROOT, config.dbFile)} (WAL, foreign_keys, busy_timeout=${config.dbBusyTimeoutMs}ms)`);

const guard = new PublishGuard({
  mode: config.guard.mode,
  allowlist: config.guard.allowlist,
  ownerChatIds: [process.env.TG_OWNER_CHAT_ID || ''],
  approvalTtlMs: config.guard.approvalTtlMs
});

/* Freshness of this process, readable from disk without asking the server.
   The Telegram control module uses it to answer /ping and /wake honestly. */
function refreshHeartbeat() {
  return writeHeartbeat(config.dataDir, { port: config.port, url: `http://localhost:${config.port}`, version: APP_VERSION });
}
refreshHeartbeat();
const heartbeatTimer = setInterval(refreshHeartbeat, 30 * 1000);
if (typeof heartbeatTimer.unref === 'function') heartbeatTimer.unref();

const env = {
  ...workerEnv(config, process.env),
  DB: db,
  NASRA_RUNTIME: 'sillydev',
  APP_VERSION,
  PREVIEW_MODE: '0'
};
/*
 * A getter, not a snapshot: "/ping" must report the age of the heartbeat at the
 * moment the owner asks, not at the moment the process started. worker.js has no
 * filesystem access on Cloudflare, so the reading is injected here.
 */
Object.defineProperty(env, 'NASRA_HEARTBEAT', {
  enumerable: true,
  get: () => readHeartbeat(config.dataDir)
});

/*
 * MOCK_AI=1 answers every agent call locally with deterministic JSON. It is off
 * by default, refuses to run with NODE_ENV=production, and never reaches the
 * network. Use it only to rehearse the pipeline on a laptop.
 */
if (config.mockAi) {
  if (config.nodeEnv === 'production') {
    console.error('[nasra] MOCK_AI=1 is refused with NODE_ENV=production; unset MOCK_AI or use NODE_ENV=development.');
    process.exit(1);
  }
  const { default: mockAgentOutput } = await import('./mock-ai.mjs');
  env.AI = {
    async run(_model, args = {}) {
      const system = args.messages?.[0]?.content || '';
      const user = args.messages?.[1]?.content || '';
      return { response: JSON.stringify(mockAgentOutput(system, user)), usage: { prompt_tokens: 100, completion_tokens: 120 } };
    }
  };
  env.AI_PROVIDER = 'workers_ai';
  console.warn('[nasra] MOCK_AI=1 — agent output is a local stub, nothing is generated by a real model.');
}

/*
 * The guard is installed by wrapping global fetch for this process only.
 * worker.js keeps calling fetch exactly as it does on Cloudflare; the wrapper
 * decides whether a publish request may leave the machine.
 */
const realFetch = globalThis.fetch.bind(globalThis);
let guardInstalled = false;

function installGuard() {
  if (guardInstalled) return;
  guardInstalled = true;
  globalThis.fetch = guardedFetch({ guard, realFetch, mediaStore, logger: console });
}

function uninstallGuard() {
  if (!guardInstalled) return;
  guardInstalled = false;
  globalThis.fetch = realFetch;
}

/* ---------------------------------------------------------------- ops API */

function publicOrigin(req) {
  const host = String(req.headers['x-forwarded-host'] || req.headers.host || `127.0.0.1:${config.port}`).split(',')[0].trim();
  const proto = config.trustedProxyHttps && String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim() === 'https' ? 'https' : 'http';
  return `${proto}://${host}`;
}

function sessionHeaders(req) {
  const headers = new Headers();
  for (const name of ['cookie', 'authorization', 'x-nasra-session', 'x-forwarded-for', 'cf-connecting-ip']) {
    const value = req.headers[name];
    if (value !== undefined) headers.set(name, Array.isArray(value) ? value.join(',') : String(value));
  }
  return headers;
}

/* Reuse the worker's own session check so /api/ops can never be more permissive. */
async function ownerSession(req) {
  const request = new Request(`${publicOrigin(req)}/api/auth/session`, { headers: sessionHeaders(req) });
  const response = await worker.fetch(request, env, { waitUntil() {}, passThroughOnException() {} });
  if (!response.ok) return null;
  const payload = await response.json().catch(() => null);
  return payload?.authenticated ? { csrf: payload.csrf_token || '' } : null;
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Content-Length': Buffer.byteLength(body)
  });
  res.end(body);
}

function readJsonBody(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) {
        reject(Object.assign(new Error('body too large'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); } catch { reject(Object.assign(new Error('invalid JSON body'), { statusCode: 400 })); }
    });
    req.on('error', reject);
  });
}

function healthSnapshot() {
  let dbBytes = 0;
  try { dbBytes = fs.statSync(config.dbFile).size; } catch { /* first boot */ }
  let walBytes = 0;
  try { walBytes = fs.statSync(`${config.dbFile}-wal`).size; } catch { /* no wal yet */ }
  return {
    ok: true,
    app: 'Nasra Esfahan Media Studio',
    runtime: 'sillydev/node',
    version: APP_VERSION,
    node: process.version,
    started_at: startedAt.toISOString(),
    uptime_sec: Math.round(process.uptime()),
    port: config.port,
    db: { file: path.basename(config.dbFile), bytes: dbBytes, wal_bytes: walBytes, wal: config.sqliteWal },
    scheduler: scheduler.status(),
    telegram: poller.status(),
    publish_guard: {
      mode: guard.mode,
      allowlist_size: guard.allowlist.length,
      pending_approvals: guard.listApprovals().length,
      stats: guard.stats
    },
    autopilot: { running: autopilot.running, ticks: autopilot.state.ticks, last_error: autopilot.state.last_error },
    media: { dir: path.relative(ROOT, config.media.dir) || '.', public_base_url: config.publicBaseUrl || '', configured: mediaStore.configured },
    image: imageProvider.status(),
    config_warnings: validation.warnings
  };
}

/*
 * POST /api/ops/media/upload
 *
 * The browser sends the file itself as application/octet-stream, with the file
 * name in X-File-Name. No multipart parser, no dependency, and a hard size limit.
 * The response is the media-link object to attach to a content item, so the panel
 * does not have to know how storage works.
 */
async function handleUpload(req, res) {
  const session = await ownerSession(req);
  if (!session) return sendJson(res, 401, { ok: false, error: { code: 'AUTH_REQUIRED', message: 'ورود مالک لازم است' } });
  const csrf = String(req.headers['x-csrf-token'] || '');
  if (!csrf || csrf !== session.csrf) return sendJson(res, 403, { ok: false, error: { code: 'CSRF_FAILED', message: 'توکن CSRF معتبر نیست؛ صفحه را تازه کنید' } });

  let buffer;
  try {
    buffer = await readRawBody(req, MAX_UPLOAD_BYTES);
  } catch (error) {
    return sendJson(res, error.statusCode || 400, { ok: false, error: { code: 'UPLOAD_TOO_LARGE', message: error.message } });
  }
  try {
    const filename = decodeURIComponent(String(req.headers['x-file-name'] || ''));
    const mime = String(req.headers['content-type'] || '');
    const title = decodeURIComponent(String(req.headers['x-media-title'] || ''));
    const link = storeUpload(mediaStore, { buffer, filename, mime, slug: slugFrom(title) });
    console.log(`[media] upload stored: ${link.name} (${Math.round(link.size_bytes / 1024)} KB)`);
    return sendJson(res, 201, { ok: true, data: link });
  } catch (error) {
    return sendJson(res, error.statusCode || 500, { ok: false, error: { code: 'UPLOAD_FAILED', message: error.message } });
  }
}

async function handleOps(req, res, url) {
  if (url.pathname === '/api/ops/health' && req.method === 'GET') {
    const tokenOk = config.healthToken && req.headers['x-health-token'] === config.healthToken;
    const session = tokenOk ? { csrf: '' } : await ownerSession(req);
    if (!session) return sendJson(res, 401, { ok: false, error: { code: 'AUTH_REQUIRED', message: 'ورود مالک لازم است' } });
    return sendJson(res, 200, healthSnapshot());
  }

  const session = await ownerSession(req);
  if (!session) return sendJson(res, 401, { ok: false, error: { code: 'AUTH_REQUIRED', message: 'ورود مالک لازم است' } });
  if (req.method !== 'GET') {
    const csrf = String(req.headers['x-csrf-token'] || '');
    if (!csrf || csrf !== session.csrf) return sendJson(res, 403, { ok: false, error: { code: 'CSRF_FAILED', message: 'توکن CSRF معتبر نیست؛ صفحه را تازه کنید' } });
  }

  if (url.pathname === '/api/ops/guard' && req.method === 'GET') {
    return sendJson(res, 200, {
      ok: true,
      data: { mode: guard.mode, allowlist: guard.allowlist, approvals: guard.listApprovals(), stats: guard.stats, recent: guard.recent(40) }
    });
  }
  if (url.pathname === '/api/ops/guard/check' && req.method === 'POST') {
    const body = await readJsonBody(req);
    if (!body.url) return sendJson(res, 400, { ok: false, error: { code: 'URL_REQUIRED', message: 'url لازم است' } });
    const action = classifyPublishRequest(String(body.url), { method: body.method || 'POST', body: body.body ? JSON.stringify(body.body) : undefined });
    return sendJson(res, 200, { ok: true, data: { publish_action: action, would_block: Boolean(action) && guard.mode === 'dry_run' } });
  }
  if (url.pathname === '/api/ops/approvals' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const approval = guard.issueApproval({
      note: body.note,
      platform: body.platform,
      action: body.action,
      destination: body.destination,
      ttlSeconds: body.ttl_seconds
    });
    console.warn(`[publish-guard] owner confirmation issued (${approval.platform || 'any'} / ${approval.action || 'any'} / ${approval.destination || 'any'}), expires ${approval.expires_at}`);
    return sendJson(res, 201, { ok: true, data: approval });
  }
  if (url.pathname === '/api/ops/approvals' && req.method === 'GET') {
    return sendJson(res, 200, { ok: true, data: guard.listApprovals() });
  }
  if (url.pathname === '/api/ops/approvals' && req.method === 'DELETE') {
    return sendJson(res, 200, { ok: true, data: { revoked: guard.revokeApprovals() } });
  }
  if (url.pathname === '/api/ops/backup' && req.method === 'POST') {
    const result = scheduler.backupNow('manual');
    return sendJson(res, result.ok ? 201 : 500, { ok: result.ok, data: result });
  }
  if (url.pathname === '/api/ops/scheduler/tick' && req.method === 'POST') {
    return sendJson(res, 200, { ok: true, data: await scheduler.tick() });
  }
  if (url.pathname === '/api/ops/scheduler' && req.method === 'GET') {
    return sendJson(res, 200, { ok: true, data: scheduler.status() });
  }
  if (url.pathname === '/api/ops/heartbeat' && req.method === 'GET') {
    return sendJson(res, 200, { ok: true, data: readHeartbeat(config.dataDir) });
  }
  if (url.pathname === '/api/ops/autopilot' && req.method === 'GET') {
    return sendJson(res, 200, { ok: true, data: await autopilot.status() });
  }
  if (url.pathname === '/api/ops/autopilot' && req.method === 'PUT') {
    const body = await readJsonBody(req);
    const next = normalizePolicy(body);
    await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at')
      .bind('autopilot', JSON.stringify(next), new Date().toISOString()).run();
    console.warn(`[autopilot] policy set to ${next.policy} (enabled=${next.enabled}, platforms=${next.platforms.join(',')})`);
    return sendJson(res, 200, { ok: true, data: policySummary(next) });
  }
  if (url.pathname === '/api/ops/autopilot/tick' && req.method === 'POST') {
    /* The panel's "اجرای یک چرخه" button is the owner asking for work now, so it
       takes the same forced path as /now in Telegram. */
    return sendJson(res, 200, { ok: true, data: await autopilot.tick({ force: true }) });
  }
  /*
   * "Why is the page empty?" is the owner's most common question, and until now
   * nothing in the panel could answer it: a queued job, a failed agent run and a
   * key that was never set all looked identical — an empty card. These two routes
   * make the machine's own record visible, read-only.
   */
  if (url.pathname === '/api/ops/jobs' && req.method === 'GET') {
    const jobs = await db.prepare(
      `SELECT id,job_type,agent_id,status,attempts,max_attempts,last_error,run_after,created_at,updated_at
       FROM jobs ORDER BY created_at DESC LIMIT 30`
    ).all();
    const runs = await db.prepare(
      `SELECT id,agent_id,mode,status,cached,total_tokens,error_message,created_at
       FROM agent_runs ORDER BY created_at DESC LIMIT 30`
    ).all();
    const counts = await db.prepare("SELECT status, COUNT(*) AS n FROM jobs GROUP BY status").all();
    /* Whether an AI key exists is a config fact, not a secret: the panel needs it
       to explain an empty queue instead of leaving the owner guessing. */
    /* The DB may hold the key (the panel lets the owner paste it there), so check
       both places before telling them it is missing. */
    /* Keys live in .env for this deployment (agents.provider/model are per-agent
       overrides). Check the same rule validateConfig uses for its warning. */
    const aiConfigured = Boolean(env.AI_PROVIDER && (env.AI_PROVIDER === 'workers_ai' || env.AI_API_KEY));
    let mockMode = String(process.env.MOCK_AI || '') === '1';
    return sendJson(res, 200, {
      ok: true,
      data: { jobs: jobs.results || jobs, runs: runs.results || runs, counts: counts.results || counts, ai_missing: !aiConfigured && !mockMode, mock_ai: mockMode }
    });
  }
  if (url.pathname === '/api/ops/retry' && req.method === 'POST') {
    /* A retry is a deliberate owner action; it only revives jobs that already
       gave up, never one that is currently running. */
    const body = await readJsonBody(req);
    const id = String(body.id || '');
    if (!id) return sendJson(res, 400, { ok: false, error: { code: 'JOB_ID_REQUIRED', message: 'شناسه کار لازم است' } });
    const job = await db.prepare('SELECT id,status FROM jobs WHERE id=?').bind(id).first();
    if (!job) return sendJson(res, 404, { ok: false, error: { code: 'JOB_NOT_FOUND', message: 'کار یافت نشد' } });
    await db.prepare("UPDATE jobs SET status='QUEUED', attempts=0, last_error=NULL, run_after=?, locked_at=NULL, updated_at=? WHERE id=?")
      .bind(new Date().toISOString(), new Date().toISOString(), id).run();
    return sendJson(res, 200, { ok: true, data: { id, status: 'QUEUED' } });
  }
  if (url.pathname === '/api/ops/media' && req.method === 'GET') {
    return sendJson(res, 200, {
      ok: true,
      data: {
        configured: mediaStore.configured,
        public_base_url: mediaStore.publicBaseUrl,
        url_path: mediaStore.urlPath,
        files: mediaStore.list(60),
        image: imageProvider.status()
      }
    });
  }
  if (url.pathname === '/api/ops/media/upload' && req.method === 'POST') {
    /* Only reachable here when the body was not consumed by preHandle (see the
       dedicated branch): this keeps the route discoverable in one place. */
    return sendJson(res, 500, { ok: false, error: { code: 'UPLOAD_ROUTE_MISUSE', message: 'مسیر آپلود باید در ابتدای درخواست مدیریت شود' } });
  }
  if (url.pathname === '/api/ops/media/prune' && req.method === 'POST') {
    return sendJson(res, 200, { ok: true, data: mediaStore.prune({ keep: config.media.keep, maxBytes: config.media.maxBytes }) });
  }
  return sendJson(res, 404, { ok: false, error: { code: 'OPS_ROUTE_NOT_FOUND', message: 'مسیر Ops یافت نشد' } });
}

/* ------------------------------------------------------------ composition */

/*
 * Local media store: written posters are served from this process so Telegram
 * and Instagram have an HTTPS URL to download. It needs PUBLIC_BASE_URL to be
 * reachable, so it stays inert (configured=false) when that is missing.
 */
fs.mkdirSync(config.media.dir, { recursive: true });
const mediaStore = new MediaStore({
  dir: config.media.dir,
  publicBaseUrl: config.publicBaseUrl,
  urlPath: config.media.urlPath,
  logger: console
});

const imageProvider = new ImageProvider({
  provider: config.image.provider,
  apiKey: config.image.apiKey,
  models: config.image.models,
  baseUrl: config.image.baseUrl,
  timeoutMs: config.image.timeoutMs,
  stateFile: path.join(config.dataDir, 'image-model.json'),
  logger: console
});

const autopilot = new Autopilot({ db, worker, env, config, mediaStore, imageProvider, logger: console });

const scheduler = new Scheduler({ worker, env, db, config, logger: console });
const poller = new TelegramPoller({ config, env, db, worker, logger: console });

const server = createNasraServer({
  worker,
  env,
  config,
  db,
  mediaStore,
  logger: console,
  preHandle: async (req, res, url) => {
    /* Upload first: it is the only ops route that streams a file body, and the
       generic ops router rejects unknown paths before any body is read. */
    if (url.pathname === '/api/ops/media/upload' && req.method === 'POST') {
      await handleUpload(req, res);
      return true;
    }
    if (url.pathname.startsWith('/api/ops/')) {
      await handleOps(req, res, url);
      return true;
    }
    if (url.pathname === '/readyz') {
      sendJson(res, 200, { ok: true, ready: true, at: new Date().toISOString() });
      return true;
    }
    return false;
  }
});

installGuard();
scheduler.start();
autopilot.start();

/*
 * The autopilot loop is separate from the worker scheduler on purpose: the
 * scheduler must stay exactly what it was (queue drain + publish), while the
 * autopilot decides what enters the queue. Both are idempotent per slot, so a
 * slow tick never double-creates content.
 */
if (config.autopilotEnabled) {
  /*
   * Cadence: the autopilot runs every AUTOPILOT_INTERVAL_MS (default 60s), but the
   * loop wakes every 15s to check for a /now request from Telegram. A request makes
   * it run immediately instead of at the next full interval — without increasing
   * how often the pipeline itself runs.
   */
  /* Talk to the owner without going through worker.js: this is a runtime-only
     message and it must never look like a publication to the guard. */
  const notifyOwner = async text => {
    if (!process.env.TG_BOT_TOKEN || !process.env.TG_OWNER_CHAT_ID) return;
    try {
      await fetch(`https://api.telegram.org/bot${process.env.TG_BOT_TOKEN}/sendMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ chat_id: process.env.TG_OWNER_CHAT_ID, text: String(text).slice(0, 900) }),
        signal: AbortSignal.timeout(10_000)
      });
    } catch (error) {
      console.warn('[autopilot] could not notify the owner:', error?.message || error);
    }
  };

  const intervalMs = Math.max(5_000, config.autopilotIntervalMs);
  const WATCH_MS = Math.min(15_000, intervalMs);
  let lastRun = 0;
  const loop = async () => {
    while (!autopilot.stopped) {
      let due = true;
      try {
        const wake = await consumeTickRequest(db);
        const forced = Boolean(wake.requested);
        if (forced) {
          console.log('[autopilot] manual tick requested from Telegram — running now');
        } else {
          due = Date.now() - lastRun >= intervalMs;
        }
        if (due || forced) {
          const summary = await autopilot.tick({ force: forced });
          lastRun = Date.now();
          if (summary?.created || summary?.approved || summary?.posters) {
            console.log(`[autopilot] tick created=${summary.created} approved=${summary.approved} posters=${summary.posters} escalated=${summary.escalated}`);
          }
          /* /now is a question as much as an instruction. A per-minute loop stays
             quiet, but a tick the owner asked for has to answer, including when
             the answer is "nothing to write about yet". */
          if (forced) {
            const lines = ['نتیجه چرخه اتوپایلوت'];
            lines.push(`محتوای ساخته‌شده: ${summary?.created || 0}`);
            if (summary?.reason) lines.push(`دلیل: ${summary.reason}`);
            else if (summary?.skipped) lines.push(`رد شد: ${summary.skipped}`);
            else if (!summary?.created) lines.push('هیچ محتوای تازه‌ای ساخته نشد.');
            await notifyOwner(lines.join('\n'));
          }
        }
      } catch (error) {
        console.error('[autopilot] loop error:', error?.message || error);
        lastRun = Date.now();
      }
      await new Promise(resolve => setTimeout(resolve, due ? Math.max(1_000, Math.min(WATCH_MS, 5_000)) : WATCH_MS));
    }
  };
  loop().catch(error => console.error('[autopilot] loop crashed:', error?.message || error));
  console.log(`[autopilot] loop every ${Math.round(intervalMs / 1000)}s (policy is read from studio_settings each tick; /now in Telegram runs one cycle within ${Math.round(WATCH_MS / 1000)}s)`);
} else {
  autopilot.stop();
  console.log('[autopilot] disabled (AUTOPILOT_ENABLED=0)');
}

if (config.backup.intervalHours > 0) {
  const timer = setInterval(() => scheduler.backupNow('scheduled'), config.backup.intervalHours * 60 * 60 * 1000);
  if (typeof timer.unref === 'function') timer.unref();
}

server.listen(config.port, config.host, async () => {
  console.log(`[nasra] listening on http://${config.host}:${config.port} (SillyDev forwards SERVER_PORT)`);
  console.log(`[nasra] media: ${path.relative(ROOT, config.media.dir)}${mediaStore.configured ? ` → ${config.publicBaseUrl}${mediaStore.urlPath}/` : ' (PUBLIC_BASE_URL empty: local media is not fetchable by platforms)'}`);
  console.log(`[nasra] image provider: ${imageProvider.configured ? `gemini (${imageProvider.candidates[0]})` : 'not configured — posters stay manual'}`);
  console.log(`[nasra] telegram control: ${env.TG_OWNER_CHAT_ID ? 'commands accepted from the owner chat only' : 'no TG_OWNER_CHAT_ID — commands ignored'}${config.wake.secret ? ` · wake helper ${config.wake.command ? 'armed' : 'watching only'}` : ''}`);
  console.log(`[nasra] publish guard: ${guard.mode}${guard.mode === 'dry_run' ? ' — no real post can be sent' : ` — allowlist ${config.guard.allowlist.length} destination(s), owner confirmation required`}`);
  await poller.start();
});

let shuttingDown = false;
function shutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(`\n[nasra] ${signal} received; shutting down`);
  poller.stop();
  scheduler.stop();
  autopilot.stop();
  clearInterval(heartbeatTimer);
  /* A stale heartbeat is the honest signal that this process is gone; leaving
     the last one in place would make /ping claim a dead studio is alive. */
  try { fs.rmSync(path.join(config.dataDir, 'nasra-heartbeat.json'), { force: true }); } catch { /* ignore */ }
  uninstallGuard();
  try { scheduler.backupNow('shutdown'); } catch { /* best effort */ }
  const forceExit = setTimeout(() => process.exit(1), 15000);
  if (typeof forceExit.unref === 'function') forceExit.unref();
  server.close(() => {
    try { db.close(); } catch (error) { console.error('[nasra] db close failed:', error.message); }
    clearTimeout(forceExit);
    console.log('[nasra] stopped cleanly');
    process.exit(0);
  });
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('unhandledRejection', error => console.error('[nasra] unhandled rejection:', error?.message || error));
process.on('uncaughtException', error => {
  console.error('[nasra] uncaught exception:', error?.stack || error);
  shutdown('uncaughtException');
});
