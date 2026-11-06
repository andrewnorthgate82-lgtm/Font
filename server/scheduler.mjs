/*
 * Real in-process scheduler for the self-hosted runtime.
 *
 * Cloudflare fires `scheduled()` every five minutes. SillyDev has no cron
 * binding, so this module calls the very same exported handler on a fixed
 * interval:
 *   - one tick at a time (no overlapping queue work),
 *   - every promise handed to ctx.waitUntil is awaited with a timeout, so a slow
 *     job is reported instead of silently leaking,
 *   - the 3 AM backup slot gets a durable, consistent SQLite snapshot because a
 *     self-hosted process cannot rely on Sheets alone,
 *   - failures are logged and counted, never thrown into the event loop.
 */
import fs from 'node:fs';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const MAX_WAIT_UNTIL = 5 * 60 * 1000;

export class Scheduler {
  constructor({ worker, env, db, config, logger = console, onBackup } = {}) {
    this.worker = worker;
    this.env = env;
    this.db = db;
    this.config = config;
    this.logger = logger;
    this.onBackup = onBackup;
    this.running = false;
    this.inFlight = false;
    this.stopped = false;
    this.timer = null;
    this.startedAt = new Date().toISOString();
    this.state = {
      ticks: 0,
      failures: 0,
      last_tick_at: null,
      last_tick_ms: null,
      last_error: null,
      last_summary: null,
      skipped_overlap: 0,
      last_backup_at: null,
      last_backup_file: null
    };
  }

  start() {
    if (!this.config.schedulerEnabled || this.running) return this;
    this.running = true;
    this.stopped = false;
    this.logger.log(`[scheduler] every ${this.config.schedulerIntervalMs}ms (worker.scheduled handler)`);
    this.#scheduleNext(0);
    return this;
  }

  #scheduleNext(delayMs) {
    if (this.stopped) return;
    this.timer = setTimeout(() => {
      this.tick().finally(() => this.#scheduleNext(this.config.schedulerIntervalMs));
    }, delayMs);
    if (typeof this.timer.unref === 'function') this.timer.unref();
  }

  async tick() {
    if (this.stopped) return { skipped: 'stopped' };
    if (this.inFlight) {
      this.state.skipped_overlap += 1;
      return { skipped: 'overlap' };
    }
    this.inFlight = true;
    const started = Date.now();
    const background = [];
    const ctx = {
      waitUntil(promise) {
        const guarded = Promise.resolve(promise).catch(error => {
          console.error('[scheduler:background]', error?.message || error);
        });
        background.push(guarded);
      },
      passThroughOnException() {}
    };
    try {
      const event = { cron: '*/5 * * * *', scheduledTime: started };
      await this.worker.scheduled(event, this.env, ctx);
      if (background.length) {
        await Promise.race([
          Promise.allSettled(background),
          delay(MAX_WAIT_UNTIL, null, { ref: false })
        ]);
      }
      this.state.ticks += 1;
      this.state.last_tick_at = new Date().toISOString();
      this.state.last_tick_ms = Date.now() - started;
      this.state.last_error = null;
      return { ok: true, ms: this.state.last_tick_ms, background: background.length };
    } catch (error) {
      this.state.failures += 1;
      this.state.last_error = { at: new Date().toISOString(), message: String(error?.message || error).slice(0, 500) };
      this.logger.error('[scheduler] tick failed:', this.state.last_error.message);
      return { ok: false, error: this.state.last_error.message };
    } finally {
      this.inFlight = false;
    }
  }

  /* Consistent on-disk snapshot; safe while the server keeps serving traffic. */
  backupNow(reason = 'manual') {
    const dir = this.config.backup.dir;
    fs.mkdirSync(dir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const target = path.join(dir, `nasra-${stamp}-${reason}.sqlite`);
    try {
      this.db.checkpoint('FULL');
      const escaped = target.replace(/'/g, "''");
      this.db.exec(`VACUUM INTO '${escaped}'`);
      this.state.last_backup_at = new Date().toISOString();
      this.state.last_backup_file = path.basename(target);
      this.#pruneBackups(dir);
      const size = fs.statSync(target).size;
      this.logger.log(`[scheduler] backup written: ${path.basename(target)} (${size} bytes)`);
      return { ok: true, file: target, bytes: size };
    } catch (error) {
      this.logger.error('[scheduler] backup failed:', error.message);
      return { ok: false, error: error.message };
    }
  }

  #pruneBackups(dir) {
    const keep = this.config.backup.keep;
    const files = fs.readdirSync(dir)
      .filter(name => /^nasra-.*\.sqlite$/.test(name))
      .map(name => ({ name, time: fs.statSync(path.join(dir, name)).mtimeMs }))
      .sort((a, b) => b.time - a.time);
    for (const file of files.slice(keep)) {
      try { fs.unlinkSync(path.join(dir, file.name)); } catch { /* keep going */ }
    }
  }

  status() {
    return {
      enabled: this.config.schedulerEnabled,
      running: this.running,
      in_flight: this.inFlight,
      interval_ms: this.config.schedulerIntervalMs,
      started_at: this.startedAt,
      ...this.state
    };
  }

  stop() {
    this.stopped = true;
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }
}
