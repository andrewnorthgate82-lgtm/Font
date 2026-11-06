/*
 * Telegram long polling for the self-hosted runtime.
 *
 * Webhooks need a public HTTPS URL. SillyDev gives a stable URL but the owner
 * may prefer (or need) long polling instead: no inbound request, no public
 * endpoint, no secret token header on the internet.
 *
 * How it works:
 * - getUpdates is long-polled with an explicit timeout and allowed_updates.
 * - The offset is persisted in studio_settings, so a restart never re-processes
 *   an old update and never skips a queued one.
 * - Updates are handed to the shared worker code through the same route used by
 *   the Cloudflare webhook (POST /webhooks/telegram), so decisions, dedupe and
 *   confirmations are identical in both runtimes. Nothing is duplicated here.
 * - No message is produced by this module: it only feeds owner updates in.
 */
import crypto from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const OFFSET_SETTING = 'telegram_polling_offset';

export class TelegramPoller {
  constructor({ config, env, db, worker, fetchImpl = fetch, logger = console } = {}) {
    this.config = config;
    this.env = env;
    this.db = db;
    this.worker = worker;
    this.fetchImpl = fetchImpl;
    this.logger = logger;
    this.stopped = false;
    this.running = false;
    this.abort = null;
    this.state = {
      polls: 0,
      updates: 0,
      last_poll_at: null,
      last_update_at: null,
      last_error: null,
      offset: null,
      mode: 'idle'
    };
    // The worker requires TG_SECRET on its webhook route. For long polling the
    // header never leaves the process, so a random value is generated when the
    // owner has not configured one.
    this.internalSecret = env.TG_SECRET || crypto.randomBytes(24).toString('hex');
  }

  get token() { return this.env.TG_BOT_TOKEN || ''; }

  get baseUrl() { return this.config.telegramBaseUrl; }

