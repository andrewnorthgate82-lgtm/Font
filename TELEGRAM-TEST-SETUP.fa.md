# راه‌اندازی تلگرام آزمایشی — مسیر امن و دستی

این مسیر برای **یک کانال تلگرام آزمایشی** است. اجرای Workflow هیچ پست یا پیام تلگرامی ارسال نمی‌کند؛ فقط D1، Worker، Pages، اتصال بات و Webhook را آماده می‌کند. اولین انتشار واقعی به تأیید جداگانهٔ مالک نیاز دارد.

## ۱) آماده‌سازی تلگرام

1. در Telegram برای **@BotFather** دستور `/newbot` را بزنید و نامی مشخص مانند «Nasra Test Bot» انتخاب کنید.
2. Tokenی که BotFather می‌دهد فقط در GitHub Secret با نام `TG_BOT_TOKEN` قرار می‌گیرد؛ آن را در گفتگو، فایل پروژه یا اسکرین‌شات نگذارید.
3. بات تازه را در Telegram باز کنید و با همان حساب مالک `/start` را بفرستید. Workflow از این پیام، Chat ID مالک را پیدا می‌کند.
4. یک **کانال آزمایشی** بسازید؛ نه کانال واقعی مخاطبان.
5. بات را Administrator کانال کنید و اجازهٔ **Post Messages** را بدهید. Workflow از رویداد افزودن بات، Chat ID کانال را می‌خواند و دسترسی ادمین را با `getChat` و `getChatMember` بررسی می‌کند.

اگر بات قبلاً چند مالک یا چند کانال داشته باشد، تشخیص خودکار برای جلوگیری از انتخاب اشتباه متوقف می‌شود. در آن حالت فقط `TG_OWNER_CHAT_ID` و `TELEGRAM_CHANNEL_ID` را به‌صورت صریح در GitHub Secrets اضافه کنید؛ شناسه را حدس نزنید.

## ۲) ساخت Cloudflare API Token

1. وارد Cloudflare Dashboard شوید و بخش **My Profile → API Tokens → Create Token → Custom Token** را باز کنید.
2. برای Account مورد استفاده، این Permissionها را بگذارید:
   - **Account → Workers Scripts → Edit**
   - **Account → D1 → Edit**
   - **Account → Cloudflare Pages → Edit**
3. در Account Resources فقط همان Account را انتخاب کنید و Token را بسازید.
4. Token را مستقیم در GitHub Secret با نام `CLOUDFLARE_API_TOKEN` ذخیره کنید.
5. `CLOUDFLARE_ACCOUNT_ID` را از Account همان داشبورد کپی و در GitHub Secret جدا قرار دهید.

## ۳) Secretهای لازم در GitHub

در Repository به **Settings → Secrets and variables → Actions → New repository secret** بروید و این چهار نام را بسازید:

| نام | مقدار |
|---|---|
| `CLOUDFLARE_API_TOKEN` | Token محدود به Account و Permissionهای بالا |
| `CLOUDFLARE_ACCOUNT_ID` | Account ID همان Cloudflare |
| `OWNER_PASSWORD` | رمز قوی و تازهٔ ورود مالک؛ نه رمز Preview و نه رمز تکراری |
| `TG_BOT_TOKEN` | Token رسمی BotFather برای بات آزمایشی |

`SESSION_SECRET` و `TG_SECRET` در اولین اجرا به‌صورت تصادفی ساخته می‌شوند و در اجراهای بعدی حفظ می‌شوند؛ مقدار آن‌ها در لاگ چاپ نمی‌شود.

## ۴) فعال‌کردن و اجرای Workflow

GitHub برای اجرای دستی، فایل `workflow_dispatch` را روی شاخهٔ پیش‌فرض لازم دارد. بنابراین ابتدا Pull Request نسخهٔ بررسی‌شده به `main` ادغام می‌شود؛ سپس:

1. **Actions → Deploy Nasra to Cloudflare → Run workflow** را باز کنید.
2. اجرا را آغاز و پایان مراحل را تماشا کنید. هیچ Tokenی وارد Log نکنید.
3. پس از موفقیت، URL رابط Pages و Worker در Summary نمایش داده می‌شود.

Workflow به‌صورت idempotent عمل می‌کند:

- D1 و Pages موجود را دوباره نمی‌سازد؛
- `schema.sql` را بدون حذف داده اعمال می‌کند؛
- Secret تصادفی موجود را نمی‌چرخاند؛
- Chat ID و دسترسی ادمین کانال را بررسی می‌کند؛
- Webhook با `TG_SECRET` ثبت می‌شود؛
- Frontend فقط Origin عمومی Worker را می‌گیرد، نه Secret؛
- **هیچ `sendMessage`، `sendPhoto` یا انتشار دیگری انجام نمی‌شود.**

## ۵) بعد از اتصال موفق

ابتدا ورود، سلامت D1، Selftest و دستورهای مالک `/status`، `/today`، `/queue` و `/inbox` بررسی می‌شود. سپس یک محتوای آزمایشی جدا ساخته می‌شود و هر سه Gate طی می‌شوند. **ارسال اولین پست آزمایشی فقط پس از تأیید صریح مالک انجام خواهد شد.**

اگر مرحله‌ای خطا داد، متن خطای عمومی را بدون Token، رمز یا URL حاوی Token گزارش کنید و اجرای مکرر انجام ندهید.
