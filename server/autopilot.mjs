/*
 * Autopilot: the part that actually closes the loop.
 *
 * worker.js already has a deterministic state machine and a five-minute
 * scheduler that drains jobs and publish_jobs. What it never did was *feed*
 * itself: every new piece of content had to be created by hand, every gate
 * needed a click, and the poster had to be converted to JPEG in a browser.
 *
 * This module adds the feed, and nothing else. Rules it obeys:
 *
 * 1. The state machine stays the only thing that moves content between states.
 *    Autopilot never writes a state directly; it calls the same decideGate /
 *    queueAgent path the owner's buttons use.
 * 2. It approves on the owner's behalf only when the published policy allows it
 *    (auto_publish.policy) AND the deterministic checks are clean. A single
 *    risk flag, a fact-check that is not `pass`, a missing source, or an
 *    unparsable model answer sends the item to the owner instead. On a public
 *    channel a wrong post cannot be unsent — especially on Rubika, whose Bot
 *    API has no delete — so every automatic approval is a logged decision.
 * 3. It is idempotent per time slot. A restart, an overlapping tick, or two
 *    scheduler ticks in the same minute cannot create duplicate content.
 * 4. Every action it takes is written to audit_logs with actor
 *    `system:autopilot`, so the owner can always see what the machine did.
 *
 * Policies:
 *   off        - nothing is created or auto-approved (default, safest)
 *   assisted   - content is created and posters are generated, but the owner
 *                approves all three gates (analytics only)
 *   auto_gates - same as assisted, plus gate 1/2/3 are auto-approved when the
 *                checks are clean, and posts are published at the scheduled time
 *   full_auto  - auto_gates, plus it keeps N content items in flight per day
 */
import { setTimeout as delay } from 'node:timers/promises';
import { buildPosterPrompt, extensionForMime } from './image-provider.mjs';
import { pngToJpeg, isJpeg } from './png-to-jpeg.mjs';

const MINUTE = 60 * 1000;
const DAY = 24 * 60 * MINUTE;

/* Slots are expressed in Tehran local time, matching the scheduler's day keys. */
const DEFAULT_SLOTS = ['09:00', '21:00'];
const POLICIES = new Set(['off', 'assisted', 'auto_gates', 'full_auto']);

function int(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(min, Math.min(max, parsed));
}

function boolish(value, fallback = false) {
  if (value === undefined || value === null) return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).trim().toLowerCase());
}

/*
 * Policy lives in studio_settings under `autopilot`, so the owner edits it from
 * the UI and it survives restarts with the rest of the data.
 */
export function normalizePolicy(input = {}) {
  const policy = String(input.policy || 'off').toLowerCase();
  const platforms = (Array.isArray(input.platforms) ? input.platforms : ['telegram'])
    .map(p => String(p).toLowerCase())
    .filter(p => ['telegram', 'rubika', 'instagram'].includes(p));
  return {
    policy: POLICIES.has(policy) ? policy : 'off',
    enabled: boolish(input.enabled, policy !== 'off'),
    platforms: platforms.length ? [...new Set(platforms)] : ['telegram'],
    daily_slots: (Array.isArray(input.daily_slots) ? input.daily_slots : DEFAULT_SLOTS)
      .map(t => String(t).trim())
      .filter(t => {
        const match = t.match(/^(\d{1,2}):(\d{2})$/);
        return Boolean(match) && Number(match[1]) < 24 && Number(match[2]) < 60;
      })
      .slice(0, 6),
    max_new_per_day: int(input.max_new_per_day, 2, 0, 10),
    auto_reject_risky: boolish(input.auto_reject_risky, false),
    require_source: boolish(input.require_source, true),
    /* Told to the owner on Telegram when a gate stops and waits for a human.
       On by default: on a laptop this is the whole point of the bot. */
    notify: boolish(input.notify, true)
  };
}

/* Public view: never leak anything secret; settings are already non-secret. */
export function policySummary(policy) {
  const normalized = normalizePolicy(policy);
  return {
    ...normalized,
    auto_approve_gates: normalized.policy === 'auto_gates' || normalized.policy === 'full_auto',
    keeps_queue_full: normalized.policy === 'full_auto'
  };
}

