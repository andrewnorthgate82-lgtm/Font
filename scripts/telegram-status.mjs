#!/usr/bin/env node
/*
 * Read-only report on the Telegram bot configured in .env.
 *
 *   npm run telegram:status
 *
 * Answers the only three questions that matter when the bot does not reply:
 *   - is the token valid, and which bot is it?
 *   - does this bot have a webhook set (which blocks getUpdates)?
 *   - is TG_OWNER_CHAT_ID the chat the owner is actually writing from?
 *
 * It NEVER changes anything: getMe and getWebhookInfo are both reads. Deleting a
 * webhook can break whatever else shares this token, so that stays a decision the
 * owner makes in BotFather, not something a status command does for them.
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { readConfig, loadEnvFiles } from '../server/env.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
loadEnvFiles(ROOT, '.env');
const config = readConfig(process.env, { rootDir: ROOT });

const say = (t = '') => console.log(t);
const token = process.env.TG_BOT_TOKEN || '';
const base = config.telegramBaseUrl;

async function call(method) {
  const response = await fetch(`${base}/bot${token}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
    signal: AbortSignal.timeout(15000)
  });
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok && data.ok !== false, status: response.status, data };
}

say('');
say('  وضعیت ربات تلگرام');
say('');

if (!token) {
  say('  ✗ TG_BOT_TOKEN تنظیم نشده است.');
  say('');
  say('    راه‌حل: npm run setup');
  say('');
  process.exit(1);
}

/* Never print the token itself, only a shape that helps tell two bots apart. */
say(`  توکن: ذخیره شده (${token.length} کاراکتر، پایان …${token.slice(-4)})`);

try {
  const me = await call('getMe');
  if (!me.ok) {
    say(`  ✗ تلگرام این توکن را نپذیرفت (HTTP ${me.status}).`);
    say('');
    say('    یعنی توکن اشتباه است یا باطل شده. در BotFather: /mybots → API Token');
    say('');
    process.exit(1);
  }
  const bot = me.data.result || {};
  say(`  ✓ ربات: @${bot.username || '?'}  (${bot.first_name || ''})`);
  if (bot.is_bot !== true) say('  ! این توکن مربوط به یک ربات نیست.');
} catch (error) {
  say(`  ✗ اتصال به تلگرام برقرار نشد: ${error.message}`);
  say('');
  say('    اینترنت یا فیلترشکن را بررسی کنید. تلگرام باید از این دستگاه در دسترس باشد.');
  say('');
  process.exit(1);
}

const ownerChat = process.env.TG_OWNER_CHAT_ID || '';
if (!ownerChat) {
  say('  ✗ TG_OWNER_CHAT_ID تنظیم نشده است → ربات به هیچ پیامی جواب نمی‌دهد.');
  say('');
  say('    شناسه خودتان را از @userinfobot بگیرید و دوباره npm run setup را اجرا کنید.');
} else {
  say(`  ✓ شناسه مالک: ${ownerChat}`);
}

try {
  const info = await call('getWebhookInfo');
  const hook = info.data.result || {};
  if (hook.url) {
    say('');
    say('  ⚠ این ربات webhook فعال دارد:');
    say(`      ${String(hook.url).slice(0, 80)}`);
    say('');
    say('    تا وقتی این webhook هست، تلگرام پیام‌ها را به آن آدرس می‌فرستد و');
    say('    getUpdates (که برای این پروژه لازم است) کار نمی‌کند.');
    say('');
    say('    اگر این ربات جای دیگری هم استفاده می‌شود، آن را دست نزنید.');
    say('    راه امن: در BotFather یک ربات تازه بسازید (/newbot) و توکن آن را بگذارید.');
    say('    اگر مطمئنید این ربات فقط برای نسراست:');
    say('      BotFather → /mybots → ربات → Bot Settings → Delete Webhook');
    say('');
  } else {
    say('  ✓ webhook فعال نیست (لازمهٔ کار polling)');
    if (Number(hook.pending_update_count || 0) > 0) {
      say(`  ! ${hook.pending_update_count} پیام در صف تلگرام مانده است.`);
    }
    if (hook.last_error_message) {
      say(`  ! آخرین خطای تلگرام: ${String(hook.last_error_message).slice(0, 120)}`);
    }
  }
} catch (error) {
  say(`  ! وضعیت webhook خوانده نشد: ${error.message}`);
}

say('');
say('  نکته: این دستور فقط می‌خواند؛ هیچ تنظیمی در تلگرام تغییر نمی‌دهد.');
say('');
say('  تست واقعی: سرور را اجرا کنید (npm start) و در تلگرام /ping بفرستید.');
say('');
