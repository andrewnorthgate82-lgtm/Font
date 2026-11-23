/*
 * Telegram-first control surface for the self-hosted runtime.
 *
 * Why this exists: on a personal machine the studio is only alive while the
 * laptop is awake. The owner should not have to keep a browser tab open to know
 * that. This module turns the existing bot into a small control panel:
 *
 *   /wake     → "start the laptop" request, acknowledged and queued
 *   /start    → what this bot can do
 *   /ping     → is the studio process alive, and how fresh is its heartbeat
 *   /pause    → stop all publishing until /resume
 *   /resume   → allow publishing again
 *   /now      → run one autopilot tick immediately
 *
 * Two safety properties matter and are tested:
 *
 * 1. This module never publishes anything. It only reports state and changes a
 *    pause flag. No command here can send a post to a channel.
 * 2. Commands are only obeyed from TG_OWNER_CHAT_ID. The worker already refuses
 *    other chats, and this module repeats the check before it acts, so a wrong
 *    token or a forwarded message can never drive the studio.
 *
 * Nothing here talks to Telegram itself: it returns the text to send and the
 * caller (worker.js, via its existing tgRequest) delivers it. That keeps the
 * bot token in one place and makes the module testable without network.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export const COMMANDS = ['/start', '/help', '/ping', '/wake', '/pause', '/resume', '/now', '/status'];

/* A heartbeat older than this means the process is probably gone or wedged. */
export const HEARTBEAT_STALE_MS = 5 * 60 * 1000;

const PAUSE_SETTING = 'publish_paused';
const FORCE_TICK_SETTING = 'autopilot_force_tick';
/* A forced tick is a request, not a command: if the studio was off when the
   owner pressed /now, replaying it hours later is wrong. Five minutes is long
   enough for the next loop and short enough to be irrelevant after a reboot. */
export const FORCE_TICK_TTL_MS = 5 * 60 * 1000;

export function heartbeatFile(dir) {
  return path.join(dir, 'nasra-heartbeat.json');
}

/*
 * The launcher (scripts/start-nasra.mjs) writes this file every 30s while the
 * server is alive. A wake helper reads it to decide whether a laptop is already
 * running the studio, which is what makes "/wake" answer honestly instead of
 * always claiming success.
 */
export function writeHeartbeat(dir, info = {}, now = Date.now()) {
  const file = heartbeatFile(dir);
  const payload = {
    at: new Date(now).toISOString(),
    pid: process.pid,
    port: info.port ?? null,
    url: info.url || '',
    version: info.version || ''
  };
  try {
    fs.writeFileSync(file, JSON.stringify(payload));
    return payload;
  } catch {
    return null;
  }
}

export function readHeartbeat(dir, now = Date.now(), staleMs = HEARTBEAT_STALE_MS) {
  const file = heartbeatFile(dir);
  try {
    const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
    const at = Date.parse(parsed.at || '');
    if (!Number.isFinite(at)) return { present: false, stale: true, path: file };
    const ageMs = Math.max(0, now - at);
    return { present: true, stale: ageMs > staleMs, age_ms: ageMs, ...parsed, path: file };
  } catch {
    return { present: false, stale: true, path: file };
  }
}

/* Public tracking link: the bot appends its own token, so this never contains one. */
export function wakeLink(code = '') {
  return `tg://resolve?domain=BotFather&start=${encodeURIComponent(code || 'wake')}`;
}

/*
 * Stable per-day wake code. The helper script computes the same value, so the
 * code can be checked offline and no state has to be stored anywhere.
 */
export function wakeCode(secret, now = Date.now()) {
  if (!secret) return '';
  const day = new Date(now).toISOString().slice(0, 10);
  return crypto.createHash('sha256').update(`${secret}:${day}`).digest('hex').slice(0, 10);
}

