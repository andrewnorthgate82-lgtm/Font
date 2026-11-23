#!/usr/bin/env node
/*
 * Create a local `.env` from `.env.example`.
 *
 *   node scripts/setup-env.mjs
 *
 * Why this exists: the runtime refuses to boot without OWNER_PASSWORD and
 * SESSION_SECRET, and asking a non-technical owner to invent a 32-byte secret
 * is how people end up pasting passwords into chat windows. This generates both
 * here, on their machine, and prints the password exactly once.
 *
 * Safety properties:
 * - Never overwrites an existing `.env` (use --force to rotate deliberately).
 * - Never prints SESSION_SECRET, never writes it anywhere but `.env`.
 * - `.env` is already in .gitignore, so nothing here can be committed by accident.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const EXAMPLE = path.join(ROOT, '.env.example');
const TARGET = path.join(ROOT, '.env');

const args = new Set(process.argv.slice(2));
const force = args.has('--force');

if (!fs.existsSync(EXAMPLE)) {
  console.error(`[setup-env] missing ${path.relative(ROOT, EXAMPLE)} — cannot build .env from a file that is not there.`);
  process.exit(1);
}
if (fs.existsSync(TARGET) && !force) {
  console.log('[setup-env] .env already exists; nothing was changed.');
  console.log('[setup-env] To rebuild it on purpose: node scripts/setup-env.mjs --force');
  process.exit(0);
}

const secret = crypto.randomBytes(32).toString('hex');
// Readable but not guessable: four words would need a dictionary, so use a
// symbol-free random string a person can retype from a screenshot.
const password = `nasra-${crypto.randomBytes(9).toString('base64url')}`;

const filled = fs.readFileSync(EXAMPLE, 'utf8')
  .replace(/^SESSION_SECRET=.*$/m, `SESSION_SECRET=${secret}`)
  .replace(/^OWNER_PASSWORD=.*$/m, `OWNER_PASSWORD=${password}`);

if (fs.existsSync(TARGET) && force) fs.copyFileSync(TARGET, `${TARGET}.bak-${Date.now()}`);

// mode 0600 where the platform honours it; ignored on Windows.
fs.writeFileSync(TARGET, filled, { mode: 0o600 });

console.log('');
console.log('  .env ساخته شد.');
console.log('');
console.log(`  رمز ورود شما به پنل:   ${password}`);
console.log('');
console.log('  همین حالا این رمز را در جای امنی ذخیره کنید. دوباره نمایش داده نمی‌شود،');
console.log('  ولی همیشه داخل فایل .env روی همین دستگاه هست.');
console.log('');
console.log('  مرحله بعد:  node scripts/doctor.mjs');
console.log('');
