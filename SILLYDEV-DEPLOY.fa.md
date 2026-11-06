# راه‌اندازی نسرا اصفهان روی SillyDev (نسخهٔ امن، Node 22)

این سند اجرای **همان استودیوی نسرا** را روی SillyDev توضیح می‌دهد: بدون Cloudflare، بدون D1، با **SQLite روی دیسک**، **Scheduler واقعی** درون پروسه و **Telegram Long Polling**. کد اصلی `worker.js` دست‌نخورده می‌ماند؛ فقط یک Entry point جدید (`server/index.mjs`) آن را روی Node 22 اجرا می‌کند.

> **مهم:** به Cloudflare، Worker فعلی، Pages و ربات مهم موجود **دست نزده‌ایم**. هیچ فایل Cloudflare تغییر نکرده و هیچ پستی در این مرحله ارسال نشده است. همهٔ آزمون‌ها محلی و بدون شبکه انجام شده‌اند.

---

## ۱) این نسخه چه چیزهایی اضافه می‌کند؟

| نیاز | راه‌حل در این نسخه | فایل |
| --- | --- | --- |
| Entry point با `SERVER_PORT` روی Node 22 | سرور HTTP روی `0.0.0.0:${SERVER_PORT}` و استفادهٔ مستقیم از `worker.fetch` | `server/index.mjs`، `server/http-server.mjs` |
| SQLite پایدار (به‌جای D1) | آداپتور D1 روی `node:sqlite` با WAL، `synchronous=NORMAL`، `busy_timeout`، retry روی BUSY و اجرای idempotent `schema.sql` در هر بوت | `server/d1-sqlite.mjs` |
| Scheduler واقعی (به‌جای Cron Trigger) | فراخوانی `worker.scheduled()` هر `SCHEDULER_INTERVAL_MS`، یک tick در هر لحظه، waitUntil با timeout، بکاپ SQLite | `server/scheduler.mjs` |
| Telegram Long Polling (بدون Webhook عمومی) | `getUpdates` با offset ماندگار در `studio_settings` و تحویل update به همان مسیر `POST /webhooks/telegram` | `server/telegram-poller.mjs` |
| «هیچ پست واقعی بدون تأیید جداگانهٔ مالک» | Publish Guard روی `fetch` + تأیید یک‌بارمصرف مالک در `POST /api/ops/approvals` | `server/publish-guard.mjs` |
| آزمون محلی بدون شبکه | `node --test tests/runtime.test.mjs` و شبیه‌ساز تلگرام `tests/fake-telegram.mjs` | `tests/` |

---

## ۲) پیش‌نیازها

- **Node.js 22.13 یا بالاتر** (برای `node:sqlite` بدون فلگ). روی Node 22.5–22.12 باید `NODE_OPTIONS=--experimental-sqlite` تنظیم شود.
- ریپوی همین پروژه (همان `Font`).
- **بدون وابستگی npm**: `dependencies` خالی است و `npm install` لازم نیست.

```bash
node -v            # باید v22.13+ باشد
node --test tests/runtime.test.mjs   # ۱۲ آزمون محلی
```

---

## ۳) متغیرهای محیطی (Panel → Startup → Environment Variables)

الگوی کامل در `.env.example` است. حداقلِ لازم:

| متغیر | الزامی | توضیح |
| --- | --- | --- |
| `SERVER_PORT` | ✅ (SillyDev می‌دهد) | پورت.listen؛ کد مقدار را از محیط می‌خواند و هیچ پورتی hard-code نشده است. |
| `OWNER_PASSWORD` | ✅ | حداقل ۱۰ کاراکتر. بدون آن سرور بالا نمی‌آید. |
| `SESSION_SECRET` | ✅ | حداقل ۳۲ کاراکتر: `openssl rand -hex 32`. |
| `DATA_DIR` | توصیه‌شده | محل SQLite و بکاپ‌ها. اگر پنل volume ماندگار می‌دهد به همان مسیر اشاره کنید. پیش‌فرض: `data/`. |
| `PUBLISH_GUARD_MODE` | ✅ | پیش‌فرض `dry_run`. تا تأیید جداگانهٔ شما همین‌طور بماند. |
| `TELEGRAM_POLLING` | اختیاری | پیش‌فرض `1`. با `0` خاموش می‌شود. |
| `TG_BOT_TOKEN` / `TG_OWNER_CHAT_ID` | برای تلگرام | توکن ربات و **آیدی عددی** مالک. فقط همین چت اجازهٔ تصمیم و دستور دارد. |
| `AI_PROVIDER` + `AI_API_KEY` | اختیاری | `gemini` یا `openai_compatible`. خالی بماند، Agentها در حالت دستی/صف می‌مانند و خطا نمی‌دهند. |