/*
 * Decide what to do with a gate decision that the machine is considering.
 *
 * This is the safety core and is deliberately a pure function so the acceptance
 * tests can pin it down without a database.
 */
export function gatePolicy({ gateNumber = 0, content = {}, policy = {}, factCheck = null, validator = null, production = null } = {}) {
  const normalized = normalizePolicy(policy);
  const block = reason => ({ allow: false, reason, action: 'to_owner' });
  if (!normalized.enabled) return block('AUTOPILOT_OFF');
  if (normalized.policy === 'assisted') return block('POLICY_ASSISTED_ONLY');

  const idea = (() => {
    try {
      const parsed = typeof content.ideation_json === 'string' ? JSON.parse(content.ideation_json) : content.ideation_json;
      return parsed?.ideas?.[Number(content.selected_idea_index ?? 0)] || parsed?.ideas?.[0] || null;
    } catch { return null; }
  })();
  const riskFlags = []
    .concat(idea?.risk_flags || [])
    .concat(idea?.needs_info || [])
    .concat(content.risk_flags || []);
  if (riskFlags.filter(Boolean).length) return block('RISK_FLAGS_PRESENT');

  if (gateNumber === 1) {
    try {
      const parsed = typeof content.ideation_json === 'string' ? JSON.parse(content.ideation_json) : content.ideation_json;
      const ideas = parsed?.ideas || [];
      const index = Number(content.selected_idea_index ?? 0);
      const chosen = ideas[index];
      if (!chosen) return block('NO_IDEA');
      if (!['post', 'carousel', 'reel', 'story'].includes(String(chosen.format || ''))) return block('UNSUPPORTED_FORMAT');
      if (chosen.needs_info && chosen.needs_info.length) return block('IDEA_NEEDS_INFO');
      return { allow: true, reason: 'CLEAN_IDEA', action: 'approve', selected_idea_index: index };
    } catch { return block('IDEATION_UNREADABLE'); }
  }

  if (gateNumber === 2) {
    let fact = factCheck;
    try { fact = fact || JSON.parse(content.fact_check_json || 'null'); } catch { fact = null; }
    if (!fact) return block('FACT_CHECK_MISSING');
    if (String(fact.overall || '').toLowerCase() !== 'pass') return block(`FACT_OVERALL_${String(fact.overall || 'unknown').toUpperCase()}`);
    const claims = Array.isArray(fact.claims) ? fact.claims : [];
    const unsupported = claims.filter(c => ['نیازمند منبع', 'نادرست', 'گمراه‌کننده'].includes(String(c?.verdict || '')));
    if (unsupported.length) return block('CLAIMS_UNSUPPORTED');
    const flags = [].concat(fact.risk_flags || [], fact.needs_info || []).filter(Boolean);
    if (flags.length) return block('FACT_FLAGS_PRESENT');
    return { allow: true, reason: 'CLEAN_FACT_CHECK', action: 'approve' };
  }

  if (gateNumber === 3) {
    const report = validator || (() => {
      try { return JSON.parse(content.validator_report_json || 'null'); } catch { return null; }
    })();
    if (!report) return block('VALIDATOR_REPORT_MISSING');
    if (report.ok !== true) return block('VALIDATOR_FAILED');
    const fails = (report.issues || []).filter(i => i.status === 'fail');
    if (fails.length) return block('VALIDATOR_ISSUES');
    const media = (() => {
      try { return JSON.parse(content.media_links_json || '[]'); } catch { return []; }
    })();
    if (!Array.isArray(media) || !media.length) return block('NO_MEDIA_ATTACHED');
    if (normalized.require_source) {
      /* Gate 3 re-checks the fact pass, because a package can reach here through
       * a rerun that changed the script after gate 2 was approved. Zero claims
       * means fact_checker had no sources at all: not a pass we can trust. */
      let fact = factCheck;
      try { fact = fact || JSON.parse(content.fact_check_json || 'null'); } catch { fact = null; }
      if (!fact) return block('FACT_CHECK_MISSING');
      const overall = String(fact.overall || '').toLowerCase();
      if (overall !== 'pass') return block(`FACT_OVERALL_${overall.toUpperCase() || 'UNKNOWN'}`);
      const claims = Array.isArray(fact.claims) ? fact.claims : [];
      if (!claims.length) return block('NO_SOURCED_CLAIMS');
      if (claims.some(c => ['نیازمند منبع', 'نادرست', 'گمراه‌کننده'].includes(String(c?.verdict || '')))) return block('CLAIMS_UNSUPPORTED');
      const factFlags = [].concat(fact.risk_flags || [], fact.needs_info || []).filter(Boolean);
      if (factFlags.length) return block('FACT_FLAGS_PRESENT');
    }
    if (production && typeof production === 'object') {
      const empty = Object.values(production).every(v => !v || (typeof v === 'object' && !Object.keys(v).length));
      if (empty) return block('NO_PRODUCTION_OUTPUT');
    }
    return { allow: true, reason: 'CLEAN_PACKAGE', action: 'approve' };
  }
  return block('UNKNOWN_GATE');
}

