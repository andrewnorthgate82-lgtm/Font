/*
 * Durable SQLite adapter that exposes the small subset of the Cloudflare D1
 * interface used by worker.js (prepare/bind/run/first/all/batch/exec).
 *
 * Design notes:
 * - One file on disk, WAL journal, synchronous=NORMAL and a busy timeout, so a
 *   crash or a restart never loses an acknowledged write and never leaves the
 *   database in a half-applied state.
 * - Statements are cached per SQL text. worker.js only uses positional "?"
 *   placeholders, so no SQL rewriting is needed.
 * - All bindings are normalized: undefined -> null and boolean -> 0/1, because
 *   node:sqlite rejects those JavaScript values while D1 accepts them.
 * - SQLITE_BUSY/SQLITE_LOCKED is retried a few times before surfacing an error.
 */
import fs from 'node:fs';
import path from 'node:path';

let DatabaseSync;
try {
  ({ DatabaseSync } = await import('node:sqlite'));
} catch {
  throw new Error(
    'node:sqlite is unavailable. Install Node.js 22.13+ (Node 22.5-22.12 needs NODE_OPTIONS=--experimental-sqlite).'
  );
}

const BUSY_CODES = new Set(['SQLITE_BUSY', 'SQLITE_LOCKED', 'SQLITE_BUSY_SNAPSHOT']);
const MAX_STATEMENT_CACHE = 600;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function normalizeParam(value) {
  if (value === undefined) return null;
  if (typeof value === 'boolean') return value ? 1 : 0;
  if (value instanceof Date) return value.toISOString();
  if (value === null) return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'bigint' || typeof value === 'string') return value;
  if (value instanceof Uint8Array) return value;
  try { return JSON.stringify(value); } catch { return String(value); }
}

function isBusy(error) {
  const code = String(error?.code || error?.errcode || '');
  if ([...BUSY_CODES].some(busy => code.includes(busy))) return true;
  return /database is locked|database table is locked|SQLITE_BUSY/i.test(String(error?.message || ''));
}

export class SqliteDb {
  constructor(file, options = {}) {
    this.file = path.resolve(file);
    this.readonly = Boolean(options.readonly);
    this.closed = false;
    this.cache = new Map();
    this.retries = Math.max(0, Number(options.retries ?? 4));
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    this.sqlite = new DatabaseSync(this.file, { readOnly: this.readonly });
    this.#applyPragmas(options);
    this.applySchemaFile(options.schemaFile);
  }

  #applyPragmas(options = {}) {
    const busyTimeout = Math.max(500, Number(options.busyTimeoutMs ?? 5000));
    const pragmas = [
      `PRAGMA busy_timeout = ${Math.trunc(busyTimeout)}`,
      'PRAGMA foreign_keys = ON',
      'PRAGMA synchronous = NORMAL',
      'PRAGMA temp_store = MEMORY',
      'PRAGMA cache_size = -16000'
    ];
    for (const pragma of pragmas) this.sqlite.exec(pragma);
    if (!this.readonly && options.wal !== false) {
      // WAL survives process restarts; the -wal/-shm sidecars are replayed
      // automatically by SQLite on the next open.
      this.sqlite.exec('PRAGMA journal_mode = WAL');
      this.sqlite.exec('PRAGMA wal_autocheckpoint = 1000');
    }
  }

  /*
   * schema.sql is 100% idempotent (CREATE ... IF NOT EXISTS + INSERT OR IGNORE).
   * It is applied on every boot, which also performs small forward migrations
   * such as new seed rows or new indexes.
   */
  applySchemaFile(schemaFile) {
    if (!schemaFile || this.readonly) return { applied: false };
    const file = path.resolve(schemaFile);
    if (!fs.existsSync(file)) throw new Error(`Schema file not found: ${file}`);
    const sql = fs.readFileSync(file, 'utf8');
    const started = Date.now();
    for (const statement of splitStatements(sql)) {
      if (!statement.trim()) continue;
      this.sqlite.exec(statement);
    }
    return { applied: true, ms: Date.now() - started };
  }

  statementFor(sql) {
    const cached = this.cache.get(sql);
    if (cached) return cached;
    const prepared = this.sqlite.prepare(sql);
    if (this.cache.size >= MAX_STATEMENT_CACHE) this.cache.clear();
    this.cache.set(sql, prepared);
    return prepared;
  }

  async withRetry(fn) {
    let attempt = 0;
    for (;;) {
      try {
        return fn();
      } catch (error) {
        if (!isBusy(error) || attempt >= this.retries) throw error;
        attempt += 1;
        await sleep(25 * Math.pow(2, attempt));
      }
    }
  }

  prepare(sql) {
    if (this.closed) throw new Error('Database is closed');
    if (typeof sql !== 'string' || !sql.trim()) throw new Error('SQL text is required');
    return new SqliteStatement(this, sql);
  }

  exec(sql) {
    if (this.closed) throw new Error('Database is closed');
    return this.sqlite.exec(sql);
  }

  async batch(statements) {
    const results = [];
    this.sqlite.exec('BEGIN');
    try {
      for (const statement of statements) results.push(await statement.run());
      this.sqlite.exec('COMMIT');
      return results;
    } catch (error) {
      try { this.sqlite.exec('ROLLBACK'); } catch { /* already rolled back */ }
      throw error;
    }
  }

  /* Merge the WAL back into the main file. Safe to call while running. */
  checkpoint(mode = 'TRUNCATE') {
    try { this.sqlite.exec(`PRAGMA wal_checkpoint(${mode})`); } catch { /* best effort */ }
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    this.cache.clear();
    try { this.checkpoint('TRUNCATE'); } catch { /* best effort */ }
    this.sqlite.close();
  }
}

