/*
 * Zero-dependency local preview for Nasra Esfahan Media Studio.
 * It adapts node:sqlite to the small D1 interface used by worker.js and serves
 * public/ without a build step. Production uses Cloudflare D1/Pages instead.
 * Usage: node dev-preview.mjs  (binds 0.0.0.0:8787)
 * Preview-only login defaults: OWNER_PASSWORD=nasra-preview, SESSION_SECRET is
 * generated for the process. Set real values through the environment locally.
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import worker from './worker.js';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC = path.join(ROOT, 'public');
const DATA = path.join(ROOT, '.preview');
fs.mkdirSync(DATA, { recursive: true });
const sqlite = new DatabaseSync(path.join(DATA, 'nasra.sqlite'));
sqlite.exec(fs.readFileSync(path.join(ROOT, 'schema.sql'), 'utf8'));

class D1Statement {
  constructor(sql) { this.sql = sql; this.params = []; }
  bind(...params) { this.params = params.map(v => v === undefined ? null : v); return this; }
  async run() {
    const result = sqlite.prepare(this.sql).run(...this.params);
    return { meta: { changes: Number(result.changes), last_rowid: Number(result.lastInsertRowid) } };
  }
  async first() { return sqlite.prepare(this.sql).get(...this.params) || null; }
  async all() { return { results: sqlite.prepare(this.sql).all(...this.params) }; }
}
const db = { prepare(sql) { return new D1Statement(sql); } };

// Explicitly opt-out with PREVIEW_MOCK_AI=0. This mock exists only in the local
// browser preview, so a reviewer can exercise the pipeline without an AI key.
function mockAgentOutput(systemPrompt, userText) {
  const id = systemPrompt.match(/##\s+([a-z_]+)/)?.[1] || '';
  let input = {};
  try { input = JSON.parse(userText); } catch {}
  const format = input.content?.selected_format || 'post';
  const common = { risk_flags: [], needs_info: [] };
  if (id === 'trend_scout') return { topics: [{ title: 'چک‌لیست سه‌مرحله‌ای برای بررسی یک پیام مشکوک', why_now: 'ورودی آزمایشی پیش‌نمایش؛ پیش از انتشار باید منبع واقعی اضافه شود.', pillar: 'شایعه‌شکنی و راستی‌آزمایی', source_refs: [], sensitivity: 'کم', urgency: 'این‌هفته' }], ...common };
  if (id === 'ideation') return { ideas: [
    { format: 'post', hook: 'قبل از بازنشر، این سه نشانه را ببین', angle: 'یک چک‌لیست کوتاه و قابل ذخیره', key_message: 'مکث و بررسی منبع، تصمیم رسانه‌ای بهتری می‌سازد.', cta: 'چک‌لیست را برای دوستت بفرست', effort: 'کم', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: چک‌لیست‌های کوتاه ذخیره بیشتری می‌گیرند.', confidence: 'کم' } },
    { format: 'carousel', hook: 'سه سؤال پیش از باور کردن یک خبر', angle: 'اسلایدهای گام‌به‌گام', key_message: 'منبع، تاریخ و تصویر را جدا بررسی کن.', cta: 'ذخیره کن و مرور کن', effort: 'متوسط', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: ساختار مرحله‌ای خوانایی را بیشتر می‌کند.', confidence: 'کم' } },
    { format: 'reel', hook: 'یک ثانیه مکث کن؛ لینک را دقیق بخوان', angle: 'بازنمایی کوتاه بدون ترساندن', key_message: 'عجله، نشانه رایج فیشینگ است.', cta: 'برای خانواده بفرست', effort: 'زیاد', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: مثال ملموس یادآوری را بیشتر می‌کند.', confidence: 'کم' } }
  ], ...common };
  if (id === 'scriptwriter') return {
    format,
    post_body: ['post', 'story'].includes(format) ? 'قبل از بازنشر یک پیام مشکوک، این سه نشانه را بررسی کن:\n۱. منبع مشخص و قابل اعتماد نیست.\n۲. تاریخ یا زمینه پیام روشن نیست.\n۳. از شما می‌خواهد عجله کنید یا رمز و اطلاعات شخصی بدهید.\n\nاگر یکی از این نشانه‌ها را دیدید، مکث کنید و پیام را از مسیر رسمی بررسی کنید.' : '',
    beats: format === 'reel' ? [{ t_start: 0, t_end: 3, voiceover: 'قبل از بازنشر یک ثانیه مکث کن.', on_screen_text: 'یک ثانیه مکث کن', visual: 'نمای نزدیک از صفحه پیام' }, { t_start: 3, t_end: 12, voiceover: 'منبع و تاریخ را بررسی کن.', on_screen_text: 'منبع و تاریخ', visual: 'نمایش چک‌لیست' }] : [],
    slides: format === 'carousel' ? [{ n: 1, headline: 'سه سؤال مهم', body: 'چه کسی منتشر کرده؟ چه زمانی؟ مدرک کجاست؟', visual_note: 'سه کارت با حاشیه امن' }] : [],
    claims_list: [], duration_estimate_sec: format === 'reel' ? 12 : 30, reading_level: 'ساده و عمومی', ...common
  };
  if (id === 'fact_checker') return { claims: [], overall: 'pass', notes: 'در ورودی آزمایشی ادعای قابل راستی‌آزمایی ثبت نشده است.', ...common };
  if (id === 'video_editor') return { shot_list: [{ n: 1, duration_sec: 3, visual: 'نمای صفحه پیام', on_screen_text: 'مکث کن', transition: 'cut' }], cut_plan: { pace: 'آرام و خوانا', beat_points: [0, 3] }, subtitle_srt: '1\n00:00:00,000 --> 00:00:03,000\nمکث کن', cover_frame_idea: 'قاب چک‌لیست با کنتراست بالا', export_spec: { ratio: '9:16', resolution: '1080x1920', fps: 30 }, tool_notes: 'مشخصات را با فایل نهایی تطبیق دهید.', ...common };
  if (id === 'poster_designer' || id === 'infographic_designer') {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 1350"><rect width="1080" height="1350" fill="#0F4C5C"/><text x="980" y="220" text-anchor="end" direction="rtl" fill="#F8FAFC" font-family="Tahoma,Arial" font-size="64">نسرا اصفهان</text><text x="980" y="420" text-anchor="end" direction="rtl" fill="#F8FAFC" font-size="52">مکث، بررسی، بعد بازنشر</text><text x="980" y="1180" text-anchor="end" direction="rtl" fill="#1B998B" font-size="34">سوادرسانه برای همه</text></svg>';
    if (id === 'poster_designer') return { brief: 'پوستر آموزشی ساده با سه رنگ برند', layout_description: 'تیتر بالا، پیام مرکزی، واترمارک پایین', text_blocks: [{ role: 'headline', text: 'مکث، بررسی، بعد بازنشر' }], svg, alt_text: 'پوستر آموزشی نسرا اصفهان درباره بررسی پیش از بازنشر', export_note: 'در مرورگر به JPEG سایز ۱۰۸۰×۱۳۵۰ تبدیل شود.', ...common };
    return { slides: [{ n: 1, title: 'سه گام بررسی', svg, data_points: [] }], alt_text: 'اینفوگرافیک سه گام بررسی محتوا', ...common };
  }
  if (id === 'sound_engineer') return { voiceover_script: 'مکث کن [مکث کوتاه] منبع را بررسی کن.', pacing: 'آرام، با مکث بعد از هر گام', sound_design_cues: [{ t: 0, cue: 'یک افکت کوتاه و ملایم' }], mix_notes: 'صدای گوینده واضح و موسیقی کم‌تر از گفتار', recording_tips: 'در فضای ساکت و با فاصله ثابت از میکروفون ضبط کنید.', ...common };
  if (id === 'music_composer') return { mood: 'آرام و امیدوارکننده', bpm_range: '۸۰ تا ۹۰', structure: [{ section: 'ورود', seconds: 4, instrumentation: 'پد ملایم' }], style_description: 'پس‌زمینه مینیمال بدون احساس اضطراب', prompts_for_external_generators: ['ambient minimal educational background, no vocals'], licensing_note: 'مجوز هر قطعه باید پیش از استفاده بررسی شود؛ این خروجی صدا تولید نمی‌کند.', ...common };
  if (id === 'editorial') return { caption: 'قبل از بازنشر، سه سؤال ساده بپرس\nچه کسی منتشر کرده؟ چه زمانی؟ مدرکش کجاست؟\n\nاین چک‌لیست را ذخیره کن و برای خانواده بفرست.', hashtags: ['#سوادرسانه', '#نسرا_اصفهان'], alt_text: 'چک‌لیست سه‌مرحله‌ای بررسی منبع و تاریخ و مدرک', cta: 'ذخیره و بازنشر آگاهانه', posting_slots: [], checklist_results: [{ item: 'قلاب مستقل', ok: true, note: 'بررسی پیش‌نمایش' }], trial_advice: { hypothesis: 'داده کافی نیست؛ ابتدا آمار جمع شود.', confidence: 'کم' }, experiment: { variable: 'فرمت', hypothesis: 'فرضیه برای آزمایش: کروسل چک‌لیستی ذخیره بیشتری بگیرد.', metric: 'saves' }, ...common };
  if (id === 'tech_advisor') return { checks: [], suggestions: [], unknown_specs: [], ...common };
  if (id === 'community_admin') {
    if (input.task?.mode === 'reply' || input.extra?.inbound_message) return { category: 'سؤال', sentiment: 'خنثی', escalate: false, reason: 'پیش‌نویس پاسخ آموزشی', reply_draft: 'سپاس از پیام شما. لطفاً منبع و زمان دریافت را بررسی کنید؛ اگر نشانه مشکوکی هست، جزئیات را بدون اطلاعات شخصی بفرستید.', do_not_reply: false, ...common };
    return { schedule: [], conflicts: [], ...common };
  }
  return common;
}
const useMock = process.env.PREVIEW_MOCK_AI !== '0';
const env = {
  DB: db,
  OWNER_PASSWORD: process.env.OWNER_PASSWORD || 'nasra-preview',
  SESSION_SECRET: process.env.SESSION_SECRET || 'preview-session-secret-change-me',
  // Preview also issues the same signed session as a Bearer token because an
  // embedding proxy or browser may block cookies. Production does not set this.
  PREVIEW_MODE: '1',
  ALLOWED_ORIGINS: process.env.ALLOWED_ORIGINS || '',
  TG_BOT_TOKEN: process.env.TG_BOT_TOKEN || '', TG_OWNER_CHAT_ID: process.env.TG_OWNER_CHAT_ID || '', TG_SECRET: process.env.TG_SECRET || '',
  AI_PROVIDER: process.env.AI_PROVIDER || 'workers_ai', AI_API_KEY: process.env.AI_API_KEY || '',
  GAS_URL: process.env.GAS_URL || '', GAS_KEY: process.env.GAS_KEY || '',
  IG_APP_SECRET: process.env.IG_APP_SECRET || '', IG_VERIFY_TOKEN: process.env.IG_VERIFY_TOKEN || '', IG_ACCESS_TOKEN: process.env.IG_ACCESS_TOKEN || '', IG_USER_ID: process.env.IG_USER_ID || '', RUBIKA_BOT_TOKEN: process.env.RUBIKA_BOT_TOKEN || '',
  AI: useMock ? { async run(model, args) { const system = args.messages?.[0]?.content || ''; const user = args.messages?.[1]?.content || ''; return { response: JSON.stringify(mockAgentOutput(system, user)), usage: { prompt_tokens: 100, completion_tokens: 120 } }; } } : null
};
const pending = new Set();
const ctx = { waitUntil(promise) { const p = Promise.resolve(promise).catch(error => console.error('[background]', error.message)); pending.add(p); p.finally(() => pending.delete(p)); } };
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.ttf': 'font/ttf', '.woff2': 'font/woff2' };
function safeStaticPath(urlPath) {
  const decoded = decodeURIComponent(urlPath.split('?')[0]);
  const relative = decoded === '/' ? 'index.html' : decoded.replace(/^\/+/, '');
  const absolute = path.resolve(PUBLIC, relative);
  return absolute.startsWith(PUBLIC + path.sep) ? absolute : null;
}
const server = http.createServer(async (req, res) => {
  try {
    const host = req.headers.host || 'localhost:8787';
    const forwardedProto = String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim();
    const protocol = forwardedProto === 'https' ? 'https' : 'http';
    const url = `${protocol}://${host}${req.url || '/'}`;
    if ((req.url || '').startsWith('/api/') || (req.url || '').startsWith('/webhooks/') || req.url === '/healthz') {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const body = Buffer.concat(chunks);
      const headers = new Headers();
      for (const [key, value] of Object.entries(req.headers)) if (value !== undefined) headers.set(key, Array.isArray(value) ? value.join(',') : value);
      const request = new Request(url, { method: req.method, headers, body: ['GET', 'HEAD'].includes(req.method) ? undefined : body });
      const response = await worker.fetch(request, env, ctx);
      res.writeHead(response.status, Object.fromEntries(response.headers.entries()));
      res.end(Buffer.from(await response.arrayBuffer()));
      return;
    }
    const file = safeStaticPath(req.url || '/');
    if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, {
      'Content-Type': mime[path.extname(file)] || 'application/octet-stream',
      'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer',
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      'Content-Security-Policy': "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self'; connect-src 'self' https:"
    });
    res.end(fs.readFileSync(file));
  } catch (error) {
    console.error('[preview]', error);
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Preview error: ' + error.message);
  }
});
const port = Number(process.env.PORT || 8787);
server.listen(port, '0.0.0.0', () => console.log(`Nasra preview listening on 0.0.0.0:${port}; login password: ${env.OWNER_PASSWORD === 'nasra-preview' ? 'nasra-preview (preview default)' : 'from environment'}; mock AI: ${useMock}`));
// Production has one five-minute Cron Trigger. The local reviewer preview
// emulates it more frequently so a one-job-per-invocation queue remains easy
// to exercise without changing production timing or query-budget behavior.
const previewCron = setInterval(() => {
  if (!pending.size) {
    try { worker.scheduled({ cron: '*/5 * * * *' }, env, ctx); } catch (error) { console.error('[preview cron]', error.message); }
  }
}, 750);
previewCron.unref();
process.on('SIGTERM', () => { clearInterval(previewCron); server.close(() => process.exit(0)); });
process.on('SIGINT', () => { clearInterval(previewCron); server.close(() => process.exit(0)); });
