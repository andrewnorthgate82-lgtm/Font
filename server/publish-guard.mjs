/*
 * Publish guard for the self-hosted (SillyDev) runtime.
 *
 * The Worker code is shared with Cloudflare, so the extra owner confirmation is
 * enforced by wrapping the outgoing fetch instead of editing worker.js:
 *
 * - Only the publish endpoints of the three supported platforms are intercepted
 *   (Instagram Graph publishing, Telegram channel sends, Rubika channel sends).
 * - Everything else the app needs (Telegram owner notifications, Rubika
 *   getMe/requestSendFile, media downloads, AI providers, RSS) passes through
 *   untouched.
 * - Reads are never blocked. Only the "this actually becomes a public post"
 *   request is gated.
 *
 * Modes:
 *   dry_run (default) - the request never leaves the process. The worker gets a
 *                       successful stub response and the publish job ends up in
 *                       the normal MANUAL_READY / retry path, so nothing is
 *                       published, but the flow stays observable.
 *   live              - the request is allowed only when the owner has approved
 *                       it with POST /api/ops/approvals (single use, short TTL).
 */
import crypto from 'node:crypto';

const IG_PUBLISH_PATH = /^\/v\d+(?:\.\d+)?\/[^/]+\/(media_publish|media|messages|[^/]+\/replies)\/?$/;
const IG_HOSTS = new Set(['graph.instagram.com', 'graph.facebook.com', 'rupload.facebook.com']);
const TG_PUBLISH_METHODS = new Set([
  'sendmessage', 'sendphoto', 'sendvideo', 'sendanimation', 'sendaudio', 'senddocument',
  'sendmediagroup', 'sendlocation', 'sendvenue', 'sendcontact', 'sendpoll', 'senddice',
  'sendchatinvite', 'sendsticker', 'sendvoice', 'sendvideonote', 'sendpaidmedia', 'copyMessage'.toLowerCase(),
  'copyMessages'.toLowerCase(), 'forwardMessage'.toLowerCase(), 'forwardMessages'.toLowerCase(),
  'editMessageText'.toLowerCase(), 'editMessageMedia'.toLowerCase()
]);
const RUBIKA_PUBLISH_METHODS = new Set(['sendmessage', 'sendpoll', 'sendfile', 'sendlocation', 'sendcontact', 'sendvideo', 'sendphoto', 'sendaudio', 'senddocument', 'forwardmessage', 'editmessagetext']);

function safeHost(url) {
  try { return new URL(url).hostname.toLowerCase(); } catch { return ''; }
}

function pathOf(url) {
  try { return new URL(url).pathname; } catch { return ''; }
}

function telegramMethod(url) {
  const path = pathOf(url);
  const match = path.match(/\/bot[^/]+\/([A-Za-z]+)$/);
  return match ? match[1].toLowerCase() : '';
}

function rubikaMethod(url) {
  const path = pathOf(url);
  const match = path.match(/\/v\d+(?:\.\d+)?\/[^/]+\/([A-Za-z]+)$/);
  return match ? match[1].toLowerCase() : '';
}

function jsonBody(init) {
  const body = init?.body;
  if (typeof body !== 'string' || !body) return null;
  try { return JSON.parse(body); } catch { return null; }
}

function chatIdOf(body) {
  if (!body || typeof body !== 'object') return '';
  const value = body.chat_id ?? body.chatId ?? body.peer_id ?? '';
  return String(value).trim();
}

/*
 * Classify one outgoing request.
 * Returns null when the request is not a publish action.
 */