نام‌های حساس هرگز در پاسخ API، لاگ یا بکاپ چاپ نمی‌شوند؛ `/api/ops/health` فقط `true/false` برمی‌گرداند.

### اگر پنل Host را بازنویسی می‌کند
برای اینکه کوکی/CSRF و لینک‌ها دقیق باشند، می‌توانید:

```
PUBLIC_BASE_URL=https://your-app.sillydev.example
```

در غیر این صورت origin از `X-Forwarded-Host` / `X-Forwarded-Proto` بازسازی می‌شود (`TRUST_PROXY_HTTPS=1`).

---

## ۴) اجرا

در پنل SillyDev (Runtime: **Node.js 22**):

- **Start command:** `node server/index.mjs`
- یا اگر فیلد Startup Command وجود دارد: `npm start` (اسکریپت `start` همین است).

خروجی مورد انتظار در Console:

```text
نسرا اصفهان — Nasra Esfahan Media Studio (self-hosted / Node 22)
[node] v22.x · data data · port 8080
[nasra] sqlite ready: data/nasra.sqlite (WAL, foreign_keys, busy_timeout=5000ms)
[scheduler] every 5000ms (worker.scheduled handler)
[nasra] listening on http://0.0.0.0:8080 (SillyDev forwards SERVER_PORT)
[nasra] publish guard: dry_run — no real post can be sent
[telegram] webhook removed; long polling is now the update source.
```

### اجرای محلی

```bash
cp .env.example .env          # مقادیر را پر کنید (‎.env در git نیست)
NODE_ENV=development node server/index.mjs
# یا برای دیدن کل خط تولید بدون AI واقعی:
NODE_ENV=development MOCK_AI=1 node server/index.mjs
```

`MOCK_AI=1` فقط در `NODE_ENV` غیر production کار می‌کند و پاسخ Agentها یک استاب محلی و قطعی است.

---

## ۵) SQLite پایدار: چه چیزی تضمین می‌شود؟

- فایل `data/nasra.sqlite` با **WAL**؛ نوشتهٔ تأییدشده با کرش/ری‌استارت از دست نمی‌رود (`-wal` خودکار replay می‌شود).
- `PRAGMA foreign_keys=ON`، `busy_timeout` (پیش‌فرض ۵ ثانیه) و retry نمایی روی `SQLITE_BUSY`.
- `schema.sql` در هر بوت اجرا می‌شود و **idempotent** است: `CREATE ... IF NOT EXISTS` و `INSERT OR IGNORE`. نتیجه: داده یا پرامپت ویرایش‌شدهٔ شما بازنویسی نمی‌شود و فقط جدول/ایندکس/Seed تازه اضافه می‌شود.
- بکاپ خودکار: `BACKUP_INTERVAL_HOURS` (پیش‌فرض ۲۴، یعنی یک بکاپ روزانه) با `VACUUM INTO` در `data/backups/`، نگهداری `BACKUP_KEEP` نسخه. علاوه بر آن **در هر خاموشی تمیز** و با `POST /api/ops/backup` یک نسخهٔ سازگار گرفته می‌شود.
- **توجه:** دیسک ۵۱۲ مگابایتی SillyDev برای دیتابیس متنی نسرا کافی است، اما ویدیوی نهایی داخل آن ذخیره نمی‌شود؛ مدیا فقط با لینک HTTPS بسته می‌شود (همان قاعدهٔ Cloudflare).

---

## ۶) Scheduler واقعی

