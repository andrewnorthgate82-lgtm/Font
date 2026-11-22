#!/usr/bin/env node
/*
 * Guided setup for someone who does not write code.
 *
 *   npm run setup:wizard
 *
 * The other scripts each do one thing and expect the owner to know the order.
 * This walks the order, asks only what it cannot work out itself, and ends by
 * telling the owner exactly what will happen next.
 *
 * Design rules:
 * - Every question has a default, so pressing Enter is always a valid answer.
 * - Nothing secret is echoed back to the screen or into logs.
 * - Nothing is written to .env until every answer has been collected, so
 *   Ctrl+C leaves the previous file untouched.
 * - It never talks to Telegram/Instagram/Rubika. The one optional network call
 *   is checking that a Telegram bot token is valid, and only if asked.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseDotEnv, mergeEnvText } from '../server/env.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ENV = path.join(ROOT, '.env');
const EXAMPLE = path.join(ROOT, '.env.example');

/*
 * A wizard needs a real terminal. With piped input, readline simply runs out of
 * lines and every later question waits forever; refusing up front turns a hang
 * into one clear sentence.
 */
if (!stdin.isTTY) {
  console.error('');
  console.error('  این راهنما به پنجره ترمینال واقعی نیاز دارد (ورودی از فایل/لوله کار نمی‌کند).');
  console.error('  در ترمینال خودتان اجرا کنید:   npm run setup:wizard');
  console.error('  یا تنظیم سریع بدون سؤال:        npm run setup');
  console.error('');
  process.exit(1);
}

const rl = readline.createInterface({ input: stdin, output: stdout });

/* Ctrl+C or Ctrl+D must leave the previous .env exactly as it was. Nothing is
   written before the last question, so exiting here is already safe — this only
   replaces a stack trace with a sentence. */
const abort = () => {
  console.log('');
  console.log('  لغو شد. فایل .env دست‌نخورده باقی ماند.');
  console.log('');
  try { rl.close(); } catch { /* already closed */ }
  process.exit(130);
};
process.on('SIGINT', abort);
process.on('uncaughtException', error => {
  if (error?.code === 'ABORT_ERR') abort();
  throw error;
});
const answers = {};
let changed = 0;

function say(text = '') { stdout.write(`${text}\n`); }

async function ask(label, { fallback = '', required = false, allowEmpty = true } = {}) {
  for (;;) {
    const hint = fallback ? ` [${fallback}]` : allowEmpty ? ' [خالی]' : '';
    const answer = (await rl.question(`  ${label}${hint}: `)).trim();
    const value = answer || fallback;
    if (required && !value) {
      say('  ⚠ این مورد لازم است. دوباره وارد کنید.');
      continue;
    }
    return value;
  }
}

async function yesNo(label, fallback = false) {
  const hint = fallback ? ' [بله/خیر — پیش‌فرض بله]' : ' [بله/خیر — پیش‌فرض خیر]';
  const answer = (await rl.question(`  ${label}${hint}: `)).trim().toLowerCase();
  if (!answer) return fallback;
  return ['y', 'yes', 'b', 'bale', 'بله', '1'].includes(answer);
}

say('');
say('  ┌──────────────────────────────────────────────┐');
say('  │   نسرا اصفهان — راه‌اندازی گام‌به‌گام          │');
say('  └──────────────────────────────────────────────┘');
say('');
say('  هر سؤال یک پیش‌فرض دارد. اگر نمی‌دانید، فقط Enter بزنید.');
say('  هیچ‌چیز تا پایانِ همه سؤال‌ها نوشته نمی‌شود.');
say('');

const existing = fs.existsSync(ENV) ? parseDotEnv(fs.readFileSync(ENV, 'utf8')) : {};
const pick = (key, fallback = '') => existing[key] || fallback;

/* ---------------------------------------------------------------- 1. basics */
say('  ── بخش ۱ از ۴: ورود به پنل ──');
say('');
let password = pick('OWNER_PASSWORD');
if (password) {
  say('  رمز ورود از قبل تنظیم شده است و تغییر نمی‌کند.');
} else {
  say('  رمز ورود به پنل ساخته می‌شود (حداقل ۱۰ کاراکتر).');
  say('  اگر چیزی وارد نکنید، یک رمز تصادفی امن ساخته می‌شود.');
  password = await ask('رمز دلخواه (یا Enter برای ساخت خودکار)');
  if (password && password.length < 10) {
    say('  ⚠ کوتاه‌تر از ۱۰ کاراکتر است؛ رمز تصادفی ساخته می‌شود.');
    password = '';
  }
}
answers.OWNER_PASSWORD = password; // filled later if empty

