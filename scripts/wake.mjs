#!/usr/bin/env node
/*
 * Turn "the laptop should wake up" into a real, checked action — or an honest
 * refusal.
 *
 *   node scripts/wake.mjs            # one attempt, verbose
 *   node scripts/wake.mjs --check    # only report state, change nothing
 *
 * Background, because this is the part people get wrong:
 *
 * Telegram's bot API cannot power on a machine. No bot can. A Telegram message
 * can only cause work if something is already listening. So the pipeline is
 * always: a physical switch (Wake-on-LAN, BIOS "power on when AC returns", a
 * smart plug) → the machine boots → this script runs → the studio starts.
 *
 * What this script does is the last two steps, with two guarantees:
 *
 * 1. It never claims success it did not observe. If the studio does not answer
 *    afterwards, it says so and exits non-zero.
 * 2. It will only run a command the owner already put in .env (WAKE_COMMAND).
 *    A bot message can therefore trigger a fixed, reviewed action — never an
 *    arbitrary shell string.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { readConfig, loadEnvFiles } from '../server/env.mjs';
import { readHeartbeat, verifyWakeCode, wakeCode } from '../server/telegram-control.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = new Set(process.argv.slice(2));
const checkOnly = args.has('--check');
const code = (process.argv.find(a => a.startsWith('--code=')) || '').split('=')[1] || '';

loadEnvFiles(ROOT, '.env');
const config = readConfig(process.env, { rootDir: ROOT });

function say(text = '') { console.log(text); }

function probe(url, timeoutMs = 2500) {
  return new Promise(resolve => {
    const req = http.get(url, { timeout: timeoutMs }, res => {
      res.resume();
      resolve(res.statusCode ? { ok: res.statusCode < 500, status: res.statusCode } : { ok: false });
    });
    req.on('timeout', () => { req.destroy(); resolve({ ok: false, reason: 'timeout' }); });
    req.on('error', error => resolve({ ok: false, reason: error.code || error.message }));
  });
}

const url = `http://127.0.0.1:${config.port}/api/health`;

say('');
say('  بررسی وضعیت نسرا');
say('');

/* 1. Is the studio already running? If so there is nothing to wake. */
const alive = await probe(url);
if (alive.ok) {
  const hb = readHeartbeat(config.dataDir);
  say(`  ✓ استودیو همین حالا روشن است (پورت ${config.port})`);
  if (hb.at) say(`    آخرین ضربان: ${Math.round((hb.age_ms || 0) / 1000)} ثانیه پیش`);
  say('');
  say('  کاری لازم نیست.');
  say('');
  process.exit(0);
}
say('  ✗ استودیو پاسخ نمی‌دهد.');

/* 2. Optional code check. The bot hands out a code derived from TG_WAKE_SECRET,
      so an old screenshot cannot be used to start the machine forever. */
const secret = process.env.TG_WAKE_SECRET || '';
if (code && secret) {
  if (!verifyWakeCode(secret, code)) {
    say('  ✗ کد روشن‌کردن معتبر نیست یا مربوط به امروز نیست.');
    say('    یک /wake تازه در تلگرام بفرستید.');
    say('');
    process.exit(2);
  }
  say('  ✓ کد روشن‌کردن معتبر است.');
} else if (code && !secret) {
  say('  ! کد داده شد ولی TG_WAKE_SECRET تنظیم نشده است؛ کد نادیده گرفته شد.');
}
if (secret) say(`    کد امروز: ${wakeCode(secret)}`);

/* 3. No reviewed command → stop here. Guessing is how a remote-start feature
      becomes a remote-code-execution feature. */
if (!config.wake.command) {
  say('');
  say('  درخواست روشن‌کردن نمی‌تواند اجرا شود.');
  say('');
  say('  دلیل: تلگرام نمی‌تواند دستگاه خاموش را روشن کند. برای این کار یک');
  say('  مسیر فیزیکی (برق/شبکه) لازم است و یک دستور ازپیش‌بررسی‌شده.');
  say('');
  say('  برای فعال‌کردن، در فایل .env مقدار WAKE_COMMAND را پر کنید و');
  say('  TG_WAKE_SECRET را یک رشته طولانی بگذارید.');
  say('  راهنما: LAPTOP.fa.md — بخش «روشن کردن از راه دور».');
  say('');
  process.exit(1);
}

if (checkOnly) {
  say('');
  say(`  WAKE_COMMAND تنظیم شده است و در صورت اجرای واقعی فراخوانی می‌شود.`);
  say('');
  process.exit(0);
}

/* 4. Run the reviewed command. Windows and POSIX differ: `exec` is a shell
      builtin on POSIX, `cmd /c` is the Windows equivalent. */
say('');
say('  اجرای دستور روشن‌کردن…');
const useShell = process.platform === 'win32';
const child = useShell
  ? spawn('cmd', ['/c', config.wake.command], { cwd: ROOT, stdio: 'inherit' })
  : spawn('/bin/sh', ['-c', config.wake.command], { cwd: ROOT, stdio: 'inherit' });

const exitCode = await new Promise(resolve => child.on('exit', resolve));
if (exitCode !== 0) {
  say('');
  say(`  ✗ دستور روشن‌کردن با کد ${exitCode} تمام شد.`);
  say('    پیام بالا را ببینید؛ همان را برای پشتیبانی بفرستید.');
  say('');
  process.exit(3);
}

/* 5. Prove it. Wait, then probe again — "I ran the command" is not "the studio
      is up", and only the second one is useful to the owner. */
say('');
say('  دستور اجرا شد. منتظر بالا آمدن استودیو…');
let up = false;
for (let attempt = 0; attempt < 20; attempt++) {
  await new Promise(r => setTimeout(r, 1500));
  const probeResult = await probe(url);
  if (probeResult.ok) { up = true; break; }
}

say('');
if (up) {
  say(`  ✓ استودیو روشن شد: http://localhost:${config.port}`);
  say('');
  process.exit(0);
}
say('  ✗ دستور اجرا شد ولی استودیو بالا نیامد.');
say('');
say('  این حالت یعنی دستور اشتباه است یا خود دستور اجرا نشده.');
say('  در فایل .env مقدار WAKE_COMMAND را بررسی کنید.');
say('');
process.exit(4);