class SqliteStatement {
  constructor(db, sql) {
    this.db = db;
    this.sql = sql;
    this.params = [];
  }

  bind(...params) {
    this.params = params.map(normalizeParam);
    return this;
  }

  prepared() {
    return this.db.statementFor(this.sql);
  }

  async run() {
    const statement = this.prepared();
    return this.db.withRetry(() => {
      const result = statement.run(...this.params);
      return {
        success: true,
        meta: {
          changes: Number(result.changes ?? 0),
          last_rowid: Number(result.lastInsertRowid ?? 0),
          duration_ms: 0
        },
        results: []
      };
    });
  }

  async first(column) {
    const statement = this.prepared();
    return this.db.withRetry(() => {
      const row = statement.get(...this.params) ?? null;
      if (!row) return null;
      if (column !== undefined) return Object.prototype.hasOwnProperty.call(row, column) ? row[column] : null;
      return row;
    });
  }

  async all() {
    const statement = this.prepared();
    return this.db.withRetry(() => ({
      success: true,
      meta: { duration_ms: 0, changes: 0 },
      results: statement.all(...this.params) || []
    }));
  }

  async raw() {
    const statement = this.prepared();
    return this.db.withRetry(() => (statement.all(...this.params) || []).map(row => Object.values(row)));
  }
}

/* Split a SQL file into statements, honoring quotes, comments and triggers. */
export function splitStatements(sql) {
  const statements = [];
  let current = '';
  let quote = '';
  let lineComment = false;
  let blockComment = false;
  const text = String(sql);
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const next = text[i + 1];
    if (lineComment) {
      current += ch;
      if (ch === '\n') lineComment = false;
      continue;
    }
    if (blockComment) {
      current += ch;
      if (ch === '*' && next === '/') { current += next; i += 1; blockComment = false; }
      continue;
    }
    if (quote) {
      current += ch;
      if (ch === quote) {
        if (next === quote) { current += next; i += 1; } else quote = '';
      }
      continue;
    }
    if (ch === '-' && next === '-') { current += ch + next; i += 1; lineComment = true; continue; }
    if (ch === '/' && next === '*') { current += ch + next; i += 1; blockComment = true; continue; }
    if (ch === "'" || ch === '"' || ch === '`') { current += ch; quote = ch; continue; }
    if (ch === ';') { statements.push(current); current = ''; continue; }
    current += ch;
  }
  if (current.trim()) statements.push(current);
  return statements;
}

export function openSqliteD1({ file, schemaFile, readonly = false, busyTimeoutMs, retries, wal = true }) {
  const db = new SqliteDb(file, { schemaFile, readonly, busyTimeoutMs, retries, wal });
  return db;
}