export function classifyPublishRequest(url, init = {}) {
  const host = safeHost(url);
  const method = String(init?.method || 'GET').toUpperCase();
  if (method === 'GET' || method === 'HEAD') return null;

  if (IG_HOSTS.has(host)) {
    const path = pathOf(url);
    if (IG_PUBLISH_PATH.test(path)) {
      const action = path.split('/').filter(Boolean).pop();
      return { platform: 'instagram', action, host, request: url };
    }
    return null;
  }
  if (host === 'api.telegram.org') {
    const action = telegramMethod(url);
    if (!TG_PUBLISH_METHODS.has(action)) return null;
    const chatId = chatIdOf(jsonBody(init));
    return { platform: 'telegram', action, host, chatId, request: url };
  }
  if (host === 'botapi.rubika.ir') {
    const action = rubikaMethod(url);
    if (!RUBIKA_PUBLISH_METHODS.has(action)) return null;
    const chatId = chatIdOf(jsonBody(init));
    return { platform: 'rubika', action, host, chatId, request: url };
  }
  return null;
}

function redactUrl(url) {
  // Bot tokens live in the path. Keep only the host and the method name.
  try {
    const parsed = new URL(url);
    const tail = parsed.pathname.split('/').filter(Boolean).pop() || '';
    return `${parsed.protocol}//${parsed.host}/…/${tail}`;
  } catch {
    return 'invalid-url';
  }
}