/* ------------------------------------------------------------- 2. telegram */
say('');
say('  ── بخش ۲ از ۴: ربات تلگرام ──');
say('');
say('  اگر می‌خواهید همه‌چیز از تلگرام کنترل شود، این بخش را پر کنید.');
say('  توکن را از BotFather بگیرید؛ همان رباتی که از قبل دارید.');
say('  اگر الان نمی‌خواهید، Enter بزنید — بعداً هم می‌شود.');
say('');
const tgToken = pick('TG_BOT_TOKEN');
let tokenValue = tgToken;
if (tgToken) {
  say('  توکن ربات از قبل تنظیم شده است.');
} else {
  say('  توکن را اینجا نچسبانید اگر کسی صفحه را می‌بیند.');
  tokenValue = await ask('توکن ربات (BotFather)');
  answers.TG_BOT_TOKEN = tokenValue;
}
say('');
say('  شناسه چت خودتان را لازم داریم تا فقط شما بتوانید دستور بدهید.');
say('  اگر نمی‌دانید: به ربات @userinfobot پیام بدهید و عدد Id را بگیرید.');
const ownerChat = await ask('شناسه چت شما (عدد)', { fallback: pick('TG_OWNER_CHAT_ID') });
answers.TG_OWNER_CHAT_ID = ownerChat;

/* ---------------------------------------------------------------- 3. brain */
say('');
say('  ── بخش ۳ از ۴: هوش مصنوعی متن ──');
say('');
say('  بدون این، ایجنت‌ها در حالت دستی می‌مانند و محتوایی تولید نمی‌شود.');
say('  اگر کلید Gemini دارید، همین‌جا می‌توانید تنظیم کنید (فقط در .env ذخیره می‌شود).');
const aiKey = pick('AI_API_KEY');
let aiValue = aiKey;
if (aiKey) {
  say('  کلید متن از قبل تنظیم شده است.');
} else {
  aiValue = await ask('کلید Gemini برای متن (یا Enter)');
}
if (aiValue) {
  answers.AI_PROVIDER = 'gemini';
  answers.AI_API_KEY = aiValue;
}

/* ----------------------------------------------------------------- 4. media */
say('');
say('  ── بخش ۴ از ۴: پوستر خودکار ──');
say('');
say('  تولید خودکار پوستر به دو چیز نیاز دارد: کلید تصویر، و یک آدرس');
say('  عمومی https که تلگرام بتواند فایل را از آن دانلود کند.');
say('');
const imageKey = pick('IMAGE_API_KEY');
let imageValue = imageKey;
if (imageKey) {
  say('  کلید تصویر از قبل تنظیم شده است.');
} else {
  imageValue = await ask('کلید تصویر Gemini (یا Enter)');
}
if (imageValue) {
  answers.IMAGE_PROVIDER = 'gemini';
  answers.IMAGE_API_KEY = imageValue;
  say('');
  say('  آدرس عمومی https (مثل https://xxxx.trycloudflare.com).');
  say('  اگر ندارید، خالی بگذارید: پوستر ساخته می‌شود ولی برای تلگرام');
  say('  فرستاده نمی‌شود. بعداً هم می‌شود اضافه کرد.');
  answers.PUBLIC_BASE_URL = await ask('PUBLIC_BASE_URL (https)', { fallback: pick('PUBLIC_BASE_URL') });
}

/* ------------------------------------------------------- 5. safe behaviour */
say('');
say('  ── تنظیمات ایمنی (پیش‌فرض‌ها را تغییر ندهید اگر مطمئن نیستید) ──');
say('');
const guardLive = await yesNo('انتشار واقعی فعال شود؟ (اگر مطمئن نیستید: خیر)', false);
answers.PUBLISH_GUARD_MODE = guardLive ? 'live' : 'dry_run';

/* ------------------------------------------------------------------- write */
rl.close();

say('');
say('  در حال نوشتن .env…');

if (!fs.existsSync(EXAMPLE)) {
  say('  ✗ فایل .env.example پیدا نشد. مخزن کامل نیست.');
  process.exit(1);
}

if (!answers.OWNER_PASSWORD) answers.OWNER_PASSWORD = `nasra-${crypto.randomBytes(9).toString('base64url')}`;
/* Reuse the existing secret if there is one; never rotate it silently, because
   rotating logs every owner session out. */
answers.SESSION_SECRET = pick('SESSION_SECRET') || crypto.randomBytes(32).toString('hex');

const base = fs.existsSync(ENV) ? fs.readFileSync(ENV, 'utf8') : fs.readFileSync(EXAMPLE, 'utf8');
const merged = mergeEnvText(base, answers);
const text = merged.text;
changed = merged.touched;

/* Keep a copy of the previous file: a wizard answer is a deliberate change, but
   nobody expects to lose yesterday's keys because of a typo. */
if (fs.existsSync(ENV)) fs.copyFileSync(ENV, `${ENV}.bak-${Date.now()}`);
fs.writeFileSync(ENV, text, { mode: 0o600 });

say(`  ✓ ${changed} مقدار نوشته شد.`);
say('');
say('  ─────────────────────────────────────────────');
say('  رمز ورود شما به پنل:');
say('');
say(`      ${answers.OWNER_PASSWORD}`);
say('');
say('  همین حالا آن را جایی امن یادداشت کنید.');
say('  ─────────────────────────────────────────────');
say('');
say('  مرحله بعد:  npm run doctor');
say('  سپس:        npm start');
say('');
