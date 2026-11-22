#!/usr/bin/env node
/*
 * Pre-flight check for running Nasra on a laptop (Windows, macOS or Linux).
 *
 *   node scripts/doctor.mjs
 *
 * It answers one question: "if I start the server right now, will it work, and
 * if not, exactly which line in .env is wrong?" It never starts the server and
 * never touches the network.
 *
 * It reuses readConfig()/validateConfig() from server/env.mjs, so the doctor can
 * never disagree with the runtime about what a valid configuration is.
 */
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { readConfig, validateConfig, loadEnvFiles, configSummary } from '../server/env.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MIN_NODE = [22, 13, 0];

const results = [];
const ok = (title, detail = '') => results.push({ level: 'ok', title, detail });
const warn = (title, detail = '') => results.push({ level: 'warn', title, detail });
const fail = (title, detail = '') => results.push({ level: 'fail', title, detail });

function cmpVersion(actual, min) {
  for (let i = 0; i < 3; i++) {
    const a = actual[i] || 0;
    const b = min[i] || 0;
    if (a > b) return 1;
    if (a < b) return -1;
  }
  return 0;
}

/* 1. Node version — node:sqlite needs 22.13+ or the flag below. */
const version = process.versions.node.split('.').map(Number);
const major = version[0];
if (cmpVersion(version, MIN_NODE) >= 0) {
  /* Say which branch of the compatibility rule was used: "newer than 22" is a
     different reason from "22.13 or later", and the owner may be asked about it. */
  ok(`Node ${process.versions.node}`, major >= 24
    ? 'نسخه جدیدتر از حد لازم؛ پایگاه‌داده داخلی بررسی می‌شود'
    : 'نسخه کافی برای node:sqlite (۲۲.۱۳ یا بالاتر)');
} else if (cmpVersion(version, [22, 5, 0]) >= 0) {
  fail(`Node ${process.versions.node}`, 'برای node:sqlite به Node 22.13 یا بالاتر نیاز است (نسخه‌های 22.5–22.12 فقط با --experimental-sqlite کار می‌کنند). Node را به‌روزرسانی کنید.');
} else {
  fail(`Node ${process.versions.node}`, 'خیلی قدیمی است. Node 22 LTS (نسخه 22.13 یا بالاتر) نصب کنید: https://nodejs.org');
}

/* 2. node:sqlite actually importable. */
try {
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(':memory:');
  db.exec('CREATE TABLE t(a INTEGER); INSERT INTO t VALUES (1);');
  const row = db.prepare('SELECT COUNT(*) AS n FROM t').get();
  db.close();
  if (Number(row?.n) === 1) ok('node:sqlite', 'نوشتن و خواندن در حافظه موفق بود');
  else fail('node:sqlite', 'پایگاه‌داده در حافظه پاسخ درست نداد');
} catch (error) {
  fail('node:sqlite', `در دسترس نیست: ${error.message}`);
}

/* 3. .env present, and which optional variables it supplied. */
const envFile = path.join(ROOT, '.env');
if (fs.existsSync(envFile)) {
  const loaded = loadEnvFiles(ROOT, '.env');
  ok('.env پیدا شد', `${loaded.length} متغیر از فایل خوانده شد`);
} else {
  fail('.env وجود ندارد', 'یک بار این را اجرا کنید:  node scripts/setup-env.mjs');
}

/* 4. Configuration validity — the same function the server uses at boot. */
const config = readConfig(process.env, { rootDir: ROOT });
const validation = validateConfig(config, process.env);
for (const problem of validation.problems) fail('تنظیمات ناقص است', problem);
for (const warning of validation.warnings) {
  /* The AI-provider warning is repeated in Persian below with an action; do not say it twice. */
  if (/No usable AI provider/.test(warning)) continue;
  warn('هشدار تنظیمات', warning);
}
if (validation.ok) ok('تنظیمات اجباری', 'رمز ورود و Secret هر دو درست هستند');

/* 5. Data directories writable (Windows paths and permissions differ from Linux). */
for (const [label, dir] of [['پوشه داده', config.dataDir], ['پوشه بکاپ', config.backup.dir], ['پوشه رسانه', config.media.dir]]) {
  try {
    fs.mkdirSync(dir, { recursive: true });
    const probe = path.join(dir, `.write-test-${process.pid}`);
    fs.writeFileSync(probe, 'ok');
    fs.rmSync(probe, { force: true });
    ok(`${label} قابل نوشتن است`, path.relative(ROOT, dir) || '.');
  } catch (error) {
    fail(`${label} قابل نوشتن نیست`, `${dir} — ${error.message}`);
  }
}