- هر `SCHEDULER_INTERVAL_MS` (پیش‌فرض ۵ ثانیه، معادل tick پنج‌دقیقه‌ای تولید) همان `worker.scheduled()` اجرا می‌شود؛ همهٔ گیت‌های ساعتی تهران با `cronOnce` idempotent هستند، پس فرکانس بیشتر داده تکراری نمی‌سازد.
- **هرگز دو tick هم‌زمان** اجرا نمی‌شود؛ اگر tick قبلی تمام نشده باشد، بعدی skip و شمرده می‌شود (`skipped_overlap`).
- `waitUntil`های Worker با timeout پوشش داده می‌شوند تا کار پس‌زمینه بی‌صدا گم نشود.
- وضعیت: `GET /api/ops/scheduler` و شمارنده‌ها در `GET /api/ops/health`.
- اجرای دستی یک tick: `POST /api/ops/scheduler/tick` (نیازمند نشست مالک + CSRF).

---

## ۷) Telegram Long Polling

- با استارت، `deleteWebhook` صدا زده می‌شود (تلگرام با webhook فعال، `getUpdates` را ۴۰۹ می‌دهد). اگر می‌خواهید webhook فعلی دست‌نخورده بماند: `TELEGRAM_DELETE_WEBHOOK=0` → در این حالت polling خاموش می‌ماند.
- `offset` در `studio_settings` ذخیره می‌شود؛ ری‌استارت نه update تکراری می‌سازد و نه update جامانده را از دست می‌دهد (dedupe خودِ Worker با `tg:<update_id>` لایهٔ دوم است).
- updateها به همان مسیر مشترک `POST /webhooks/telegram` تحویل داده می‌شوند؛ یعنی منطق تأیید گیت، دکمه‌ها و دستورهای `/status /today /queue /inbox` **دقیقاً** مثل نسخهٔ Cloudflare است.
- فقط چت `TG_OWNER_CHAT_ID` پاسخ می‌گیرد؛ پیام سایرین ignore می‌شود (آزمون خودکار دارد).
- توکن نامعتبر (۴۰۱) باعث توقف polling می‌شود، نه حلقهٔ خطا.

---

## ۸) لایهٔ ایمنی انتشار: «هیچ پستی بدون تأیید جداگانه»

`worker.js` بدون تغییر از `fetch` استفاده می‌کند. Entry point جدید، `fetch` را برای طول عمر پروسه می‌پوشاند و **فقط** درخواست‌های انتشار را بررسی می‌کند:

| مقصد | مثال | در حالت `dry_run` |
| --- | --- | --- |
| انتشار اینستاگرام | `POST /{ig-user}/media_publish` | ✋ مسدود |
| ارسال به کانال تلگرام | `sendMessage/sendPhoto/sendVideo/sendMediaGroup/...` | ✋ مسدود |
| آلبوم/فایل روبیکا | `sendFile`, `sendMessage` | ✋ مسدود |
| خواندن‌ها | `getMe`, `getUpdates`, `GET /{id}?fields=` | ✅ آزاد |
| اعلان به خود مالک | `sendMessage` به `TG_OWNER_CHAT_ID` | ✅ آزاد (پست عمومی نیست) |
| AI / رسانه / RSS | Gemini، OpenAI، دانلود فایل | ✅ آزاد |

دو حالت:

1. **`PUBLISH_GUARD_MODE=dry_run` (پیش‌فرض):** درخواست انتشار هرگز از پروسه بیرون نمی‌رود؛ Worker یک پاسخ ۴۰۳ استاب می‌گیرد و کار به `MANUAL_READY` (بستهٔ دستی) می‌رود. یعنی «هیچ پست واقعی» و در عین حال قابل مشاهده بودن جریان.
2. **`PUBLISH_GUARD_MODE=live`:** علاوه بر `PUBLISH_ALLOWLIST` (آیدی چت/کانال مجاز)، هر انتشار به یک **تأیید یک‌بارمصرف و کوتاه‌عمر** مالک نیاز دارد:

```bash
# ۱) مالک وارد استودیو می‌شود و از صفحه، csrf_token می‌گیرد
# ۲) تأیید جداگانه صادر می‌کند (پیش‌فرض ۱۰ دقیقه اعتبار)
curl -X POST https://APP/api/ops/approvals \
  -H 'Content-Type: application/json' \
  -H "X-CSRF-Token: $CSRF" \
  -b cookies.txt \
  -d '{"note":"تأیید ارسال تست کانال تست","platform":"telegram","destination":"-1001234567890","ttl_seconds":600}'
```

- تأییدها در **حافظه** نگه داشته می‌شوند: ری‌استارت = همهٔ تأییدها باطل.
- هر تأیید فقط **یک** درخواست انتشار را مجاز می‌کند، به همان platform/action/destination.
- دیدن وضعیت و لاگ آخرین تلاش‌ها: `GET /api/ops/guard` — لغو همه: `DELETE /api/ops/approvals`.

> برای فعال‌کردن `live` **دو کار لازم است**: (۱) تغییر متغیر محیطی، (۲) صدور تأیید برای همان ارسال. تا زمانی که شما این دو را انجام نداده‌اید، حتی با توکن واقعی هم پستی ارسال نمی‌شود.

---

## ۹) آزمون محلی (بدون Cloudflare، بدون شبکه)

```bash
node --test tests/runtime.test.mjs
```

۱۲ آزمون:

1. طبقه‌بندی درخواست‌های انتشار (اینستاگرام/تلگرام/روبیکا) و عبور آزاد خواندن‌ها.
2. `dry_run` همهٔ انتشارها را می‌بندد و اعلان مالک را رد نمی‌کند.
3. `live` بدون allowlist/تأیید کار نمی‌کند و تأیید یک‌بارمصرف است.
4. انقضای تأیید و رد platform ناهمخوان.
5. SQLite: ماندگاری پس از reopen، WAL، اجرای دوبارهٔ schema بدون داده تکراری.
6. Splitter دستورات SQL (کوت، کامنت، تریگر).
7. Scheduler: بدون هم‌پوشانی، skip شدن tick هم‌زمان، خطا به حلقه نشت نمی‌کند.
8. بکاپ `VACUUM INTO` قابل بازکردن و شامل Seedها.
9. سرور HTTP: `/api/health`، فایل استاتیک، `/api/ops/guard`، جلوگیری از path traversal، ۴۰۱ ناشناس، Login + CSRF، و **۱۶/۱۶ selftest خود Worker**.
10. اجرای کامل یک محتوا در dry_run: هیچ `PUBLISHED` ساخته نمی‌شود.
11. Long polling با **تلگرام تقلبی محلی**: حذف webhook، ماندگاری offset، پاسخ فقط به چت مالک، نادیده‌گرفتن غریبه.
12. اعتبارسنجی env: رد رمز ضعیف، رد `live` بدون allowlist، رد `TELEGRAM_BASE_URL` غیر HTTPS.

اجرای دستی شبیه‌ساز تلگرام:

```bash
node tests/fake-telegram.mjs
# سپس:
TELEGRAM_BASE_URL=http://127.0.0.1:PORT ALLOW_INSECURE_TELEGRAM_BASE=1 TELEGRAM_POLLING=1 node server/index.mjs
```

---

## ۱۰) مسیرهای جدید (فقط مالک، با CSRF)

| متد و مسیر | کار |
| --- | --- |
| `GET /api/ops/health` | وضعیت runtime، SQLite، Scheduler، Polling، Guard (با `HEALTH_TOKEN` هم قابل خواندن) |
| `GET /api/ops/guard` | حالت، allowlist، تأییدهای فعال، آمار و ۴۰ تلاش آخر |
| `POST /api/ops/guard/check` | پیش از هر اقدام: «آیا این درخواست انتشار است؟» |
| `POST /api/ops/approvals` | صدور تأیید یک‌بارمصرف |
| `GET/DELETE /api/ops/approvals` | مشاهده/لغو تأییدها |
| `POST /api/ops/backup` | بکاپ فوری SQLite |
| `GET /api/ops/scheduler` / `POST /api/ops/scheduler/tick` | وضعیت و اجرای دستی یک tick |
| `GET /readyz` | liveness ساده |

