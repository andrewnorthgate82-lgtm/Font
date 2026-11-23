#!/usr/bin/env node
/*
 * The single setup program. Everything the owner needs, in one run.
 *
 *   nasra-setup.bat            (double-click)  → this file
 *   node scripts/setup.mjs                     → same thing from a terminal
 *
 * Steps, in order, each one idempotent:
 *   1. Node version + built-in SQLite
 *   2. .env from the template, with a generated password
 *   3. Telegram: token + owner chat id (skip with Enter)
 *   4. AI text key (skip with Enter)
 *   5. Image key + public base URL (skip with Enter)
 *   6. Publish guard choice
 *   7. Final check
 *   8. Start the server and open the browser
 *
 * Rules this file follows, because the owner is not a programmer:
 *   - never print a secret, except the panel password, exactly once;
 *   - never fail silently: every exit has a sentence the owner can act on;
 *   - never write .env until every answer is collected;
 *   - a wrong answer is repaired (a mistyped URL is dropped, not saved);
 *   - --non-interactive skips every question and uses what is already there.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import readline from 'node:readline/promises';
import { spawn } from 'node:child_process';
import { stdin, stdout } from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseDotEnv, mergeEnvText } from '../server/env.mjs';
import { readHeartbeat } from '../server/telegram-control.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ENV = path.join(ROOT, '.env');
const EXAMPLE = path.join(ROOT, '.env.example');
const NON_INTERACTIVE = process.argv.includes('--non-interactive');

const c = {
  green: '\u001b[32m', red: '\u001b[31m', yellow: '\u001b[33m',
  cyan: '\u001b[36m', dim: '\u001b[2m', bold: '\u001b[1m', reset: '\u001b[0m'
};

/*
 * Every line also goes to a log file.
 *
 * Why: when the owner says "it did not work", the only useful thing they can send
 * is the exact output — and copying from a Windows console window is painful and
 * often truncated. Colour codes are stripped, so the file is readable. Secrets are
 * never printed by this script in the first place.
 */
