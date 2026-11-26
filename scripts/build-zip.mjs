#!/usr/bin/env node
/*
 * Build the ZIP that goes on the owner's laptop.
 *
 * The branch download contains the whole repository: two Excel reports from an
 * earlier project, a stray photo, a logo SVG, a 2.3 MB result.json, the
 * Cloudflare worker config, the CI workflow, the tests. None of it is needed to
 * run Nasra on a Windows laptop, and all of it makes "which file do I click?"
 * harder than it should be.
 *
 * So the package is an allowlist, not "archive everything": a file is in the ZIP
 * only if it is named below. Anything listed must exist, otherwise the build
 * fails instead of shipping a broken package.
 *
 *   node scripts/build-zip.mjs [output.zip]
 *
 * The archive has a single top-level folder, "nasra", because the owner extracts
 * it into C:\nasra and is then one double-click away from the panel. The setup
 * batch file detects package.json next to itself and skips its own download, so
 * this package and the download path stay the same experience.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.resolve(process.argv[2] || path.join(ROOT, 'nasra-laptop.zip'));
const TOP = 'nasra';

/* Everything the laptop runtime reads, relative to the repo root. */
const FILES = [
  /* the three files the owner double-clicks */
  'nasra-setup.bat',
  'nasra-start.bat',
  'nasra-settings.bat',

  /* configuration template — the setup writes .env from it */
  '.env.example',

  /* runtime */
  'package.json',
  'worker.js',
  'schema.sql',
  'server/index.mjs',
  'server/http-server.mjs',
  'server/env.mjs',
  'server/d1-sqlite.mjs',
  'server/publish-guard.mjs',
  'server/scheduler.mjs',
  'server/telegram-poller.mjs',
  'server/telegram-control.mjs',
  'server/autopilot.mjs',
  'server/media-store.mjs',
  'server/image-provider.mjs',
  'server/png-to-jpeg.mjs',
  'server/media-upload.mjs',
  'server/telegram-media.mjs',
  /* loaded by a dynamic import in server/index.mjs (MOCK_AI=1), so a static
     scan of the imports does not see it; leaving it out breaks mock mode only
     when the owner turns it on, which is exactly the kind of late failure to avoid. */
  'server/mock-ai.mjs',

  /* setup, start, diagnose */
  'scripts/setup.mjs',
  'scripts/start-nasra.mjs',
  'scripts/doctor.mjs',
  'scripts/telegram-status.mjs',
  'scripts/wake.mjs',
  'scripts/local-https.mjs',

  /* the panel */
  'public/index.html',
  'public/styles.css',
  'public/_headers',
  'public/js/app.js',
  'public/js/pages.js',
  'public/js/config.js',
  'public/js/jalali.js',
  'public/js/validators.js',
  'public/js/svg-export.js',
  'public/assets/IRANSans.ttf',

  /* the only documents a laptop owner needs */
  'START-HERE.fa.md',
  'LAPTOP.fa.md',
  'TELEGRAM-CONTROL.fa.md'
];

/* Kept in the repository, deliberately not in the laptop package. */
const EXCLUDED = [
  ['تهیه کارنامه نواحی.xlsx', 'spreadsheet from an earlier project; nothing reads it'],
  ['گزارش شهریور ماه 1405 ناحیه.xlsx', 'spreadsheet from an earlier project; nothing reads it'],
  ['result.json', '2.3 MB leftover output of an older run'],
  ['photo_2026-08-29_10-31-59.jpg', 'stray photo; the panel never loads it'],
  ['horse-4-svgrepo-com.svg', 'stray logo; the panel never loads it'],
  ['IRANSans.ttf', 'duplicate of public/assets/IRANSans.ttf, which is the one the CSS loads'],
  ['gas/', 'Google Apps Script backup target, only used by the Cloudflare deploy'],
  ['wrangler.toml', 'Cloudflare Workers config; the laptop runtime does not read it'],
  ['.github/', 'CI workflow that deploys to Cloudflare'],
  ['dev-preview.mjs', 'developer preview harness'],
  ['tests/', 'test suite; not needed to run or to install'],
  ['GUIDE.fa.md', 'Cloudflare deploy guide'],
  ['SILLYDEV-DEPLOY.fa.md', 'hosting-provider guide'],
  ['AUTOPILOT.fa.md', 'reference material, kept in the repo'],
  ['TELEGRAM-TEST-SETUP.fa.md', 'one-off testing note'],
  ['CLAUDE.md', 'notes for coding assistants']
];

const missing = FILES.filter(file => !fs.existsSync(path.join(ROOT, file)));
if (missing.length) {
  console.error('refusing to build: these files are listed but absent:');
  for (const file of missing) console.error(`  ${file}`);
  process.exit(1);
}

/*
 * Guard against the failure mode this list invites: a module reached through a
 * dynamic import (or a file added later) is invisible to a static scan and
 * missing from the package, and the break only shows up when that feature is
 * used. Compare the packaged imports against what is on disk and noisily warn.
 */
function reachableModules() {
  const seen = new Set();
  const queue = FILES.filter(file => file.endsWith('.mjs') || file.endsWith('.js'));
  while (queue.length) {
    const file = queue.shift();
    if (seen.has(file)) continue;
    seen.add(file);
    const source = fs.readFileSync(path.join(ROOT, file), 'utf8');
    /* Both `import x from './y.mjs'` and `await import('./y.mjs')` land here. */
    for (const match of source.matchAll(/['"](\.{1,2}\/[^'"]+\.m?js)['"]/g)) {
      const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(file), match[1]));
      if (!seen.has(resolved)) queue.push(resolved);
    }
  }
  return seen;
}

const reachable = reachableModules();
const unshipped = [...reachable].filter(file => !FILES.includes(file));
if (unshipped.length) {
  console.error('warning: these modules are imported but not in the package:');
  for (const file of unshipped) console.error(`  ${file}`);
}

const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-zip-'));
try {
  for (const file of FILES) {
    const target = path.join(stage, TOP, file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(path.join(ROOT, file), target);
  }
  fs.rmSync(OUT, { force: true });
  execFileSync('zip', ['-q', '-r', '-X', OUT, TOP], { cwd: stage });
} finally {
  fs.rmSync(stage, { recursive: true, force: true });
}

const kb = Math.round(fs.statSync(OUT).size / 1024);
console.log(`built ${path.relative(process.cwd(), OUT)} — ${FILES.length} files, ${kb} KB`);
console.log('');
console.log('left out of the laptop package (still in the repository):');
for (const [name, why] of EXCLUDED) console.log(`  ${name}  —  ${why}`);