/* ------------------------------------------------------------------ runtime */

export class Autopilot {
  constructor({ db, worker, env, config, mediaStore = null, imageProvider = null, logger = console, clock = Date.now } = {}) {
    this.db = db;
    this.worker = worker;
    this.env = env;
    this.config = config;
    this.mediaStore = mediaStore;
    this.imageProvider = imageProvider;
    this.logger = logger;
    this.clock = clock;
    this.running = false;
    this.inFlight = false;
    this.stopped = false;
    this.state = {
      ticks: 0,
      last_tick_at: null,
      last_error: null,
      last_summary: null,
      created: 0,
      approved: 0,
      posters: 0,
      escalated: 0
    };
  }

  /* The DB helpers below are the same positional-? contract worker.js uses. */
  async #first(sql, params = []) {
    return await this.db.prepare(sql).bind(...params).first();
  }

  async #all(sql, params = []) {
    const result = await this.db.prepare(sql).bind(...params).all();
    return result?.results || [];
  }

  async #run(sql, params = []) {
    return await this.db.prepare(sql).bind(...params).run();
  }

  async #changed(result) {
    return Number(result?.meta?.changes ?? result?.changes ?? 0) > 0;
  }

  async #audit(action, entityType, entityId, details = null) {
    try {
      await this.#run(
        'INSERT INTO audit_logs (id,actor,action,entity_type,entity_id,details_json,ip_hash,created_at) VALUES (?,?,?,?,?,?,?,?)',
        [`log_${crypto.randomUUID()}`, 'system:autopilot', action, entityType, entityId, details ? JSON.stringify(details) : null, null, new Date(this.clock()).toISOString()]
      );
    } catch (error) {
      this.logger.warn?.(`[autopilot] audit failed: ${error.message}`);
    }
  }

  /*
   * Call the worker's own HTTP surface in-process and finish its background work.
   *
   * worker.js hands follow-up stages (scriptwriter after gate 1, fact_checker
   * after the script, ...) to ctx.waitUntil. Dropping those promises leaves the
   * job claimed in RUNNING with nobody to finish it, so every call here waits for
   * them — with a ceiling, so one slow model call cannot block the whole loop.
   */
  async #callWorker(pathname, { method = 'POST', body = null, timeoutMs = 180000 } = {}) {
    const init = {
      method,
      headers: { 'Content-Type': 'application/json', 'X-Nasra-Autopilot': '1' }
    };
    if (body !== null) init.body = JSON.stringify(body);
    const request = new Request(`http://127.0.0.1${pathname}`, init);
    const background = [];
    const ctx = {
      waitUntil(promise) {
        background.push(Promise.resolve(promise).catch(error => {
          this.logger.warn?.(`[autopilot] background step failed: ${error?.message || error}`);
        }));
      },
      passThroughOnException() {}
    };
    const response = await this.worker.fetch(request, { ...this.env, NASRA_AUTOPILOT: '1' }, ctx);
    const payload = await response.json().catch(() => ({}));
    if (background.length) {
      await Promise.race([
        Promise.allSettled(background),
        delay(timeoutMs, null, { ref: false })
      ]);
    }
    return { response, payload };
  }

  async #setting(key, fallback = null) {
    const row = await this.#first('SELECT value_json FROM studio_settings WHERE key=?', [key]);
    if (!row?.value_json) return fallback;
    try { return JSON.parse(row.value_json); } catch { return fallback; }
  }

  async #putSetting(key, value) {
    const now = new Date(this.clock()).toISOString();
    await this.#run(
      'INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at',
      [key, JSON.stringify(value), now]
    );
  }

  /*
   * One slot marker per day+slot, written with an INSERT OR IGNORE so two ticks
   * racing in the same minute cannot both "win" the slot.
   */
  async #claimSlot(key) {
    const now = new Date(this.clock()).toISOString();
    const result = await this.#run(
      'INSERT OR IGNORE INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?)',
      [`autopilot:slot:${key}`, JSON.stringify({ at: now }), now]
    );
    return await this.#changed(result);
  }

  async policy() {
    return normalizePolicy(await this.#setting('autopilot', { policy: 'off' }));
  }

  /* Owner brake, shared with the Telegram control module. */
  async #paused() {
    const value = await this.#setting('publish_paused', { paused: false });
    return Boolean(value?.paused);
  }

  async paused() {
    return await this.#paused();
  }

  /*
   * One short Telegram message to the owner, at most once per entity.
   *
   * Why dedupe: the loop runs every minute and the same pending gate would
   * otherwise produce a message per tick, which is how a helpful bot becomes a
   * muted bot. The audit row is the receipt.
   */
  async #notifyOnce(action, entityType, entityId, text) {
    try {
      const seen = await this.#first(
        'SELECT id FROM audit_logs WHERE action=? AND entity_id=? LIMIT 1',
        [action, entityId]
      );
      if (seen) return false;
      await this.#audit(action, entityType, entityId, { notified: true });
      await this.#callWorker('/api/ops/notify', { body: { text: String(text).slice(0, 900) } });
      return true;
    } catch (error) {
      this.logger.warn?.(`[autopilot] notify failed: ${error.message}`);
      return false;
    }
  }

  async status() {
    const policy = policySummary(await this.policy());
    const [inFlight, pendingGates, scheduled, publishedToday] = await Promise.all([
      this.#first("SELECT COUNT(*) AS n FROM content_items WHERE state NOT IN ('PUBLISHED','REJECTED','ERROR')"),
      this.#first("SELECT COUNT(*) AS n FROM gate_decisions WHERE status='PENDING'"),
      this.#first("SELECT COUNT(*) AS n FROM publish_jobs WHERE status IN ('SCHEDULED','RETRY','QUEUED_LIMIT','POLLING_CONTAINER')"),
      this.#first("SELECT COUNT(*) AS n FROM publish_jobs WHERE status='PUBLISHED' AND published_at>=?", [new Date(this.clock() - DAY).toISOString()])
    ]);
    return {
      policy,
      paused: await this.#paused(),
      running: this.running,
      image_provider: this.imageProvider?.status?.() || { configured: false },
      media_store: {
        configured: Boolean(this.mediaStore?.configured),
        public_base_url: this.mediaStore?.publicBaseUrl || '',
        files: this.mediaStore?.list(200).length || 0
      },
      content_in_flight: Number(inFlight?.n || 0),
      pending_gates: Number(pendingGates?.n || 0),
      scheduled_jobs: Number(scheduled?.n || 0),
      published_last_24h: Number(publishedToday?.n || 0),
      ...this.state
    };
  }

  /* ------------------------------------------------------------------ tick */

  async tick() {
    if (this.stopped) return { skipped: 'stopped' };
    if (this.inFlight) return { skipped: 'overlap' };
    this.inFlight = true;
    const started = this.clock();
    const summary = { created: 0, approved: 0, escalated: 0, posters: 0, slots: [] };
    try {
      const policy = await this.policy();
      if (!policy.enabled || policy.policy === 'off') {
        this.state.last_summary = { ...summary, skipped: 'policy_off' };
        return this.state.last_summary;
      }
      /* The owner's /pause from Telegram is an absolute brake: no new content is
         created and no gate is auto-approved while it is set. Publishing itself
         is stopped by the runtime, so this only has to stop the front of the
         pipeline. */
      if (await this.#paused()) {
        this.state.last_summary = { ...summary, skipped: 'paused' };
        return this.state.last_summary;
      }

      /* 1. Fill the calendar slots that are due now. */
      for (const slot of policy.daily_slots) {
        const day = this.#tehranDayKey();
        if (!this.#slotDue(slot)) continue;
        if (!(await this.#claimSlot(`${day}-${slot}`))) continue;
        summary.slots.push(slot);
        const created = await this.#createForSlot(policy, slot);
        if (created) summary.created += 1;
      }

      /* 1b. full_auto keeps a queue of work instead of waiting for the next slot. */
      if (policy.policy === 'full_auto') {
        const inFlight = await this.#first("SELECT COUNT(*) AS n FROM content_items WHERE state NOT IN ('PUBLISHED','REJECTED','ERROR')");
        if (Number(inFlight?.n || 0) < policy.max_new_per_day) {
          const created = await this.#createForSlot(policy, 'top-up');
          if (created) summary.created += 1;
        }
      }

      /* 2. Generate the poster for anything waiting on media. */
      if (policy.policy !== 'off') summary.posters += await this.#producePosters(policy);

      /* 3. Approve gates the policy allows, and nudge the pipeline forward. */
      if (policy.policy === 'auto_gates' || policy.policy === 'full_auto') {
        const result = await this.#approvePendingGates(policy);
        summary.approved += result.approved;
        summary.escalated += result.escalated;
      }

      this.state.ticks += 1;
      this.state.last_tick_at = new Date(this.clock()).toISOString();
      this.state.last_error = null;
      this.state.created += summary.created;
      this.state.approved += summary.approved;
      this.state.posters += summary.posters;
      this.state.escalated += summary.escalated;
      this.state.last_summary = { ...summary, ms: this.clock() - started };
      if (summary.created || summary.approved || summary.posters) {
        await this.#audit('autopilot_tick', 'system', null, summary);
        this.logger.log?.(`[autopilot] created=${summary.created} approved=${summary.approved} posters=${summary.posters} escalated=${summary.escalated}`);
      }
      return this.state.last_summary;
    } catch (error) {
      this.state.last_error = { at: new Date(this.clock()).toISOString(), message: String(error.message || error).slice(0, 500) };
      this.state.last_summary = { ...summary, error: this.state.last_error.message };
      this.logger.error?.('[autopilot] tick failed:', error.message);
      await this.#audit('autopilot_tick_failed', 'system', null, this.state.last_error);
      return this.state.last_summary;
    } finally {
      this.inFlight = false;
    }
  }

  #tehranDayKey(date = new Date(this.clock())) {
    /* Iran is UTC+03:30 all year; no DST, so a fixed offset is exact. */
    const shifted = new Date(date.getTime() + 210 * MINUTE);
    return shifted.toISOString().slice(0, 10);
  }

  #tehranClock(date = new Date(this.clock())) {
    const shifted = new Date(date.getTime() + 210 * MINUTE);
    return { hour: shifted.getUTCHours(), minute: shifted.getUTCMinutes() };
  }

  /* A slot fires when the local clock is inside the slot's 20-minute window. */
  #slotDue(slot) {
    const [hour, minute] = String(slot).split(':').map(n => Number(n) || 0);
    const now = this.#tehranClock();
    const nowMinutes = now.hour * 60 + now.minute;
    const target = hour * 60 + minute;
    return nowMinutes >= target && nowMinutes < target + 20;
  }

  /*
   * Pick what the next piece should be about.
   *
   * Source order matters: an unused trend_scout topic is better than a fresh
   * one, because it already carries why_now and source_refs. If nothing is
   * available and no AI key is configured, no content is invented — a title is
   * never made up in code.
   */
  async #createForSlot(policy, slot) {
    const today = this.#tehranDayKey();
    const createdToday = await this.#first(
      'SELECT COUNT(*) AS n FROM content_items WHERE created_at>=?',
      [new Date(this.clock() - DAY).toISOString()]
    );
    if (Number(createdToday?.n || 0) >= policy.max_new_per_day) return null;

    const platformCsv = policy.platforms.join(',');
    const topic = await this.#first(
      "SELECT * FROM topic_candidates WHERE content_id IS NULL AND status='NEW' ORDER BY created_at DESC LIMIT 1"
    );
    let title = '';
    let pillar = '';
    let topicId = null;
    let sourceRefs = [];
    if (topic) {
      title = String(topic.title || '').trim();
      pillar = String(topic.pillar || '').trim();
      topicId = topic.id;
      try { sourceRefs = JSON.parse(topic.source_refs_json || '[]'); } catch { sourceRefs = []; }
    }
    if (!title) return null;

    /* Ask worker.js for the content the same way the UI does. Nothing is faked. */
    const { response, payload } = await this.#callWorker('/api/content', {
      body: {
        title,
        pillar: pillar || undefined,
        topic_id: topicId,
        source_refs: sourceRefs,
        target_platforms: policy.platforms
      }
    });
    if (!response.ok) {
      this.logger.warn?.(`[autopilot] content creation rejected: ${payload?.error?.message || response.status}`);
      return null;
    }
    const contentId = payload?.data?.id;
    if (!contentId) return null;
    if (topicId) {
      await this.#run("UPDATE topic_candidates SET status='IN_PIPELINE', content_id=? WHERE id=?", [contentId, topicId]);
    }
    await this.#audit('autopilot_content_created', 'content', contentId, { slot, platforms: platformCsv, topic_id: topicId });
    return contentId;
  }

  /*
   * Turn poster_designer / infographic_designer output into real JPEGs.
   *
   * The agents emit SVG. Instead of asking the owner to click "download JPEG",
   * the runtime renders the same SVG with an image model and stores the bytes. If
   * no image provider is configured this is a no-op: the manual path still works
   * exactly as before.
   */
  async #producePosters(policy) {
    if (!this.imageProvider?.configured || !this.mediaStore?.fetchable) return 0;
    const rows = await this.#all(
      "SELECT * FROM content_items WHERE state='MEDIA_PENDING' AND (media_links_json IS NULL OR media_links_json='[]') ORDER BY updated_at ASC LIMIT 2"
    );
    let produced = 0;
    /* A failed generation must not be retried on every tick: it costs money and
     * hides the real problem. Six hours of quiet after a failure is enough. */
    const recentFailure = await this.#first(
      "SELECT id FROM audit_logs WHERE action='autopilot_poster_failed' AND created_at>=? LIMIT 1",
      [new Date(this.clock() - 6 * 60 * MINUTE).toISOString()]
    );
    if (recentFailure) return 0;
    for (const content of rows) {
      try {
        produced += await this.#posterFor(content, policy);
      } catch (error) {
        await this.#audit('autopilot_poster_failed', 'content', content.id, { message: String(error.message).slice(0, 400) });
        this.logger.warn?.(`[autopilot] poster failed for ${content.id}: ${error.message}`);
        await this.#escalate(content, `تولید پوستر ناموفق بود: ${String(error.message).slice(0, 200)}`);
        if (policy.notify) {
          await this.#notifyOnce(
            'autopilot_poster_failed_notified', 'content', content.id,
            `تولید پوستر ناموفق بود\n${String(content.title || '').slice(0, 120)}\n${String(error.message).slice(0, 200)}\n\nتا ۶ ساعت تلاش دوباره انجام نمی‌شود.`
          );
        }
        break;
      }
    }
    return produced;
  }

  /*
   * Guarantee JPEG output. If the conversion is impossible the raw bytes are
   * kept and the owner is told, rather than silently attaching a PNG that the
   * validator will reject later.
   */
  #toJpeg(buffer, mime, content) {
    if (isJpeg(buffer)) return { buffer, ext: 'jpg' };
    const ext = extensionForMime(mime);
    if (ext !== 'png') return { buffer, ext };
    try {
      const jpeg = pngToJpeg(buffer, { quality: this.config?.image?.jpegQuality || 90 });
      this.logger.log?.(`[autopilot] converted PNG→JPEG (${buffer.length} → ${jpeg.length} bytes) for ${content.id}`);
      return { buffer: jpeg, ext: 'jpg' };
    } catch (error) {
      this.logger.warn?.(`[autopilot] PNG→JPEG conversion failed for ${content.id}: ${error.code || ''} ${error.message}`);
      return { buffer, ext: 'png' };
    }
  }

  async #posterFor(content, policy) {
    const production = (() => { try { return JSON.parse(content.production_json || '{}'); } catch { return {}; } })();
    const brand = (await this.#first("SELECT * FROM brand_kit WHERE id='default'")) || {};
    const format = content.selected_format || 'post';
    const slides = [];

    if (format === 'carousel') {
      const info = production.infographic_designer || {};
      for (const [index, slide] of (info.slides || []).entries()) {
        slides.push({
          index,
          textBlocks: [{ role: 'headline', text: slide.title || '' }, ...(slide.text_blocks || [])],
          brief: info.alt_text || '',
          layout: slide.layout || '',
          svg: slide.svg || ''
        });
      }
    } else {
      const poster = production.poster_designer || {};
      slides.push({
        index: 0,
        textBlocks: Array.isArray(poster.text_blocks) && poster.text_blocks.length
          ? poster.text_blocks
          : [{ role: 'headline', text: content.title || '' }, { role: 'body', text: poster.layout_description || '' }],
        brief: poster.brief || '',
        layout: poster.layout_description || '',
        svg: poster.svg || ''
      });
    }
    const usable = slides.filter(s => (s.textBlocks || []).some(b => String(b?.text || '').trim()));
    if (!usable.length) return 0;

    const media = [];
    for (const slide of usable.slice(0, 10)) {
      const prompt = buildPosterPrompt({
        brand,
        format,
        textBlocks: slide.textBlocks,
        layout: slide.layout,
        brief: slide.brief,
        index: slide.index,
        total: usable.length
      });
      const { buffer, mime, model } = await this.imageProvider.generate(prompt, {
        aspectRatio: format === 'story' || format === 'reel' ? '9:16' : '4:5'
      });
      /* Publish specs only allow JPEG (platform_specs.image_specs), and the model
       * returns PNG, so convert here instead of failing at gate 3. */
      const converted = this.#toJpeg(buffer, mime, content);
      const saved = this.mediaStore.save(converted.buffer, {
        ext: converted.ext,
        slug: `poster-${String(content.id).slice(-8)}`,
        meta: { width: 1080, height: format === 'story' ? 1920 : 1350, generated_by: model, slide: slide.index + 1 }
      });
      media.push({
        url: saved.url,
        kind: 'image',
        format: saved.format,
        mime: saved.mime,
        size_bytes: saved.bytes,
        width: saved.width,
        height: saved.height,
        source: 'autopilot:image-model',
        model,
        slide: slide.index + 1
      });
      if (usable.length > 1) await delay(400, null, { ref: false });
    }
    if (!media.length) return 0;

    /* Let worker.js own the state transition, exactly as the media form does. */
    const { response, payload } = await this.#callWorker(`/api/content/${encodeURIComponent(content.id)}/media`, {
      body: { media_links: media }
    });
    if (!response.ok) {
      throw new Error(payload?.error?.message || `media attach failed with HTTP ${response.status}`);
    }
    await this.#audit('autopilot_media_attached', 'content', content.id, { count: media.length, models: [...new Set(media.map(m => m.model))] });
    return media.length;
  }

  /*
   * Walk every open gate and approve the ones that pass the policy check.
   * Ordering is oldest first so the pipeline drains in the order the owner would
   * have seen the cards.
   */
  /*
   * Convert a gate that the policy will not approve into a question the owner can
   * answer from the phone. This is what makes the bot a control surface: without
   * it, the owner sees "waiting for you" and then has to open the panel on a
   * laptop to actually do something.
   */
  async #askOwner(row, reason) {
    try {
      const seen = await this.#first(
        'SELECT id FROM audit_logs WHERE action=? AND entity_id=? LIMIT 1',
        ['autopilot_gate_asked', row.gate_id]
      );
      if (seen) return false;
      const { response } = await this.#callWorker(`/api/ops/gate-request/${encodeURIComponent(row.gate_id)}`, {
        body: { reason: String(reason).slice(0, 200) }
      });
      if (response.ok) {
        await this.#audit('autopilot_gate_asked', 'content', row.gate_id, { gate: Number(row.gate_number) });
        return true;
      }
      return false;
    } catch (error) {
      this.logger.warn?.(`[autopilot] gate question failed: ${error.message}`);
      return false;
    }
  }

  async #approvePendingGates(policy) {
    let approved = 0;
    let escalated = 0;
    const rows = await this.#all(
      "SELECT g.id AS gate_id,g.gate_number,g.version,c.* FROM gate_decisions g JOIN content_items c ON c.id=g.content_id WHERE g.status='PENDING' ORDER BY g.created_at ASC LIMIT 5"
    );
    for (const row of rows) {
      const verdict = gatePolicy({
        gateNumber: Number(row.gate_number),
        content: row,
        policy,
        production: (() => { try { return JSON.parse(row.production_json || '{}'); } catch { return {}; } })()
      });
      if (!verdict.allow) {
        await this.#markEscalated(row.gate_id, verdict.reason);
        escalated += 1;
        if (policy.notify) {
          /* One message with the actual buttons, so approving needs no laptop.
             Falls back to a plain notice only if the question could not be sent. */
          const asked = await this.#askOwner(row, verdict.reason);
          if (!asked) {
            const label = { 1: 'ایده', 2: 'سناریو', 3: 'بسته نهایی' }[Number(row.gate_number)] || 'گیت';
            await this.#notifyOnce(
              'autopilot_escalated_notified', 'content', `${row.id}:${row.gate_number}`,
              `گیت ${row.gate_number} (${label}) منتظر شماست\n${String(row.title || '').slice(0, 120)}\nدلیل توقف: ${String(verdict.reason).slice(0, 200)}\n\nدر ربات: /queue`
            );
          }
        }
        continue;
      }
      const { response, payload } = await this.#callWorker(`/api/content/${encodeURIComponent(row.id)}/gates/${row.gate_number}`, {
        body: {
          decision: 'APPROVE',
          note: `تأیید خودکار اتوپایلوت: ${verdict.reason}`,
          selected_idea_index: verdict.selected_idea_index
        }
      });
      if (!response.ok) {
        await this.#markEscalated(row.gate_id, `APPROVE_FAILED: ${String(payload?.error?.code || response.status)}`);
        escalated += 1;
        continue;
      }
      approved += 1;
      await this.#audit('autopilot_gate_approved', 'content', row.id, {
        gate: Number(row.gate_number),
        version: Number(row.version),
        reason: verdict.reason
      });
    }
    return { approved, escalated };
  }

  /*
   * Mark a gate as needing the owner, without changing its decision status.
   * The marker is written once: the loop runs every minute, and appending the
   * same line on every tick would bury the owner's own note under noise.
   */
  async #markEscalated(gateId, reason) {
    const marker = `[autopilot:${String(reason).slice(0, 80)}]`;
    try {
      const row = await this.#first('SELECT note FROM gate_decisions WHERE id=?', [gateId]);
      if (row?.note?.includes(marker)) return;
      await this.#run(
        "UPDATE gate_decisions SET note=COALESCE(note,'')||? WHERE id=? AND status='PENDING'",
        [`${marker} `, gateId]
      );
    } catch { /* the note is a convenience, not a requirement */ }
  }

  async #escalate(content, message) {
    try {
      await this.#run(
        "UPDATE content_items SET owner_notes=?, updated_at=? WHERE id=?",
        [String(message).slice(0, 2000), new Date(this.clock()).toISOString(), content.id]
      );
    } catch { /* best effort */ }
  }

  start() {
    this.running = true;
    this.stopped = false;
    return this;
  }

  stop() {
    this.stopped = true;
    this.running = false;
  }
}