export function verifyWakeCode(secret, code, now = Date.now()) {
  const expected = wakeCode(secret, now);
  if (!expected || !code) return false;
  const a = Buffer.from(String(code));
  const b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

/*
 * Decide what to do with one owner message.
 *
 * Inputs are plain data so this can be unit-tested with no Telegram, no network
 * and no database. `paused` and `heartbeat` are supplied by the caller.
 *
 * Returns { command, action, reply, parse_mode? } where action is one of:
 *   'none' | 'pause' | 'resume' | 'tick' | 'wake'
 */
export function handleCommand({
  text = '',
  ownerChatId = '',
  chatId = '',
  heartbeat = { present: false, stale: true },
  paused = false,
  autopilot = null,
  now = Date.now()
} = {}) {
  const raw = String(text || '').trim();
  const command = raw.split(/\s+/)[0].toLowerCase();
  const base = command.split('@')[0];

  /* Fail closed: an unknown chat is never answered with studio state. */
  if (!ownerChatId || String(chatId) !== String(ownerChatId)) {
    return { command: base, action: 'none', reply: '', ignored: 'not_owner' };
  }

  if (base === '/start' || base === '/help') {
    const lines = [
      'نسرا اصفهان — کنترل از تلگرام',
      '',
      '/ping      آیا استودیو روشن است؟',
      '/status    وضعیت صف، انتشار و اتوپایلوت',
      '/today     انتشارهای امروز',
      '/queue     گیت‌های در انتظار تأیید',
      '/wake      درخواست روشن کردن لپ‌تاپ',
      '/now       اجرای فوری یک چرخه اتوپایلوت',
      paused ? '/resume    فعال‌کردن انتشار (فعلاً متوقف است)' : '/pause     توقف انتشار',
      '',
      'این ربات فقط وضعیت را نشان می‌دهد و انتشار را متوقف/آزاد می‌کند.',
      'هیچ دستوری از این‌جا پستی به کانال نمی‌فرستد.'
    ];
    return { command: base, action: 'none', reply: lines.join('\n') };
  }

  if (base === '/ping') {
    if (!heartbeat?.present) {
      return {
        command: base,
        action: 'none',
        reply: 'استودیو خاموش است.\nهیچ نشانه‌ای از اجرا پیدا نشد.\n\nبرای روشن کردن: /wake'
      };
    }
    const age = Math.round((heartbeat.age_ms ?? 0) / 1000);
    if (heartbeat.stale) {
      return {
        command: base,
        action: 'none',
        reply: `استودیو پاسخ نمی‌دهد.\nآخرین نشانه: ${age} ثانیه پیش\n\nلپ‌تاپ احتمالاً خواب رفته یا برنامه بسته شده است.\nبرای روشن کردن: /wake`
      };
    }
    return {
      command: base,
      action: 'none',
      reply: `استودیو روشن است.\nآخرین ضربان: ${age} ثانیه پیش\nپورت: ${heartbeat.port ?? '?'}\nانتشار: ${paused ? 'متوقف (با /resume فعال کنید)' : 'آزاد'}`
    };
  }

  if (base === '/status') {
    const lines = ['وضعیت نسرا اصفهان', ''];
    lines.push(`استودیو: ${heartbeat?.present && !heartbeat.stale ? 'روشن' : 'خاموش یا بی‌پاسخ'}`);
    lines.push(`انتشار: ${paused ? 'متوقف' : 'آزاد'}`);
    if (autopilot) {
      lines.push(`اتوپایلوت: ${autopilot.policy || 'off'}${autopilot.running ? ' (در حال اجرا)' : ''}`);
      if (Array.isArray(autopilot.platforms) && autopilot.platforms.length) lines.push(`مقصد: ${autopilot.platforms.join('، ')}`);
      if (autopilot.last_tick_at) lines.push(`آخرین چرخه: ${autopilot.last_tick_at}`);
      if (autopilot.last_error) lines.push(`آخرین خطا: ${String(autopilot.last_error.message || autopilot.last_error).slice(0, 160)}`);
    }
    return { command: base, action: 'none', reply: lines.join('\n') };
  }

  if (base === '/pause') {
    if (paused) return { command: base, action: 'none', reply: 'انتشار از قبل متوقف است. برای فعال‌کردن: /resume' };
    return { command: base, action: 'pause', reply: 'انتشار متوقف شد.\nهیچ پستی زمان‌بندی یا ارسال نمی‌شود تا /resume بفرستید.\nمحتوای در حال ساخت متوقف نمی‌شود.' };
  }

  if (base === '/resume') {
    if (!paused) return { command: base, action: 'none', reply: 'انتشار از قبل آزاد است.' };
    return { command: base, action: 'resume', reply: 'انتشار آزاد شد.\nکارهای سررسیده در چرخه بعدی زمان‌بند ارسال می‌شوند.' };
  }

  if (base === '/now') {
    if (!heartbeat?.present || heartbeat.stale) {
      return { command: base, action: 'none', reply: 'استودیو خاموش است، چرخه‌ای اجرا نشد.\nبرای روشن کردن: /wake' };
    }
    return {
      command: base,
      action: 'tick',
      reply: 'درخواست ثبت شد.\nچرخه بعدی اتوپایلوت (حداکثر ۶۰ ثانیه دیگر) همین حالا اجرا می‌شود و نتیجه‌اش را می‌فرستد.'
    };
  }

  if (base === '/wake') {
    /* The bot cannot power on a machine. Saying so is the whole point: a fake
       "done" here would leave the owner waiting for content that never comes. */
    const lines = [
      'درخواست روشن کردن ثبت شد.',
      '',
      'تلگرام نمی‌تواند لپ‌تاپ را روشن کند؛ هیچ رباتی نمی‌تواند.',
      'این پیام فقط یک درخواست است. برای روشن شدن واقعی، لپ‌تاپ باید:',
      '• در برق باشد،',
      '• در تنظیمات بایوس/ویندوز «روشن‌شدن با برق» فعال باشد،',
      '• و یک دستگاه دیگر (مثل یک رله هوشمند) برقش را وصل کند.',
      '',
      'اگر لپ‌تاپ روشن است ولی استودیو خاموش مانده، دکمه زیر را در همان لپ‌تاپ بزنید.',
      '',
      'راهنمای کامل: LAPTOP.fa.md — بخش «روشن کردن از راه دور»'
    ];
    return { command: base, action: 'wake', reply: lines.join('\n') };
  }

  return {
    command: base,
    action: 'none',
    reply: 'دستور شناخته نشد.\nبرای دیدن فهرست: /help'
  };
}

/* Read the pause flag the same way the scheduler does. */
export async function readPaused(db) {
  const row = await db.prepare('SELECT value_json FROM studio_settings WHERE key=?').bind(PAUSE_SETTING).first();
  if (!row?.value_json) return false;
  try {
    const parsed = JSON.parse(row.value_json);
    return Boolean(parsed?.paused);
  } catch {
    return false;
  }
}

export async function writePaused(db, paused, actor = 'owner_telegram') {
  const now = new Date().toISOString();
  const value = JSON.stringify({ paused: Boolean(paused), at: now, by: actor });
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at')
    .bind(PAUSE_SETTING, value, now).run();
  return Boolean(paused);
}

/* Ask the autopilot loop to run one extra cycle as soon as it next wakes up. */
export async function requestTick(db, actor = 'owner_telegram') {
  const now = new Date().toISOString();
  const value = JSON.stringify({ at: now, by: actor });
  await db.prepare('INSERT INTO studio_settings (key,value_json,updated_at) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at')
    .bind(FORCE_TICK_SETTING, value, now).run();
  return true;
}

/*
 * Read and consume a pending request. Returns false when there is none, and also
 * when it is too old — a stale request must never run behind the owner's back.
 */
export async function consumeTickRequest(db, now = Date.now(), ttlMs = FORCE_TICK_TTL_MS) {
  const row = await db.prepare('SELECT value_json FROM studio_settings WHERE key=?').bind(FORCE_TICK_SETTING).first();
  if (!row?.value_json) return { requested: false };
  await db.prepare('DELETE FROM studio_settings WHERE key=?').bind(FORCE_TICK_SETTING).run().catch(() => {});
  let parsed = {};
  try { parsed = JSON.parse(row.value_json); } catch { /* treat as stale */ }
  const at = Date.parse(parsed.at || '');
  if (!Number.isFinite(at)) return { requested: false, reason: 'unreadable' };
  if (now - at > ttlMs) return { requested: false, reason: 'expired' };
  return { requested: true, at: parsed.at, by: parsed.by || '' };
}