const LOG = path.join(ROOT, 'nasra-setup-log.txt');
const strip = text => String(text).replace(/\u001b\[[0-9;]*[A-Za-z]/g, '');
try {
  fs.writeFileSync(LOG, [
    'Nasra setup log',
    `started: ${new Date().toISOString()}`,
    `node: ${process.version}`,
    `platform: ${process.platform} ${process.arch}`,
    `cwd: ${process.cwd()}`,
    `script: ${ROOT}`,
    ''.padEnd(60, '-'),
    ''
  ].join('\n'));
} catch {
  /* A read-only folder is not a reason to stop the setup. */
}

const say = (t = '') => {
  const line = String(t);
  stdout.write(`${line}\n`);
  try { fs.appendFileSync(LOG, `${strip(line)}\n`); } catch { /* best effort */ }
};
const ok = t => say(`  ${c.green}✓${c.reset} ${t}`);
const warn = t => say(`  ${c.yellow}!${c.reset} ${t}`);
const bad = t => say(`  ${c.red}✗${c.reset} ${t}`);
const dim = t => say(`  ${c.dim}${t}${c.reset}`);
/* Console only: the password must never end up in a file the owner might share. */
const saySecret = (t = '') => stdout.write(`${t}\n`);
const head = (n, total, t) => say(`\n${c.bold}[${n}/${total}]${c.reset} ${t}`);
const hr = () => say(`  ${c.dim}${'─'.repeat(52)}${c.reset}`);

function finish(code = 0) {
  say('');
  try { fs.appendFileSync(LOG, `${''.padEnd(60, '-')}\nexit code: ${code}\n`); } catch { /* best effort */ }
  process.exit(code);
}

/* --------------------------------------------------------------- preflight */

say('');
say(`${c.bold}  ┌─────────────────────────────────────────────┐${c.reset}`);
say(`${c.bold}  │   نسرا اصفهان — راه‌اندازی                   │${c.reset}`);
say(`${c.bold}  └─────────────────────────────────────────────┘${c.reset}`);
say('');

if (!fs.existsSync(path.join(ROOT, 'worker.js')) || !fs.existsSync(EXAMPLE)) {
  bad('فایل‌های پروژه کامل نیستند.');
  say('');
  say('  چه کار کنید: پوشه را از نو استخراج کنید، بعد دوباره روی nasra-setup.bat بزنید.');
  say('  راهنما: START-HERE.fa.md');
  finish(1);
}

/* 1 ------------------------------------------------------------------ Node */
head(1, 7, 'بررسی Node.js');
const [major, minor] = process.versions.node.split('.').map(Number);
if (major < 22 || (major === 22 && minor < 13)) {
  bad(`نسخه Node شما ${process.versions.node} است؛ حداقل 22.13 لازم است.`);
  say('');
  say('  چه کار کنید:');
  say('    1) به https://nodejs.org بروید');
  say('    2) دکمه سبز «Download Node.js (LTS)» را بزنید و نصب کنید');
  say('    3) لپ‌تاپ را یک بار خاموش و روشن کنید');
  say('    4) دوباره روی nasra-setup.bat دوبار کلیک کنید');
  finish(1);
}
ok(`Node ${process.versions.node}`);

/* The owner reads this warning as a failure. Filter exactly this one sentence. */
const originalEmitWarning = process.emitWarning;
process.emitWarning = function emitWarning(warning, ...rest) {
  const message = typeof warning === 'string' ? warning : warning?.message;
  if (/SQLite is an experimental/i.test(String(message || ''))) return;
  return originalEmitWarning.call(process, warning, ...rest);
};

try {
  const { DatabaseSync } = await import('node:sqlite');
  const probe = new DatabaseSync(':memory:');
  probe.exec('CREATE TABLE t(a INTEGER)');
  probe.close();
  ok('پایگاه‌داده داخلی Node کار می‌کند');
} catch (error) {
  bad(`پایگاه‌داده داخلی Node کار نمی‌کند: ${error.message}`);
  say('');
  say('  چه کار کنید: Node را از سایت رسمی دوباره نصب کنید (نسخه LTS).');
  finish(1);
}

/* ------------------------------------------------------------ read current */

const existing = fs.existsSync(ENV) ? parseDotEnv(fs.readFileSync(ENV, 'utf8')) : {};
const pick = (key, fallback = '') => existing[key] || fallback;

/* Non-interactive runs keep whatever is configured and only fill the gaps. */
let rl = null;
if (!NON_INTERACTIVE) {
  if (!stdin.isTTY) {
    say('');
    warn('این برنامه به پنجره ترمینال نیاز دارد.');
    say('');
    say('  درست‌ترین راه: روی فایل nasra-setup.bat دوبار کلیک کنید.');
    say('  یا از داخل PowerShell این را بزنید:  node scripts\\setup.mjs');
    say('  برای تنظیم خودکار بدون سؤال:        node scripts\\setup.mjs --non-interactive');
    finish(1);
  }
  rl = readline.createInterface({ input: stdin, output: stdout });
  const abort = () => {
    say('');
    say('  لغو شد. فایل .env دست‌نخورده ماند.');
    say('');
    try { rl.close(); } catch { /* closed */ }
    process.exit(130);
  };
  process.on('SIGINT', abort);
  process.on('uncaughtException', error => {
    if (error?.code === 'ABORT_ERR') abort();
    throw error;
  });
}

const answers = {};

async function ask(label, { fallback = '', secret = false } = {}) {
  const shown = fallback ? (secret ? '(از قبل تنظیم شده — Enter برای نگه‌داشتن)' : `[${fallback}]`) : '—';
  const answer = (await rl.question(`  ${label} ${c.dim}${shown}${c.reset}\n  > `)).trim();
  return answer || fallback;
}

async function askYes(label, fallback = false) {
  const hint = fallback ? 'بله/خیر — Enter = بله' : 'بله/خیر — Enter = خیر';
  const answer = (await rl.question(`  ${label} ${c.dim}${hint}${c.reset}\n  > `)).trim().toLowerCase();
  if (!answer) return fallback;
  return ['y', 'yes', 'b', 'bale', 'بله', '1'].includes(answer);
}

/* 2 ------------------------------------------------------------------ .env */
head(2, 7, 'فایل تنظیمات');
if (fs.existsSync(ENV)) {
  ok('از قبل هست — مقادیر قبلی نگه داشته می‌شوند');
} else {
  ok('از الگو ساخته می‌شود');
}
answers.OWNER_PASSWORD = pick('OWNER_PASSWORD') || `nasra-${crypto.randomBytes(9).toString('base64url')}`;
/* Never rotate an existing secret: that would log every session out. */
answers.SESSION_SECRET = pick('SESSION_SECRET') || crypto.randomBytes(32).toString('hex');

/* -------------------------------------------------------------- questions */

if (!NON_INTERACTIVE) {
  /* 3 ---------------------------------------------------------- telegram */
  head(3, 7, 'ربات تلگرام (اختیاری)');
  dim('اگر نمی‌خواهید، فقط Enter بزنید. بعداً هم می‌شود.');
  say('');
  const token = await ask('توکن ربات از BotFather', { fallback: pick('TG_BOT_TOKEN'), secret: true });
  if (token) answers.TG_BOT_TOKEN = token;

  const ownerChat = await ask('شناسه چت خودتان — عددی که @userinfobot می‌دهد', { fallback: pick('TG_OWNER_CHAT_ID') });
  if (ownerChat) {
    if (!/^-?\d{5,}$/.test(ownerChat)) {
      warn('این عدد به نظر معتبر نیست؛ ذخیره نشد. ربات به پیام‌ها جواب نمی‌دهد تا اصلاح شود.');
      answers.TG_OWNER_CHAT_ID = pick('TG_OWNER_CHAT_ID');
    } else {
      answers.TG_OWNER_CHAT_ID = ownerChat;
    }
  }

  /* 4 ------------------------------------------------------------- text AI */
  head(4, 7, 'هوش مصنوعی متن (اختیاری)');
  dim('بدون این، ایجنت‌ها در صف دستی می‌مانند و محتوایی ساخته نمی‌شود.');
  say('');
  const aiKey = await ask('کلید Gemini برای متن', { fallback: pick('AI_API_KEY'), secret: true });
  if (aiKey) {
    answers.AI_PROVIDER = 'gemini';
    answers.AI_API_KEY = aiKey;
  }

  /* 5 --------------------------------------------------------- image + URL */
  head(5, 7, 'پوستر خودکار (اختیاری)');
  dim('برای تولید پوستر، کلید تصویر و یک آدرس عمومی https لازم است.');
  say('');
  const imageKey = await ask('کلید تصویر Gemini', { fallback: pick('IMAGE_API_KEY'), secret: true });
  if (imageKey) {
    answers.IMAGE_PROVIDER = 'gemini';
    answers.IMAGE_API_KEY = imageKey;
    const baseUrl = await ask('آدرس عمومی https (مثل https://xxxx.trycloudflare.com)', { fallback: pick('PUBLIC_BASE_URL') });
    if (baseUrl) {
      /* A wrong URL is the most common reason posters silently stay manual. */
      if (!/^https:\/\//i.test(baseUrl)) {
        warn('آدرس باید با https:// شروع شود؛ ذخیره نشد.');
      } else {
        answers.PUBLIC_BASE_URL = baseUrl.replace(/\/+$/, '');
      }
    }
  }

  /* 6 -------------------------------------------------------- publish mode */
  head(6, 7, 'حالت انتشار');
  dim('dry_run یعنی هیچ پستی از این دستگاه بیرون نمی‌رود. این ایمن‌ترین حالت است.');
  say('');
  const live = await askYes('انتشار واقعی فعال شود؟', false);
  answers.PUBLISH_GUARD_MODE = live ? 'live' : 'dry_run';
} else {
  head(3, 7, 'پاسخ‌ها: غیرفعال (--non-interactive)');
  ok('فقط مقادیر موجود استفاده می‌شوند');
}

/* ------------------------------------------------------------------ write */

if (rl) rl.close();

const base = fs.existsSync(ENV) ? fs.readFileSync(ENV, 'utf8') : fs.readFileSync(EXAMPLE, 'utf8');
const merged = mergeEnvText(base, answers);
if (fs.existsSync(ENV)) fs.copyFileSync(ENV, `${ENV}.bak-${Date.now()}`);
fs.writeFileSync(ENV, merged.text, { mode: 0o600 });
ok(`${merged.touched} مقدار در .env نوشته شد`);

/* 7 ---------------------------------------------------------------- doctor */
head(7, 7, 'بررسی نهایی');
const doctor = spawn(process.execPath, [path.join(ROOT, 'scripts', 'doctor.mjs')], {
  cwd: ROOT,
  stdio: ['ignore', 'pipe', 'pipe'],
  encoding: 'utf8'
});
let doctorOut = '';
doctor.stdout.on('data', d => { doctorOut += d; });
const doctorCode = await new Promise(resolve => doctor.on('exit', resolve));
for (const line of doctorOut.split('\n')) {
  if (line.trim()) { try { fs.appendFileSync(LOG, `${strip(line)}\n`); } catch { /* best effort */ } }
}
if (doctorCode !== 0) {
  bad('یک مانع جدی هست:');
  say('');
  for (const line of doctorOut.split('\n')) {
    if (line.includes('✗') || /^\s{6}\S/.test(line)) say(`  ${line.trim()}`);
  }
  say('');
  say('  راهنما: START-HERE.fa.md');
  finish(1);
}
const warnings = (doctorOut.match(/! /g) || []).length;
ok(`آماده اجراست${warnings ? c.dim + ` (${warnings} هشدار طبیعی که مانع کار نیست)` + c.reset : ''}`);

/* ---------------------------------------------------------------- summary */

say('');
hr();
say(`${c.bold}  رمز ورود شما به پنل${c.reset}`);
say('');
saySecret(`      ${c.green}${c.bold}${answers.OWNER_PASSWORD}${c.reset}`);
say('');
dim('همین حالا یادداشتش کنید. در فایل .env هم هست، ولی یک‌بار نشان داده می‌شود.');
say(`${c.dim}  (رمز عمداً در فایل لاگ نوشته نمی‌شود؛ پس این فایل را می‌توانید امن بفرستید.)${c.reset}`);
hr();

/* Report what is actually in .env, not what this run happened to ask about: a
   token saved on a previous run is still a working bot. */
const finalEnv = parseDotEnv(fs.readFileSync(ENV, 'utf8'));
const telegramReady = Boolean(finalEnv.TG_BOT_TOKEN && finalEnv.TG_OWNER_CHAT_ID);
const aiReady = Boolean(finalEnv.AI_API_KEY);

say('');
say(`${c.bold}  آنچه تنظیم شد${c.reset}`);
say(`    سرور پنل:        http://localhost:8787`);
say(`    ربات تلگرام:     ${telegramReady ? `${c.green}وصل${c.reset}` : `${c.yellow}وصل نیست${c.reset} (بعداً: npm run setup)`}`);
say(`    هوش مصنوعی متن:  ${aiReady ? `${c.green}وصل${c.reset}` : `${c.yellow}وصل نیست${c.reset} (ایجنت‌ها دستی می‌مانند)`}`);
say(`    انتشار:          ${finalEnv.PUBLISH_GUARD_MODE === 'live' ? `${c.yellow}واقعی${c.reset}` : `${c.green}ایمن (هیچ پستی بیرون نمی‌رود)${c.reset}`}`);

if (NON_INTERACTIVE) {
  say('');
  say('  حالا این را بزنید:  npm start');
  finish(0);
}

/* -------------------------------------------------------------- start now */

say('');
say(`${c.bold}  سرور را الان اجرا کنم؟${c.reset}`);
say('');
const startNow = await (async () => {
  rl = readline.createInterface({ input: stdin, output: stdout });
  const answer = (await rl.question(`  ${c.dim}Enter = بله   /   n = بعداً${c.reset}\n  > `)).trim().toLowerCase();
  rl.close();
  return !['n', 'no', 'خیر'].includes(answer);
})();

if (!startNow) {
  say('');
  say('  هر وقت خواستید:  npm start');
  finish(0);
}

/* The launcher already refuses to start twice and opens the browser. */
say('');
hr();
const launcher = spawn(process.execPath, [path.join(ROOT, 'scripts', 'start-nasra.mjs')], {
  cwd: ROOT,
  stdio: 'inherit'
});
launcher.on('exit', code => finish(code ?? 0));
