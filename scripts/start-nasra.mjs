#!/usr/bin/env node
/*
 * One-command launcher for a personal machine (Windows / macOS / Linux).
 *
 *   node scripts/start-nasra.mjs
 *
 * What it adds over `node server/index.mjs`:
 * - It refuses to start with a clear Persian reason instead of a stack trace.
 * - It waits until the port is actually listening, then opens the browser on
 *   the right URL, so the owner never has to type an address.
 * - It keeps the window meaningful while the process runs: every important log
 *   line is echoed, Ctrl+C shuts the server down cleanly (SQLite backup first).
 *
 * It does not change any runtime behaviour: the real server is still
 * server/index.mjs, started with the same environment and the same .env file.
 */
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import { spawn, execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { readConfig, validateConfig, loadEnvFiles } from '../server/env.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ENTRY = path.join(ROOT, 'server', 'index.mjs');

/*
 * --dev turns this into the rehearsal launcher: stubbed AI, no Telegram polling,
 * guard forced to dry_run. Doing it here (instead of `KEY=value node ...` in
 * package.json) is what makes `npm run dev` work in a Windows terminal too.
 */
const DEV = process.argv.includes('--dev');
if (DEV) {
  process.env.NODE_ENV = 'development';
  process.env.MOCK_AI = '1';
  process.env.TELEGRAM_POLLING = '0';
  process.env.PUBLISH_GUARD_MODE = 'dry_run';
}

function line(text = '') { process.stdout.write(`${text}\n`); }
function head() {
  line('');
  line('  ┌─────────────────────────────────────────────┐');
  line('  │   نسرا اصفهان — استودیوی رسانه              │');
  line('  └─────────────────────────────────────────────┘');
  line('');
}

if (!fs.existsSync(path.join(ROOT, '.env'))) {
  head();
  line('  فایل .env پیدا نشد.');
  line('');
  line('  اول این را اجرا کنید:   node scripts/setup-env.mjs');
  line('');
  process.exit(1);
}

loadEnvFiles(ROOT, '.env');
const config = readConfig(process.env, { rootDir: ROOT });
const validation = validateConfig(config, process.env);
if (!validation.ok) {
  head();
  line('  سرور شروع نشد. دلیل:');
  line('');
  for (const problem of validation.problems) line(`    ✗ ${problem}`);
  line('');
  line('  بررسی کامل:   node scripts/doctor.mjs');
  line('');
  process.exit(1);
}
/* The server repeats these exact warnings once it starts, and two of them are
   expected on a laptop. Keep the pre-flight clean and let the runtime speak. */

/* Is something already on the port? Two servers on one port is the classic
   "my edits do nothing" trap, so say it out loud instead of failing on EADDRINUSE. */
function portBusy(port) {
  return new Promise(resolve => {
    const server = net.createServer();
    server.once('error', err => resolve(err.code === 'EADDRINUSE'));
    server.once('listening', () => server.close(() => resolve(false)));
    server.listen(port, '127.0.0.1');
  });
}

if (await portBusy(config.port)) {
  head();
  line(`  بندر ${config.port} از قبل اشغال است.`);
  line('');
  line('  احتمالاً نسخه‌ای از نسرا همین حالا در حال اجراست.');
  line(`  در مرورگر امتحان کنید:  http://localhost:${config.port}`);
  line('');
  line('  اگر می‌خواهید نسخه تازه اجرا شود، پنجره قبلی را با Ctrl+C ببندید.');
  line('');
  process.exit(1);
}

const url = `http://localhost:${config.port}`;
head();
line(`  آدرس پنل:      ${url}`);
line(`  پایگاه‌داده:    ${path.relative(ROOT, config.dbFile)}`);
line(`  هوش مصنوعی:    ${process.env.MOCK_AI === '1' ? 'حالت تمرینی (پاسخ ساختگی، بدون شبکه)' : (process.env.AI_PROVIDER ? process.env.AI_PROVIDER : 'تنظیم نشده — ایجنت‌ها دستی می‌مانند')}`);
line(`  پوستر خودکار:  ${process.env.IMAGE_PROVIDER ? process.env.IMAGE_PROVIDER : 'خاموش'}`);
line(`  گارد انتشار:   ${config.guard.mode === 'live' ? 'live — انتشار واقعی ممکن است' : 'dry_run — هیچ پستی بیرون نمی‌رود'}`);
line('');
line('  برای توقف: Ctrl+C در همین پنجره');
line('');

/* Open the browser once, after the port answers. Failure here is not fatal:
   plenty of headless/WSL setups have no browser, and the URL is already printed. */
const openers = { win32: ['cmd', ['/c', 'start', '', url]], darwin: ['open', [url]], linux: ['xdg-open', [url]] };
function openBrowser() {
  const [cmd, argv] = openers[process.platform] || openers.linux;
  execFile(cmd, argv, () => {});
}

const child = spawn(process.execPath, [ENTRY], {
  cwd: ROOT,
  /* NASRA_QUIET only hides the boot banner and the JSON summary; validation
     warnings and errors still reach the window. */
  env: { ...process.env, NASRA_QUIET: '1' },
  stdio: ['ignore', 'pipe', 'pipe']
});

let opened = false;
const relay = chunk => {
  process.stdout.write(chunk);
  if (!opened && /listening|\[nasra\] sqlite ready/i.test(String(chunk))) {
    opened = true;
    setTimeout(openBrowser, 300);
  }
};
child.stdout.on('data', relay);
child.stderr.on('data', relay);

/* If the server never prints a listening line (older boot path), open anyway
   once, a few seconds in. */
const fallbackOpen = setTimeout(() => {
  if (!opened) { opened = true; openBrowser(); }
}, 6000);

let stopping = false;
const stop = signal => {
  if (stopping) return;
  stopping = true;
  clearTimeout(fallbackOpen);
  line('');
  line('  در حال خاموش کردن… (بکاپ پایگاه‌داده نوشته می‌شود)');
  child.kill(signal);
  /* The server writes a SQLite backup during shutdown. Give it time; if it is
     still alive after 15s, escalate rather than leaving a stale process that
     holds the database open. */
  setTimeout(() => { if (child.exitCode === null) child.kill('SIGKILL'); }, 15000);
};
process.on('SIGINT', () => stop('SIGINT'));
process.on('SIGTERM', () => stop('SIGTERM'));

child.on('exit', code => {
  clearTimeout(fallbackOpen);
  line('');
  line(`  سرور متوقف شد (کد ${code}).`);
  if (code && !stopping) line('  برای دیدن دلیل:  node scripts/doctor.mjs');
  line('');
  process.exit(code ?? 0);
});
