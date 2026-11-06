/*
 * Environment loading for the self-hosted (SillyDev) runtime.
 *
 * Rules:
 * - Real environment variables always win.
 * - Optional .env files are parsed here (no dependency) for local runs only.
 *   SillyDev injects variables through its panel, so .env is never required.
 * - Secrets are never logged and never returned by any API response. The
 *   summary below contains booleans and non-secret values only.
 */
import fs from 'node:fs';
import path from 'node:path';

const TRUTHY = new Set(['1', 'true', 'yes', 'on', 'y']);
const FALSY = new Set(['0', 'false', 'no', 'off', 'n', '']);

export function parseDotEnv(text) {
  const out = {};
  for (const rawLine of String(text).split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#') || line.startsWith(';')) continue;
    const withoutExport = line.startsWith('export ') ? line.slice(7).trim() : line;
    const eq = withoutExport.indexOf('=');
    if (eq < 1) continue;
    const key = withoutExport.slice(0, eq).trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
    let value = withoutExport.slice(eq + 1).trim();
    const quoted = (value.startsWith('"') && value.endsWith('"') && value.length > 1) || (value.startsWith("'") && value.endsWith("'") && value.length > 1);
    if (quoted) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, '');
    out[key] = value;
  }
  return out;
}

export function loadEnvFiles(rootDir, fileName = '.env') {
  const loaded = [];
  const file = path.join(rootDir, fileName);
  if (!fs.existsSync(file)) return loaded;
  const parsed = parseDotEnv(fs.readFileSync(file, 'utf8'));
  for (const [key, value] of Object.entries(parsed)) {
    if (process.env[key] === undefined) {
      process.env[key] = value;
      loaded.push(key);
    }
  }
  return loaded;
}

const bool = (value, fallback = false) => {
  if (value === undefined || value === null) return fallback;
  const normalized = String(value).trim().toLowerCase();
  if (TRUTHY.has(normalized)) return true;
  if (FALSY.has(normalized)) return false;
  return fallback;
};

const int = (value, fallback, min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER) => {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(min, Math.min(max, parsed));
};

const list = (value) => String(value || '').split(',').map(v => v.trim()).filter(Boolean);

export const PROVIDER_HINT = 'Set AI_PROVIDER=gemini|openai_compatible|openai and AI_API_KEY, or leave both empty and let agents run in manual mode.';

export function readConfig(env = process.env, { rootDir = process.cwd() } = {}) {
  const publicDir = path.resolve(rootDir, env.PUBLIC_DIR || 'public');
  const dataDir = path.resolve(rootDir, env.DATA_DIR || 'data');
  const config = {
    nodeEnv: env.NODE_ENV || 'production',
    port: int(env.SERVER_PORT ?? env.PORT, 8787, 1, 65535),
    host: env.HOST || '0.0.0.0',
    publicDir,
    dataDir,
    schemaFile: path.resolve(rootDir, env.SCHEMA_FILE || 'schema.sql'),
    dbFile: path.resolve(rootDir, env.DB_FILE || path.join(dataDir, 'nasra.sqlite')),
    dbBusyTimeoutMs: int(env.DB_BUSY_TIMEOUT_MS, 5000, 500, 60000),
    sqliteWal: bool(env.SQLITE_WAL, true),
    // Runtime-only knobs for the scheduler.
    schedulerEnabled: bool(env.SCHEDULER_ENABLED, true),
    schedulerIntervalMs: int(env.SCHEDULER_INTERVAL_MS, 5000, 1000, 300000),
    telegramPolling: bool(env.TELEGRAM_POLLING, true),
    telegramPollTimeoutSec: int(env.TELEGRAM_POLL_TIMEOUT_SEC, 25, 1, 50),
    telegramBaseUrl: (env.TELEGRAM_BASE_URL || 'https://api.telegram.org').replace(/\/+$/, ''),
    allowInsecureTelegramBase: bool(env.ALLOW_INSECURE_TELEGRAM_BASE, false),
    ownerPassword: env.OWNER_PASSWORD || '',
    sessionSecret: env.SESSION_SECRET || '',
    trustedProxyHttps: bool(env.TRUST_PROXY_HTTPS, true),
    allowedOrigins: list(env.ALLOWED_ORIGINS),
    guard: {
      mode: (env.PUBLISH_GUARD_MODE || 'dry_run').toLowerCase() === 'live' ? 'live' : 'dry_run',
      allowlist: list(env.PUBLISH_ALLOWLIST),
      approvalTtlMs: int(env.PUBLISH_APPROVAL_TTL_SEC, 600, 30, 3600) * 1000
    },
    backup: {
      dir: path.resolve(rootDir, env.BACKUP_DIR || path.join(dataDir, 'backups')),
      intervalHours: int(env.BACKUP_INTERVAL_HOURS, 24, 1, 24 * 7),
      keep: int(env.BACKUP_KEEP, 14, 1, 200)
    },
    mockAi: bool(env.MOCK_AI, false),
    healthToken: env.HEALTH_TOKEN || '',
    publicBaseUrl: /^https?:\/\//.test(env.PUBLIC_BASE_URL || '') ? env.PUBLIC_BASE_URL.replace(/\/+$/, '') : ''
  };
  return config;
}

