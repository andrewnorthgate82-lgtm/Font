# CLAUDE.md — Nasra Esfahan Media Studio

راهنمای کاری برای هر عامل هوش مصنوعی (Claude Code یا مشابه) که در این مخزن کار می‌کند.

## این پروژه چیست

استودیوی رسانه‌ای «نسرا اصفهان»: یک Worker سازگار با Cloudflare در `worker.js` و یک
زمان‌اجر خودمیزبان Node 22 + SQLite در `server/`. همان `worker.js` در هر دو محیط
بدون تغییر اجرا می‌شود؛ تفاوت فقط در لایه‌ای است که D1 و fetch را تأمین می‌کند.

## قواعد غیرقابل‌مذاکره

این‌ها را نقض نکنید، حتی اگر کار را ساده‌تر می‌کردند:

1. **هرگز `git clean` یا `git reset --hard` اجرا نکن.** داده‌های محلی و `.env` با
   یک دستور از بین می‌روند.
2. **هرگز روی `wrangler.toml`، پوشه `.github/workflows` یا مسیر Cloudflare کار نکن**
   مگر اینکه صریحاً خواسته شود. این نسخه فقط از نظر محلی/خودمیزبان تست می‌شود.
3. **هرگز `setWebhook` یا `deleteWebhook` صدا نزن.** ربات تلگرام از قبل وجود دارد و
   این پروژه به آن دست نمی‌زند. `TG_BOT_TOKEN` را در `.env` نساز و در چت نگذار.
4. **کلیدهای API را در چت، کد یا لاگ نگذار.** فقط در `.env` روی دستگاه خودِ کاربر.
5. **`PUBLISH_GUARD_MODE` پیش‌فرض `dry_run` است.** آن را `live` نکن مگر کاربر صریحاً
   بخواهد و `PUBLISH_ALLOWLIST` پر باشد.
6. **`ctx.waitUntil` را رها نکن.** در این زمان‌اجر، وعده‌های ثبت‌شده باید await شوند،
   وگرنه انتشار/راست‌آزمایی نیمه‌کاره می‌ماند.

## نقشهٔ فایل‌ها

| مسیر | کار |
|---|---|
| `worker.js` | کل API، ایجنت‌ها، گیت‌ها، انتشار. ~۲۸۰۰ خط. تنها منبع منطق کسب‌وکار. |
| `server/index.mjs` | نقطه ورود خودمیزبان: DB، گارد انتشار، زمان‌بند، poller، اتوپایلوت |
| `server/env.mjs` | خواندن `.env`، `readConfig()`، `validateConfig()`، خلاصهٔ امن |
| `server/d1-sqlite.mjs` | آداپتور D1 روی `node:sqlite` |
| `server/http-server.mjs` | سرور HTTP، کوکی، CSRF، سرو `/media` |
| `server/autopilot.mjs` | `normalizePolicy()`، `gatePolicy()`، کلاس `Autopilot` |
| `server/image-provider.mjs` | تولید پوستر (فقط `gemini`) |
| `server/png-to-jpeg.mjs` | مبدل PNG→JPEG بدون وابستگی |
| `server/media-store.mjs` | ذخیره/سرو/prune رسانه محلی |
| `server/publish-guard.mjs` | رهگیری fetch و جلوگیری از انتشار واقعی |
| `server/scheduler.mjs` | زمان‌بند انتشار |
| `server/telegram-poller.mjs` | long polling تلگرام (بدون نیاز به IP عمومی) |
| `scripts/setup-env.mjs` | ساخت `.env` با رمز و Secret تصادفی |
| `scripts/doctor.mjs` | بررسی پیش از اجرا (نسخه Node، sqlite، پوشه‌ها، بندر) |
| `scripts/start-nasra.mjs` | اجرای یک‌دستوری با باز کردن مرورگر |
| `scripts/local-https.mjs` | پروکسی HTTPS محلی برای تست لینک رسانه |
| `public/` | پنل (HTML/CSS/JS بدون build step) |
| `tests/` | `node --test` — بدون فریمورک |

## دستورهای روزمره

```
npm run doctor    # قبل از هر چیز؛ اگر «آماده اجراست» نگفت، سرور بالا نمی‌آید
npm start         # اجرای واقعی
npm run dev       # اجرای تمرینی: MOCK_AI=1، تلگرام خاموش، dry_run
npm test          # ۲۹ تست
npm run check     # node --check روی فایل‌های اصلی
```

`npm test` باید همیشه سبز بماند. تست‌ها به شبکه دست نمی‌زنند.

## ساختار داده

`schema.sql` منبع حقیقت است. جدول‌های مهم: `content_items` (با `state`)،
`gate_decisions` (۳ گیت)، `publish_jobs` (زمان‌بندی + `manual_package_json`)،
`studio_settings` (کلید/مقدار، از جمله `autopilot`)، `audit_logs`.

`state` حرکت می‌کند: `IDEA → ... → MEDIA_PENDING → READY_FOR_GATE3 → APPROVED →
SCHEDULED → PUBLISHED`؛ یا `REJECTED`/`ERROR`.

## تفاوت‌های محیط خودمیزبان

- `env.AI` و `env.R2` هستند ولی `null`؛ کد باید مسیر بدون آن‌ها را تحمل کند.
- آداپتور D1 فقط `prepare/bind/run/first/all/batch/exec` را دارد. جای دیگری متد اضافه
  نکنید.
- `node:sqlite` از Node 22.13 لازم است. `d1-sqlite.mjs` با پیام روشن شکست می‌خورد.
- رسانهٔ محلی فقط وقتی برای تلگرام/روبیکا قابل استفاده است که `PUBLIC_BASE_URL`
  با `https://` شروع شود (`MediaStore.fetchable`).

## گیت‌ها

سه گیت دستی/خودکار. `gatePolicy()` در `server/autopilot.mjs` تصمیم می‌گیرد کدام گیت
قابل تأیید خودکار است؛ آن تابع خالص است و تست دارد — منطق گیت را داخل `worker.js`
تکرار نکن.

`FACTCHECK_FAIL` و `NO_SOURCED_CLAIMS` خطا **نیستند**: گیت عمدی است. بدون
`source_items` هر محتوایی در گیت ۲/۳ متوقف می‌شود. این رفتار را «رفع» نکنید.

## سبک کار

- فارسی برای متن کاربر، انگلیسی برای کد و کامنت‌های فنی.
- بدون وابستگی جدید (zero-dep). اگر لازم شد، اول دلیلش را بنویس.
- تغییرات کوچک و قابل تست. بعد از هر تغییر منطقی، `npm test`.
- اگر چیزی را نمی‌توانی تست کنی، همان را صریح بگو؛ ادعای «کار می‌کند» بدون اجرا نکن.
- سندهای فارسی موجود (`GUIDE.fa.md`، `SILLYDEV-DEPLOY.fa.md`، `AUTOPILOT.fa.md`،
  `LAPTOP.fa.md`) را با تغییر رفتار هم‌گام نگه دار.
