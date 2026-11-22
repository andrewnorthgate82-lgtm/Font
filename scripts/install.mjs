#!/usr/bin/env node
/*
 * One-command installer.
 *
 *   npm install
 *
 * The owner should not have to know the order of the five scripts. This runs
 * them in order, stops at the first real blocker, and ends with something they
 * can act on. It is safe to run twice: every step is idempotent.
 *
 * What it deliberately does NOT do: create any account, fetch any key, or send
 * anything to Telegram. Those need the owner's own credentials, so it asks.
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const c = { green: '\u001b[32m', red: '\u001b[31m', yellow: '\u001b[33m', dim: '\u001b[2m', bold: '\u001b[1m', reset: '\u001b[0m' };
const say = (text = '') => process.stdout.write(`${text}\n`);
const step = n => say(`\n${c.bold}[${n}/3]${c.reset}`);

function run(script, args = [], { interactive = false } = {}) {
  const child = spawnSync(process.execPath, [path.join(ROOT, script), ...args], {
    cwd: ROOT,
    stdio: interactive ? 'inherit' : ['ignore', 'pipe', 'pipe'],
    encoding: 'utf8'
  });
  return { code: child.status ?? 1, out: child.stdout || '', err: child.stderr || '' };
}

say('');
say(`${c.bold}  نسرا اصفهان — نصب${c.reset}`);
say(`${c.dim}  این برنامه همه‌چیز را به ترتیب آماده می‌کند.${c.reset}`);

/* 1. Node version. The runtime cannot work without node:sqlite. */
step(1);
say('  بررسی نسخه Node.js');
const [major, minor] = process.versions.node.split('.').map(Number);
if (major < 22 || (major === 22 && minor < 13)) {
  say(`  ${c.red}✗${c.reset} نسخه Node شما ${process.versions.node} است.`);
  say('');
  say('  نسخه 22.13 یا بالاتر لازم است.');
  say('  از https://nodejs.org دکمه LTS را نصب کنید و دوباره اجرا کنید:');
  say('     npm install');
  say('');
  process.exit(1);
}
say(`  ${c.green}✓${c.reset} Node ${process.versions.node}`);

/* Node prints an ExperimentalWarning for node:sqlite. The owner reads that as a
   failure, so this one sentence is filtered here as well as in d1-sqlite.mjs. */
const originalEmitWarning = process.emitWarning;
process.emitWarning = function emitWarning(warning, ...rest) {
  const message = typeof warning === 'string' ? warning : warning?.message;
  if (/SQLite is an experimental/i.test(String(message || ''))) return;
  return originalEmitWarning.call(process, warning, ...rest);
};

try {
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(':memory:');
  db.exec('CREATE TABLE t(a INTEGER)');
  db.close();
  say(`  ${c.green}✓${c.reset} پایگاه‌داده داخلی Node کار می‌کند`);
} catch (error) {
  say(`  ${c.red}✗${c.reset} پایگاه‌داده داخلی Node کار نمی‌کند: ${error.message}`);
  say('  Node را از سایت رسمی دوباره نصب کنید (نسخه LTS).');
  process.exit(1);
}

/* 2. .env — password and secret are generated, never invented by the owner. */
step(2);
const envPath = path.join(ROOT, '.env');
if (fs.existsSync(envPath)) {
  say(`  ${c.green}✓${c.reset} فایل تنظیمات از قبل هست؛ دست نخورد.`);
} else {
  say('  ساخت فایل تنظیمات و رمز ورود');
  const result = run('scripts/setup-env.mjs');
  if (result.code !== 0) {
    say(`  ${c.red}✗${c.reset} ساخته نشد.`);
    say(result.err || result.out);
    process.exit(1);
  }
  const password = (result.out.match(/رمز ورود شما به پنل:\s+(\S+)/) || [])[1] || '(در فایل .env)';
  say(`  ${c.green}✓${c.reset} ساخته شد.`);
  say('');
  say(`  ${c.bold}رمز ورود شما به پنل:${c.reset}`);
  say(`      ${c.green}${password}${c.reset}`);
  say('');
  say(`  ${c.yellow}همین حالا یادداشتش کنید.${c.reset} در فایل .env هم هست،`);
  say('  ولی این تنها جایی است که یک‌بار نمایش داده می‌شود.');
}

/* 3. Checks. Warnings are expected; only failures stop the owner here. */
step(3);
say('  بررسی نهایی');
const doctor = run('scripts/doctor.mjs');
if (doctor.code !== 0) {
  say(`  ${c.red}✗${c.reset} یک مانع جدی هست:`);
  say(doctor.out.split('\n').filter(l => l.includes('✗') || l.trim().startsWith('✗')).join('\n') || doctor.out);
  say('');
  say('  راهنما: START-HERE.fa.md');
  process.exit(1);
}
const warnings = (doctor.out.match(/! /g) || []).length;
say(`  ${c.green}✓${c.reset} آماده اجراست${warnings ? c.dim + ` (${warnings} هشدار طبیعی)` + c.reset : ''}`);

say('');
say(`${c.bold}  نصب تمام شد.${c.reset}`);
say('');
say('  حالا این را اجرا کنید:');
say(`      ${c.green}npm start${c.reset}`);
say('');
say('  اگر می‌خواهید ربات تلگرام را هم وصل کنید (اختیاری ولی توصیه‌شده):');
say(`      ${c.green}npm run setup:wizard${c.reset}`);
say('');