/*
 * Translate config into the `env` object worker.js expects.
 * Anything not configured stays empty, so the app reports "not configured"
 * instead of failing at boot.
 */
export function workerEnv(config, source = process.env) {
  const env = {
    OWNER_PASSWORD: config.ownerPassword,
    SESSION_SECRET: config.sessionSecret,
    TG_BOT_TOKEN: source.TG_BOT_TOKEN || '',
    TG_OWNER_CHAT_ID: source.TG_OWNER_CHAT_ID || '',
    TG_SECRET: source.TG_SECRET || '',
    AI_PROVIDER: source.AI_PROVIDER || '',
    AI_API_KEY: source.AI_API_KEY || '',
    AI_BASE_URL: source.AI_BASE_URL || '',
    GAS_URL: source.GAS_URL || '',
    GAS_KEY: source.GAS_KEY || '',
    IG_APP_SECRET: source.IG_APP_SECRET || '',
    IG_VERIFY_TOKEN: source.IG_VERIFY_TOKEN || '',
    IG_ACCESS_TOKEN: source.IG_ACCESS_TOKEN || '',
    IG_USER_ID: source.IG_USER_ID || '',
    RUBIKA_BOT_TOKEN: source.RUBIKA_BOT_TOKEN || '',
    ALLOWED_ORIGINS: config.allowedOrigins.join(','),
    // Self-hosted runs have no Workers AI and no R2 binding. Both are optional.
    AI: null,
    R2: null
  };
  return env;
}

export function configSummary(config) {
  return {
    node: process.version,
    port: config.port,
    host: config.host,
    data_dir: config.dataDir,
    db_file: config.dbFile,
    scheduler: { enabled: config.schedulerEnabled, interval_ms: config.schedulerIntervalMs },
    telegram_polling: config.telegramPolling,
    publish_guard: config.guard,
    owner_password_set: Boolean(config.ownerPassword),
    session_secret_set: Boolean(config.sessionSecret),
    allowed_origins: config.allowedOrigins,
    public_base_url: config.publicBaseUrl || '(derived from request headers)'
  };
}

export function validateConfig(config, env = process.env) {
  const problems = [];
  const warnings = [];
  if (!config.ownerPassword) problems.push('OWNER_PASSWORD is required.');
  else if (config.ownerPassword.length < 10) problems.push('OWNER_PASSWORD must be at least 10 characters.');
  if (!config.sessionSecret) problems.push('SESSION_SECRET is required.');
  else if (config.sessionSecret.length < 32) problems.push('SESSION_SECRET must be at least 32 characters (use: openssl rand -hex 32).');
  if (config.guard.mode === 'live' && !config.guard.allowlist.length) {
    problems.push('PUBLISH_GUARD_MODE=live requires PUBLISH_ALLOWLIST (owner chat id and/or channel id).');
  }
  if (config.telegramPolling && !env.TG_BOT_TOKEN) warnings.push('TELEGRAM_POLLING is on but TG_BOT_TOKEN is empty; polling stays idle.');
  if (config.telegramPolling && env.TG_BOT_TOKEN && !env.TG_OWNER_CHAT_ID) warnings.push('TG_OWNER_CHAT_ID is empty; every Telegram update will be ignored.');
  if (config.telegramBaseUrl) {
    let url = null;
    try { url = new URL(config.telegramBaseUrl); } catch { problems.push('TELEGRAM_BASE_URL is not a valid URL.'); }
    if (url) {
      const local = ['127.0.0.1', 'localhost', '::1'].includes(url.hostname);
      if (url.protocol !== 'https:' && !(config.allowInsecureTelegramBase && local)) {
        problems.push('TELEGRAM_BASE_URL must use HTTPS (plain HTTP is only allowed for 127.0.0.1 in local tests).');
      }
    }
  }
  if (config.mockAi) warnings.push('MOCK_AI=1: AI answers are a local stub (development only).');
  if (config.guard.mode === 'dry_run') warnings.push('Publish guard is in dry_run: no real post can leave this process.');
  if (!env.AI_PROVIDER || (env.AI_PROVIDER !== 'workers_ai' && !env.AI_API_KEY)) warnings.push(`No usable AI provider; agents will queue in manual mode. ${PROVIDER_HINT}`);
  return { ok: problems.length === 0, problems, warnings };
}