/* 6. Port availability. A stale server on the same port is the most common
      "why does nothing change when I edit the code" cause. */
function portFree(port) {
  return new Promise(resolve => {
    const server = net.createServer();
    server.once('error', err => resolve(err.code === 'EADDRINUSE' ? false : true));
    server.once('listening', () => server.close(() => resolve(true)));
    server.listen(port, '0.0.0.0');
  });
}
if (await portFree(config.port)) ok(`بندر ${config.port} آزاد است`);
else fail(`بندر ${config.port} اشغال است`, 'یک نمونه از سرور از قبل در حال اجراست. اول آن را ببندید (Ctrl+C در پنجره قدیمی) و بعد دوباره اجرا کنید.');

/* 7. openssl — only needed by scripts/local-https.mjs (the local https test). */
try {
  const out = execFileSync('openssl', ['version'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  ok('openssl موجود است', out);
} catch {
  warn('openssl پیدا نشد', 'فقط برای تست HTTPS محلی لازم است (scripts/local-https.mjs). برای اجرای عادی سرور اهمیتی ندارد. روی ویندوز با Git for Windows نصب می‌شود.');
}

/* 8. What will actually happen at boot — mirrors the server's own log lines. */
const summary = configSummary(config);
if (!process.env.AI_PROVIDER || (process.env.AI_PROVIDER !== 'workers_ai' && !process.env.AI_API_KEY)) {
  warn('هوش مصنوعی متن تنظیم نشده', 'ایجنت‌ها در حالت دستی صف می‌شوند؛ محتوایی تولید نمی‌شود. AI_PROVIDER و AI_API_KEY را پر کنید.');
} else {
  ok('هوش مصنوعی متن تنظیم شده', summary.image_provider ? `تصویر: ${summary.image_provider}` : '');
}
if (summary.image_provider && !config.publicBaseUrl) {
  warn('PUBLIC_BASE_URL خالی است', 'پوستر ساخته می‌شود ولی تلگرام نمی‌تواند فایل را دانلود کند؛ لینک رسانه باید https عمومی باشد.');
} else if (summary.image_provider && !/^https:\/\//i.test(config.publicBaseUrl)) {
  warn('PUBLIC_BASE_URL با https شروع نمی‌شود', 'تلگرام و اینستاگرام فقط لینک https را می‌پذیرند.');
}
if (config.guard.mode === 'dry_run') {
  ok('گارد انتشار در حالت dry_run', 'هیچ پستی از این دستگاه بیرون نمی‌رود');
} else {
  warn('گارد انتشار در حالت live است', `allowlist: ${config.guard.allowlist.length} مورد — انتشار واقعی ممکن است انجام شود`);
}

/* 9. Platform notes that only apply to a personal machine. */
if (process.platform === 'win32') {
  warn('ویندوز: سرور باید در پنجره باز بماند', 'اتوپایلوت و زمان‌بند تا وقتی این پنجره باز است کار می‌کنند. برای اجرای بدون پنجره از Task Scheduler استفاده کنید (راهنما: LAPTOP.fa.md).');
  if (os.totalmem() < 512 * 1024 * 1024) warn('حافظه کم', 'کمتر از ۵۱۲ مگابایت رم آزاد');
}

/* ---- report ---- */
const order = { fail: 0, warn: 1, ok: 2 };
const marks = { fail: '✗', warn: '!', ok: '✓' };
console.log('\nنسرا اصفهان — بررسی پیش از اجرا\n');
for (const item of results.sort((a, b) => order[a.level] - order[b.level])) {
  console.log(`  ${marks[item.level]} ${item.title}${item.detail ? `\n      ${item.detail}` : ''}`);
}
const failures = results.filter(r => r.level === 'fail').length;
const warnings = results.filter(r => r.level === 'warn').length;
console.log('');
if (failures) {
  console.log(`  نتیجه: ${failures} مانع جدی. تا رفع نشود سرور بالا نمی‌آید.`);
  console.log('  راهنما: LAPTOP.fa.md');
  console.log('');
  process.exit(1);
}
console.log(`  نتیجه: آماده اجراست${warnings ? ` (${warnings} هشدار، اجرا را متوقف نمی‌کند)` : ''}.`);
console.log(`  شروع:  npm start      (مرورگر خودکار باز می‌شود: http://localhost:${config.port})`);
console.log('');