همهٔ مسیرهای موجود استودیو (`/api/...`) بدون تغییر کار می‌کنند، چون از همان Worker می‌آیند.

---

## ۱۱) عیب‌یابی

| نشانه | علت رایج | راه‌حل |
| --- | --- | --- |
| `ExperimentalWarning: SQLite` | Node نسخهٔ ۲۲.۵–۲۲.۱۲ | به Node 22.13+ بروید یا `NODE_OPTIONS=--experimental-sqlite` بگذارید. |
| `node:sqlite is unavailable` | Node < 22.5 | Runtime پنل را روی Node 22 تنظیم کنید. |
| `configuration is incomplete` | `OWNER_PASSWORD`/`SESSION_SECRET` خالی یا کوتاه | مقادیر را در Panel → Startup وارد کنید. |
| `live requires PUBLISH_ALLOWLIST` | `PUBLISH_GUARD_MODE=live` بدون allowlist | آیدی کانال/چت را اضافه کنید یا به `dry_run` برگردید. |
| polling خطای ۴۰۹ | webhook فعال یا نمونهٔ دیگری همان ربات را poll می‌کند | `TELEGRAM_DELETE_WEBHOOK=1` و مطمئن شوید نسخهٔ Cloudflare/webhook دیگری با همان توکن کار نمی‌کند. |
| polling خطای ۴۰۱ | توکن نامعتبر | توکن را عوض کنید؛ polling خودکار می‌ایستد. |
| `کد ۵۰۳ NOT_CONFIGURED` در Publish | اعتبارنامه/کانال تنظیم نشده | این خطای طراحی‌شده است: بستهٔ دستی ساخته می‌شود و پستی ارسال نمی‌شود. |
| داده پس از redeploy نیست | `DATA_DIR` روی دیسک موقت است | `DATA_DIR` را روی volume ماندگار پنل بگذارید. |
| همهٔ درخواست‌ها ۴۰۱ | Host پشت پروکسی تغییر می‌کند | `PUBLIC_BASE_URL` را تنظیم کنید. |
| `no usable AI provider` | `AI_PROVIDER`/`AI_API_KEY` خالی | طبیعی است؛ Agentها در صف/دستی می‌مانند. |

---

## ۱۲) چک‌لیست پیش از هر انتشار واقعی (بعد از تأیید جداگانهٔ شما)

- [ ] `node --test tests/runtime.test.mjs` سبز است (۱۲/۱۲) و selftest خود Worker ۱۶/۱۶.
- [ ] `GET /api/ops/health` سالم است و `publish_guard.mode` همان است که انتظار دارید.
- [ ] `TG_OWNER_CHAT_ID` عددی و فقط مالک است؛ `/status` پاسخ می‌دهد.
- [ ] `DATA_DIR` ماندگار است و یک بکاپ تازه گرفته شده (`POST /api/ops/backup`).
- [ ] برای ارسال واقعی: `PUBLISH_GUARD_MODE=live` + `PUBLISH_ALLOWLIST` فقط با **کانال تست**.
- [ ] تأیید یک‌بارمصرف برای همان ارسال صادر شده و TTL کوتاه است.
- [ ] پس از ارسال، `GET /api/ops/guard` و `/api/publish` وضعیت را تأیید می‌کنند.
- [ ] Cloudflare/ربات مهم فعلی در این مسیر دخیل نیستند.

---

## ۱۳) تغییرات نسبت به نسخهٔ Cloudflare (خلاصه)

| فایل | وضعیت |
| --- | --- |
| `worker.js` | **بدون تغییر** (همان کد Cloudflare) |
| `wrangler.toml`، `.github/workflows/deploy.yml`، `scripts/deploy-cloudflare.sh`، `public/` | **بدون تغییر** |
| `server/*` | جدید: Entry point، آداپتور SQLite، Scheduler، Polling، Guard، استاب AI |
| `tests/*` | جدید: آزمون‌های محلی و تلگرام تقلبی |
| `.env.example`، `package.json`، این سند | جدید |
| `.gitignore` | فقط `data/`, `*.sqlite*` اضافه شد |