  async #api(method, payload = {}, { signal } = {}) {
    const response = await this.fetchImpl(`${this.baseUrl}/bot${this.token}/${method}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.ok === false) {
      const error = new Error(`Telegram ${method}: HTTP ${response.status} ${String(data.description || '').slice(0, 200)}`);
      error.status = response.status;
      error.description = String(data.description || '');
      throw error;
    }
    return data.result;
  }

  async loadOffset() {
    const row = await this.db.prepare('SELECT value_json FROM studio_settings WHERE key=?').bind(OFFSET_SETTING).first();
    if (!row?.value_json) return null;
    try {
      const parsed = JSON.parse(row.value_json);
      return Number.isFinite(Number(parsed.offset)) ? Number(parsed.offset) : null;
    } catch { return null; }
  }

  async saveOffset(offset) {
    const now = new Date().toISOString();
    await this.db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at')
      .bind(OFFSET_SETTING, JSON.stringify({ offset, saved_at: now }), now).run();
  }

  /*
   * Drop a Cloudflare-style webhook before polling. Telegram refuses getUpdates
   * with 409 while a webhook is set, so this is the documented way to switch.
   * The owner decides: TELEGRAM_DELETE_WEBHOOK=0 keeps the current webhook and
   * polling stays disabled.
   */
  async detachWebhook() {
    if (process.env.TELEGRAM_DELETE_WEBHOOK === '0') {
      this.state.mode = 'webhook-kept';
      this.logger.warn('[telegram] TELEGRAM_DELETE_WEBHOOK=0 — polling stays off, the existing webhook is kept.');
      return false;
    }
    try {
      await this.#api('deleteWebhook', { drop_pending_updates: false });
      this.logger.log('[telegram] webhook removed; long polling is now the update source.');
      return true;
    } catch (error) {
      this.logger.warn('[telegram] deleteWebhook failed:', error.message);
      return false;
    }
  }

  async start() {
    if (!this.config.telegramPolling) {
      this.state.mode = 'disabled';
      this.logger.log('[telegram] long polling disabled (TELEGRAM_POLLING=0).');
      return this;
    }
    if (!this.token) {
      this.state.mode = 'no-token';
      this.logger.warn('[telegram] no TG_BOT_TOKEN — long polling idle.');
      return this;
    }
    this.running = true;
    this.stopped = false;
    this.state.mode = 'polling';
    this.state.offset = await this.loadOffset();
    await this.detachWebhook();
    this.loop().catch(error => this.logger.error('[telegram] poller crashed:', error.message));
    return this;
  }

  async loop() {
    let backoff = 1000;
    while (!this.stopped) {
      this.abort = new AbortController();
      try {
        const result = await this.#api('getUpdates', {
          offset: this.state.offset ?? undefined,
          timeout: this.config.telegramPollTimeoutSec,
          limit: 25,
          allowed_updates: ['message', 'callback_query']
        }, { signal: AbortSignal.any([this.abort.signal, AbortSignal.timeout((this.config.telegramPollTimeoutSec + 20) * 1000)]) });
        backoff = 1000;
        this.state.polls += 1;
        this.state.last_poll_at = new Date().toISOString();
        this.state.last_error = null;
        if (Array.isArray(result) && result.length) await this.dispatch(result);
      } catch (error) {
        if (this.stopped) break;
        this.state.last_error = { at: new Date().toISOString(), message: String(error.message || error).slice(0, 400) };
        if (error.status === 401) {
          this.logger.error('[telegram] the bot token was rejected (401). Long polling stopped.');
          this.state.mode = 'unauthorized';
          this.running = false;
          return;
        }
        if (error.status === 409) {
          this.logger.warn('[telegram] another getUpdates consumer or webhook is active (409). Retrying after detaching the webhook.');
          this.state.mode = 'conflict';
          await this.detachWebhook();
        } else {
          this.logger.warn('[telegram] poll failed:', this.state.last_error.message);
        }
        await delay(backoff, null, { ref: false });
        backoff = Math.min(60_000, backoff * 2);
      }
    }
  }

  /*
   * Feed one batch into the shared worker code, in order, one at a time, and
   * persist the offset only after the worker acknowledged the update. A crash in
   * between means the same update is delivered again — the worker's dedupe table
   * (tg:<update_id>) makes that harmless.
   */
  async dispatch(updates) {
    for (const update of updates) {
      const updateId = Number(update?.update_id);
      if (!Number.isFinite(updateId)) continue;
      const request = new Request(`${this.requestOrigin()}/webhooks/telegram`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Telegram-Bot-Api-Secret-Token': this.internalSecret },
        body: JSON.stringify(update)
      });
      const waitUntil = [];
      const ctx = {
        waitUntil(promise) { const p = Promise.resolve(promise).catch(error => console.error('[telegram:background]', error?.message || error)); waitUntil.push(p); },
        passThroughOnException() {}
      };
      const env = { ...this.env, TG_SECRET: this.internalSecret };
      const response = await this.worker.fetch(request, env, ctx);
      if (response.status === 401 || response.status === 403) {
        this.logger.error('[telegram] the worker rejected the internal update dispatch; long polling stopped.');
        this.state.mode = 'dispatch-rejected';
        this.running = false;
        this.stop();
        return;
      }
      if (!response.ok) this.logger.warn(`[telegram] update ${updateId} processed with HTTP ${response.status}`);
      if (waitUntil.length) await Promise.allSettled(waitUntil);
      this.state.offset = updateId + 1;
      this.state.updates += 1;
      this.state.last_update_at = new Date().toISOString();
      await this.saveOffset(this.state.offset);
    }
  }

  requestOrigin() {
    // Only the path and method matter for the worker; a stable synthetic origin
    // keeps URL parsing and origin checks deterministic.
    return process.env.PUBLIC_BASE_URL && /^https?:\/\//.test(process.env.PUBLIC_BASE_URL)
      ? process.env.PUBLIC_BASE_URL.replace(/\/+$/, '')
      : `http://127.0.0.1:${this.config.port}`;
  }

  status() {
    return {
      enabled: this.config.telegramPolling,
      running: this.running,
      token_set: Boolean(this.token),
      base_url: this.baseUrl,
      timeout_sec: this.config.telegramPollTimeoutSec,
      ...this.state
    };
  }

  stop() {
    this.stopped = true;
    this.running = false;
    try { this.abort?.abort(); } catch { /* already aborted */ }
  }
}