function uid() {
  return `approval_${crypto.randomBytes(8).toString('hex')}`;
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export class PublishGuard {
  constructor({ mode = 'dry_run', allowlist = [], ownerChatIds = [], approvalTtlMs = 10 * 60 * 1000, clock = Date.now } = {}) {
    this.mode = mode === 'live' ? 'live' : 'dry_run';
    this.allowlist = allowlist.map(String).map(v => v.trim()).filter(Boolean);
    // Owner DMs are notifications, not publications: a sendMessage to the
    // owner's own chat never becomes a public post, so it is not gated.
    this.ownerChatIds = ownerChatIds.map(String).map(v => v.trim()).filter(Boolean);
    this.approvalTtlMs = Math.max(30 * 1000, Number(approvalTtlMs) || 10 * 60 * 1000);
    this.clock = clock;
    this.approvals = new Map();
    this.log = [];
    this.stats = { allowed: 0, blocked: 0, dry_run: 0 };
  }

  /* Requests are blocked unless their destination chat/channel is allowlisted. */
  destinationAllowed(action) {
    if (!this.allowlist.length) return false;
    if (!action.chatId) return true; // Instagram has no chat id; approval + mode govern it.
    return this.allowlist.includes(action.chatId);
  }

  fingerprint(action) {
    return sha256([action.platform, action.action, action.chatId || '', redactUrl(action.request)].join('|'));
  }

  /*
   * A separate, explicit owner confirmation. It is scoped (platform / action /
   * destination), single-use and short lived. In-memory on purpose: a restart
   * invalidates every outstanding confirmation.
   */
  issueApproval({ note = '', ttlMs, platform = null, action = null, destination = null, ttlSeconds = null } = {}) {
    const now = this.clock();
    const token = crypto.randomBytes(18).toString('base64url');
    const requestedTtl = Number(ttlSeconds) ? Number(ttlSeconds) * 1000 : Number(ttlMs);
    const record = {
      id: uid(),
      token_hash: sha256(token),
      note: String(note || '').slice(0, 300),
      platform: platform ? String(platform) : null,
      action: action ? String(action).toLowerCase() : null,
      destination: destination ? String(destination) : null,
      created_at: new Date(now).toISOString(),
      expires_at: new Date(now + Math.min(this.approvalTtlMs, Math.max(30_000, requestedTtl || this.approvalTtlMs))).toISOString()
    };
    this.approvals.set(record.token_hash, record);
    this.#prune();
    return { ...record, token_hash: undefined, token };
  }

  listApprovals() {
    this.#prune();
    return [...this.approvals.values()].map(({ token_hash, ...rest }) => rest);
  }

  revokeApprovals() {
    const count = this.approvals.size;
    this.approvals.clear();
    return count;
  }

  #prune() {
    const now = this.clock();
    for (const [hash, record] of this.approvals) {
      if (new Date(record.expires_at).getTime() <= now) this.approvals.delete(hash);
    }
  }

  /* Find the oldest approval that matches this publish action, then burn it. */
  #consume(token, action) {
    this.#prune();
    let matchedHash = null;
    let matched = null;
    if (token) {
      const hash = sha256(String(token));
      const record = this.approvals.get(hash);
      if (!record) return { ok: false, reason: 'APPROVAL_INVALID_OR_EXPIRED' };
      if (record.platform && record.platform !== action.platform) return { ok: false, reason: 'APPROVAL_PLATFORM_MISMATCH' };
      if (record.action && record.action !== action.action) return { ok: false, reason: 'APPROVAL_ACTION_MISMATCH' };
      matchedHash = hash;
      matched = record;
    } else {
      for (const [hash, record] of this.approvals) {
        if (record.platform && record.platform !== action.platform) continue;
        if (record.action && record.action !== action.action) continue;
        if (record.destination && action.chatId && record.destination !== action.chatId) continue;
        matchedHash = hash;
        matched = record;
        break;
      }
    }
    if (!matched) return { ok: false, reason: 'APPROVAL_REQUIRED' };
    this.approvals.delete(matchedHash);
    return { ok: true, record: matched };
  }

  /*
   * Decide about one outgoing publish request.
   * Returns { decision: 'pass' | 'block', ... }.
   */
  decide(action, { approvalToken = '' } = {}) {
    if (action.chatId && this.ownerChatIds.includes(action.chatId)) {
      this.record({ ...action, decision: 'pass', reason: 'OWNER_DM_NOT_A_PUBLISH' });
      return { decision: 'pass', status: 'owner_dm', reason: 'OWNER_DM_NOT_A_PUBLISH' };
    }
    if (this.mode === 'dry_run') {
      this.record({ ...action, decision: 'dry_run', reason: 'GUARD_DRY_RUN' });
      this.stats.dry_run += 1;
      return { decision: 'block', status: 'dry_run', reason: 'GUARD_DRY_RUN' };
    }
    if (!this.destinationAllowed(action)) {
      this.record({ ...action, decision: 'block', reason: 'DESTINATION_NOT_ALLOWLISTED' });
      this.stats.blocked += 1;
      return { decision: 'block', status: 'blocked', reason: 'DESTINATION_NOT_ALLOWLISTED' };
    }
    const approval = this.#consume(approvalToken, action);
    if (!approval.ok) {
      this.record({ ...action, decision: 'block', reason: approval.reason });
      this.stats.blocked += 1;
      return { decision: 'block', status: 'blocked', reason: approval.reason };
    }
    this.record({ ...action, decision: 'pass', reason: 'OWNER_APPROVED', approval_note: approval.record.note });
    this.stats.allowed += 1;
    return { decision: 'pass', status: 'allowed', reason: 'OWNER_APPROVED' };
  }

  record(entry) {
    this.log.push({ at: new Date(this.clock()).toISOString(), ...entry, request: redactUrl(entry.request || '') });
    if (this.log.length > 200) this.log.splice(0, this.log.length - 200);
  }

  recent(limit = 50) {
    return this.log.slice(-Math.max(1, Math.min(200, Number(limit) || 50))).reverse();
  }
}

/* Stub response handed to the worker instead of a real external publish. */
export function dryRunResponse(action) {
  const payload = {
    ok: false,
    error_code: 403,
    description: `NASRA_GUARD_DRY_RUN: ${action.platform} ${action.action} was not sent`,
    result: {
      message_id: 0,
      id: `guard-dry-run-${Date.now()}`,
      dry_run: true,
      platform: action.platform,
      action: action.action
    }
  };
  return new Response(JSON.stringify(payload), {
    status: 403,
    headers: { 'Content-Type': 'application/json', 'X-Nasra-Guard': 'dry_run' }
  });
}
