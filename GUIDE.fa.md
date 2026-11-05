# راهنمای راه‌اندازی «استودیو رسانه‌ای نسرا اصفهان»

**نسخه راهنما:** ۱٫۰٫۰ — **تاریخ بررسی مستندات:** ۲۰۲۶-۱۱-۰۴ (۱۴۰۵/۰۸/۱۳)  
**مخاطب راهنما:** مالک غیربرنامه‌نویس؛ همه مراحل production با مرورگر انجام می‌شود.

## فرض‌ها و تصمیم‌های امن پیش‌فرض

- مخاطب، فارسی‌زبانان عمومی و شهروندان اصفهان از نوجوان تا بزرگسال فرض شده‌اند. لحن پیش‌فرض آموزشی، صمیمی، محترمانه و فراجناحی است و از Playbook قابل ویرایش است.
- مسیر پیش‌فرض توکن اینستاگرام **Instagram Login** با `graph.instagram.com` و Graph API `v26.0` است. مسیر **Facebook Login** با `graph.facebook.com` نیز در Settings و adapter پشتیبانی می‌شود؛ permissionها و روش تازه‌سازی دو مسیر یکسان نیستند.
- هیچ URL واقعی برای Sources ساخته نشده است. قالب Sources خالی است و کانال تلگرامی گزارش کلاس‌های خودتان را می‌توانید با شناسه واقعی خودتان اضافه کنید.
- فایل‌ها پیش‌فرض روی هاست/درایو شما و به شکل **لینک HTTPS عمومی و مستقیم** هستند. لینک صفحه اشتراک‌گذاری Google Drive به‌جای لینک دانلود مستقیم برای Meta مناسب نیست. R2 اختیاری و پشت flag است.
- ساعت‌های پیش‌فرض `13:30` و `21:00` فقط fallback تقویم‌اند، نه ادعای الگوریتم. بدون داده Insights، پیشنهاد زمان‌بندی «داده کافی نیست» است. همه زمان‌های ذخیره‌شده UTC و همه نمایش‌ها Asia/Tehran و جلالی‌اند.
- نبود هر Secret فقط همان سرویس را به «not configured» و Manual Package می‌برد؛ بقیه برنامه و Selftest کار می‌کند. هیچ Agent ابزار، دسترسی انتشار یا اجازه پاسخ مستقل ندارد.
- `dev-preview.mjs` فقط برای پیش‌نمایش فنی محلی است و در راه‌اندازی production لازم نیست. Mock AI هم فقط در همان preview است؛ در Worker production خروجی مدل واقعی یا حالت not configured وجود دارد.

---

## ۱) درخت فایل‌ها

```text
Font/
├── schema.sql                 # ساختار و Seed کامل D1؛ idempotent
├── worker.js                  # تنها Worker؛ API، cron، AI، webhook و adapterها
├── wrangler.toml              # مانیفست اختیاری؛ برای paste در Dashboard لازم نیست
├── dev-preview.mjs            # پیش‌نمایش صفر وابستگی با node:sqlite؛ فقط آزمون فنی
├── GUIDE.fa.md                # همین راهنما
├── gas/
│   ├── Code.gs                # Mirror و Snapshot در Google Sheets
│   └── appsscript.json        # Manifest با Asia/Tehran
└── public/                    # Frontend استاتیک روی Cloudflare Pages
    ├── index.html
    ├── styles.css
    ├── _headers               # CSP و Security Headers
    ├── assets/IRANSans.ttf    # کپی فونت آپلودشده برای Frontend
    └── js/
        ├── app.js             # نشست، CSRF، Router و API client
        ├── pages.js           # هر ۱۶ صفحه RTL فارسی
        ├── config.js          # فقط origin عمومی API؛ بدون Secret
        ├── jalali.js          # تبدیل جلالی/میلادی و تقویم تهران
        ├── validators.js      # اعتبارسنجی‌های سمت مرورگر
        └── svg-export.js      # SVG امن و خروجی JPEG سایز ۱۰۸۰×۱۳۵۰
```

**نکته:** فایل‌های قبلی مخزن تغییر نکرده‌اند. برای نمایش فارسی، یک کپی از `IRANSans.ttf` در `public/assets/` قرار گرفته است؛ تصاویر و سایر دارایی‌های آپلودشده دست‌نخورده باقی مانده‌اند.

---

## ۲) اعداد راستی‌آزمایی‌شده و بودجه Free Tier

این اعداد در تاریخ بالا از مستندات رسمی بررسی شده‌اند. Limits ممکن است تغییر کنند؛ پیش از راه‌اندازی مجدد لینک‌های همان ردیف را باز کنید. طراحی صف‌ها محافظه‌کارانه است و این اعداد را مجوز مصرف کامل نمی‌داند.

| سرویس | حد/قرارداد بررسی‌شده | تصمیم در نسرا | منبع رسمی |
|---|---|---|---|
| Workers Free | ۱۰۰٬۰۰۰ درخواست/روز؛ ۱۰ms CPU per invocation؛ ۵۰ subrequest خارجی؛ ۱۲۸MB حافظه؛ ۶ اتصال خروجی هم‌زمان | Webhook سریع ACK می‌دهد؛ کار سنگین در صف و `waitUntil` است | [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) |
| Workers Cron | ۵ Cron Trigger در حساب؛ Cron wall-clock تا ۱۵ دقیقه؛ `waitUntil` پس از پاسخ تا ۳۰ ثانیه | فقط یک Trigger `*/5 * * * *`؛ ساعت‌های تهران با slot idempotent در D1 | [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) |
| D1 Free | ۱۰ دیتابیس؛ ۵۰۰MB/DB و ۵GB کل؛ ۵M خواندن و ۱۰۰k نوشتن ردیف/روز؛ ۵۰ کوئری/invocation؛ ۲MB/ردیف؛ ۱۰۰KB/SQL؛ ۱۰۰ bind parameter | Jobها کوچک‌اند؛ UI Selftest در پنج invocation جدا اجرا می‌شود؛ export حجم‌دار را محدود کنید | [D1 limits](https://developers.cloudflare.com/d1/platform/limits/) |
| D1 نتایج و بازیابی | ۵۰MB حد نتیجه کوئری و ۷ روز Time Travel | JSON Backup مستقل از Time Travel و بدون Secrets | [D1 limits](https://developers.cloudflare.com/d1/platform/limits/) |
| Pages Free | ۱ build هم‌زمان؛ ۵۰۰ build/ماه؛ ۲۰٬۰۰۰ فایل؛ ۲۵MiB/فایل؛ ۱۰۰ دامنه سفارشی | Vanilla static، بدون build و بدون npm | [Pages limits](https://developers.cloudflare.com/pages/platform/limits/) |
| Workers AI | ۱۰٬۰۰۰ Neurons رایگان/روز؛ Neuron با Token یکسان نیست | سقف داخلی Token تقریبی و روزانه است؛ مصرف واقعی را در Cloudflare ببینید | [Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/) |
| Gemini Free | RPM/TPM/RPD به مدل و پروژه وابسته است؛ RPD نیمه‌شب Pacific reset می‌شود | عدد ثابت جعلی درج نشده؛ خطای 429 به retry-later می‌رود؛ سقف داخلی UTC محافظ اضافی است | [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) |
| Instagram Publishing | حداکثر ۱۰۰ پست API در ۲۴ ساعت غلتان؛ ۴۰۰ container در ۲۴ ساعت؛ container پس از ۲۴ ساعت منقضی می‌شود | پیش از هر container، `content_publishing_limit` خوانده می‌شود؛ اتمام سهمیه = `QUEUED_LIMIT` | [Content Publishing](https://developers.facebook.com/documentation/instagram-platform/content-publishing) |
| Instagram Image | فقط JPEG برای API؛ تا ۸MB؛ نسبت ۰٫۸ تا ۱٫۹۱؛ عرض ۳۲۰ تا ۱۴۴۰ پیکسل؛ کپشن ۲۲۰۰؛ حداکثر ۳۰ هشتگ API و ۲۰ منشن؛ Alt تا ۱۰۰۰ | Validator پیش از Gate 3؛ پیشنهاد تحریریه ۳ تا ۵ هشتگ مرتبط است | [IG media reference](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user/media) |
| Instagram Carousel | مرجع API فعلی تا ۲۰ فرزند را می‌پذیرد؛ استودیو محافظه‌کارانه ۲ تا ۱۰ اسلاید و همگن می‌گیرد؛ Reel داخل Carousel نه | قانون Studio سخت‌گیرانه‌تر از سقف API است و برای جلوگیری از برش/خطا قابل تنظیم است | [Content Publishing](https://developers.facebook.com/documentation/instagram-platform/content-publishing) |
| Reels | MP4/MOV؛ H264/HEVC و AAC؛ ۳ ثانیه تا ۹۰۰ ثانیه؛ تا ۳۰۰MB؛ ۲۳–۶۰fps؛ عرض حداکثر ۱۹۲۰؛ نسبت پیشنهادی ۹:۱۶ | Container ذخیره و `POLLING_CONTAINER` در Cron بعدی ادامه می‌یابد | [Reel specifications](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user/media#reel-specifications) |
| Stories | JPEG تا ۸MB یا MP4/MOV بین ۳ تا ۶۰ ثانیه و تا ۱۰۰MB؛ ۹:۱۶ پیشنهادی | استیکر لینک/نظرسنجی با API ساخته نمی‌شود | [Story specifications](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user/media#story-video-specifications) |
| Private Reply | فقط یک پیام خصوصی به هر کامنت و تا ۷ روز؛ ۷۵۰ call/hour برای Post/Reel؛ Live تا ۱۰۰/second | هر سه قاعده در کد و در لحظه ارسال دوباره بررسی می‌شود | [Private Replies](https://developers.facebook.com/documentation/business-messaging/instagram-messaging/features/private-replies) |
| Instagram DM | پنجره استاندارد ۲۴ ساعت از آخرین پیام کاربر؛ follow-up فقط پس از پاسخ تازه کاربر | Countdown در Inbox؛ پیام سرد/انبوه ممنوع | [Instagram Messaging](https://developers.facebook.com/documentation/business-messaging/instagram-messaging/overview) |
| Rubika Bot API v3 | `POST https://botapi.rubika.ir/v3/{token}/{method}`؛ تصویر تا ۱۰MB و ویدیو MP4/File تا ۵۰MB | `sendMessage`، `requestSendFile`، upload و `sendFile`؛ آلبوم بومی در مستندات v3 تأیید نشده و Manual Fallback دارد | [Rubika methods](https://rubika.ir/botapi/methods) و [models](https://rubika.ir/botapi/models) |
| Telegram | متن ۴۰۹۶ کاراکتر؛ Caption رسانه ۱۰۲۴؛ Media Group از ۲ تا ۱۰؛ عکس با URL تا ۱۰MB و ویدیو با URL تا ۲۰MB | Webhook با Secret Header و dedupe؛ فقط مالک برای دستور/دکمه | [Telegram Bot API](https://core.telegram.org/bots/api) |

---

## ۳) ساخت دیتابیس D1

۱. در مرورگر وارد **Cloudflare Dashboard → Workers & Pages → D1** شوید و یک حساب رایگان بسازید/باز کنید.  
۲. **Create database** را بزنید و نام `nasra_esfahan` را وارد کنید.  
۳. دیتابیس را باز و به **Console** بروید. کل محتوای `schema.sql` را یک‌جا Paste و **Run** کنید. فایل `BEGIN/COMMIT` ندارد و برای D1 Console آماده است.

**✅ نتیجه مورد انتظار:** دستورها بدون خطا اجرا می‌شوند؛ جدول `agents` دارای ۱۲ ردیف، `platform_specs` دارای ۸ ردیف و Playbook نسخه ۱ است. اجرای دوباره `schema.sql` هم خطا نمی‌دهد و ویرایش‌های بعدی Agents را بازنویسی نمی‌کند.

۴. در Query Console برای آزمون اختیاری این دو Query را جدا اجرا کنید:

```sql
SELECT id, name_fa, version FROM agents ORDER BY rowid;
SELECT platform, format, spec_key, last_verified FROM platform_specs;
```

**✅ نتیجه مورد انتظار:** نام فارسی هر ۱۲ Agent و منابع رسمی Specs با `last_verified = 2026-11-04` دیده می‌شود.

---

## ۴) ساخت Worker و اتصال D1

۱. از **Workers & Pages → Create → Worker → Start with Hello World** یک Worker بسازید؛ نام پیشنهادی `nasra-esfahan-media-studio` است.  
۲. **Edit code** را باز کنید، کد نمونه را حذف و تمام `worker.js` را Paste کنید و **Deploy** بزنید. هیچ Build یا npm لازم نیست.  
۳. به **Settings → Bindings** بروید و یک **D1 database binding** اضافه کنید:

```text
Variable name: DB
D1 database: nasra_esfahan
```

۴. اگر Workers AI می‌خواهید، در همان Bindings یک **Workers AI binding** با نام `AI` اضافه کنید. برای Gemini/OpenAI-compatible این binding لازم نیست.  
۵. در **Settings → Triggers → Cron Triggers** فقط این Trigger را اضافه کنید:

```text
*/5 * * * *
```

**✅ نتیجه مورد انتظار:** Deploy موفق است، Binding `DB` دیده می‌شود و فقط یک Cron پنج‌دقیقه‌ای فعال است. با باز کردن `https://<worker-name>.<account>.workers.dev/api/health` پاسخ JSON با `ok:true` و `db:true` می‌گیرید؛ هیچ Secret در پاسخ نیست.

در هر Tick معمولی فقط یک Job عمومی یا یک Publish Job اجرا می‌شود. ساعت‌های نگهداری (RSS/Backup/Insights/Digest) عمداً از کار صف جدا می‌شوند و Tick بعدی صف را ادامه می‌دهد؛ این تأخیر چنددقیقه‌ای برای رعایت Free Tier است، نه گم‌شدن کار.

---

## ۵) وارد کردن Secrets

در **Worker → Settings → Variables and Secrets** هر مورد را به صورت **Secret** اضافه کنید. هیچ‌کدام را به‌صورت خام در `public/js/config.js`، D1، Playbook، Sheets یا Commit قرار ندهید؛ تنها استثنا، توکن refresh‌شده اینستاگرام است که با AES-GCM رمزنگاری و بدون ورود به Backup در D1 نگهداری می‌شود.

| Secret | کاربرد |
|---|---|
| `OWNER_PASSWORD` | رمز قوی و یکتای مالک برای ورود |
| `SESSION_SECRET` | رشته تصادفی طولانی برای HMAC Session و AES-GCM توکن refresh‌شده |
| `TG_BOT_TOKEN` | توکن رسمی BotFather |
| `TG_OWNER_CHAT_ID` | Chat ID عددی مالک؛ فقط همین کاربر می‌تواند دستور/دکمه بزند |
| `TG_SECRET` | رشته تصادفی برای `X-Telegram-Bot-Api-Secret-Token` |
| `AI_PROVIDER` | `workers_ai` یا `gemini` یا `openai_compatible` |
| `AI_API_KEY` | کلید Gemini/OpenAI-compatible؛ برای Workers AI معمولاً خالی می‌ماند |
| `GAS_URL` | URL وب‌اپ Apps Script که به `/exec` ختم می‌شود |
| `GAS_KEY` | کلید مشترک Backup؛ در Script Properties هم همین مقدار را می‌گذارید |
| `IG_APP_SECRET` | App Secret برای اعتبارسنجی `X-Hub-Signature-256` |
| `IG_VERIFY_TOKEN` | رشته دلخواه قوی برای GET Verification |
| `IG_ACCESS_TOKEN` | Long-lived token اینستاگرام |
| `IG_USER_ID` | Instagram-scoped User ID حساب Business/Creator |
| `RUBIKA_BOT_TOKEN` | توکن بات رسمی روبیکا |

**روش امن ساخت رشته تصادفی بدون ابزار محلی:** از Password Manager مرورگر یک رمز ۴۰+ کاراکتری با حروف/عدد/نشانه بسازید و آن را فقط در Secret مربوط ذخیره کنید. `OWNER_PASSWORD` و `SESSION_SECRET` و `TG_SECRET` را یکسان نگیرید.

**✅ نتیجه مورد انتظار:** در `/api/health` فقط مقدار Boolean حضور Secrets دیده می‌شود و خود Token/Password هیچ‌جا چاپ نمی‌شود. اگر فعلاً فقط دو Secret اول را بسازید، Login و Manual Mode کار می‌کند.

> **هشدار:** تغییر `SESSION_SECRET` رمزگشایی توکن refresh‌شده قبلی را ناممکن می‌کند. در این صورت `IG_ACCESS_TOKEN` را دوباره وارد و یک‌بار Refresh دستی انجام دهید.

---

## ۶) انتشار Frontend روی Cloudflare Pages

### روش پیشنهادی فقط با مرورگر: GitHub Web UI

۱. در GitHub Web یک Repository بسازید و فایل‌های پوشه `public/` را با همان ساختار Upload کنید؛ یا کل این پروژه را در Repository قرار دهید. نیازی به نصب Git نیست.  
۲. اگر API روی همان Origin نیست، فایل `public/js/config.js` را در همان صفحه GitHub با دکمه Edit باز کنید و **فقط Origin عمومی Worker** را قرار دهید:

```js
export const API_BASE = 'https://<worker-name>.<account>.workers.dev';
```

هیچ Token یا Passwordی به این فایل اضافه نکنید. برای preview و اتصال same-origin مقدار `''` درست است.  
۳. در Cloudflare **Workers & Pages → Create → Pages → Connect to Git** پروژه را انتخاب کنید.  
۴. Framework preset را **None**، Build command را **خالی** و Build output directory را `public` بگذارید. اگر فقط محتوای `public` را در root آپلود کرده‌اید، output را `/` بگذارید.  
۵. Deploy را بزنید.  
۶. در Worker **Settings → Variables** یک Variable غیرمحرمانه به نام `ALLOWED_ORIGINS` با مقدار Origin کامل Pages بسازید:

```text
https://<pages-project>.pages.dev
```

برای چند Origin، با کاما جدا کنید. این Variable لازم نیست Secret باشد.  
۷. صفحه Pages را در **همان پنجره‌ای که می‌خواهید Session بماند** باز و با `OWNER_PASSWORD` وارد شوید.

**✅ نتیجه مورد انتظار:** رابط راست‌به‌چپ فارسی بدون Google Fonts باز می‌شود؛ Login موفق است و Dashboard داده D1 را می‌خواند. در DevTools مرورگر خطای CORS یا CSP نباید باشد.

### مسیر جایگزین Same-origin

اگر دامنه سفارشی دارید، Pages و Worker را زیر یک Origin/Route قرار دهید و `API_BASE=''` بگذارید. در این حالت Cookie با `SameSite=Lax` ساده‌تر است. در حالت دو Origin، Worker خودش `SameSite=None; Secure` و CORS Credential را اعمال می‌کند. در Preview تعبیه‌شده نیز رابط به‌صورت خودکار حالت embedded را اعلام می‌کند تا کوکی امن `SameSite=None; Secure; Partitioned` صادر شود و Session بلافاصله پس از Login از دست نرود؛ ورود مستقیم Top-level همچنان `SameSite=Lax` باقی می‌ماند. برای حالتی که مرورگر یا Preview proxy کوکی را کاملاً حذف می‌کند، Preview همان نشست امضاشده را با `Authorization: Bearer` نیز ارسال می‌کند و توکن فقط در `sessionStorage` همان تب نگهداری می‌شود. در ورود مستقیم Production که embedded نیست، `session_token` به Frontend داده نمی‌شود و HttpOnly cookie مسیر پیش‌فرض باقی می‌ماند.

**✅ نتیجه مورد انتظار:** Refresh صفحه Session را نگه می‌دارد و Logout کوکی را ابطال می‌کند.

---

## ۷) انتخاب و تست AI

### الف) Workers AI

۱. Binding `AI` را بسازید و Secret `AI_PROVIDER=workers_ai` را بگذارید. `AI_API_KEY` خالی می‌ماند.  
۲. به **Agents** در استودیو بروید و برای مدل پیش‌فرض خالی بمانید یا Model سازگار با حساب خود را وارد کنید.  
۳. یک **Test run** انجام دهید.

**✅ نتیجه مورد انتظار:** خروجی فقط JSON معتبر با `risk_flags` و `needs_info` است؛ مصرف و Repair در Agent Runs ثبت می‌شود. اگر Binding نباشد، پیام not configured ظاهر می‌شود نه خطای مرگبار.

### ب) Gemini

۱. در Google AI Studio با همان مرورگر API Key بسازید و در Secret `AI_API_KEY` بگذارید.  
۲. `AI_PROVIDER=gemini` و در صورت نیاز Model سازگار را در Agents وارد کنید.  
۳. RPM/TPM/RPD همان مدل را از [صفحه رسمی Rate Limits](https://ai.google.dev/gemini-api/docs/rate-limits) بخوانید و سقف روزانه داخلی را پایین‌تر بگذارید.

**✅ نتیجه مورد انتظار:** 429/Resource Exhausted به `QUOTA_EXHAUSTED` و Job با `run_after` تبدیل می‌شود؛ هیچ انتشار یا Retry بی‌نهایت سریع رخ نمی‌دهد.

### ج) OpenAI-compatible

۱. `AI_PROVIDER=openai_compatible` و `AI_API_KEY` را بگذارید.  
۲. Base URL HTTPS و Model را در صفحه Agents وارد کنید. Base URL پیش‌فرض `https://api.openai.com/v1` است و برای سرویس دیگر باید مسیر سازگار `/chat/completions` داشته باشد.  
۳. Test run را اجرا کنید.

**✅ نتیجه مورد انتظار:** JSON Schema نامعتبر یک‌بار Repair می‌شود؛ پس از شکست دوم، خطا و Raw Output در Logs/Agent Runs برای عیب‌یابی ثبت می‌شود.

---

## ۸) راه‌اندازی Google Sheets و Apps Script

۱. در Google Drive یک **Google Sheet** جدید بسازید و نام آن را `Nasra Backup` بگذارید.  
۲. از **Extensions → Apps Script** وارد ویرایشگر شوید. محتوای `Code.gs` را در فایل `Code.gs` Paste کنید.  
۳. در **Project Settings → Manifest** محتوای `gas/appsscript.json` را قرار دهید؛ Time Zone باید `Asia/Tehran` باشد.  
۴. در **Project Settings → Script Properties** یک Property بسازید:

```text
Name: GAS_KEY
Value: <همان رشته قوی GAS_KEY در Cloudflare Secrets>
```

۵. روی **Deploy → New deployment → Web app** بزنید:

```text
Execute as: Me
Who has access: Anyone
```

این دسترسی فقط درخواست را می‌پذیرد؛ `Code.gs` بدون `GAS_KEY` در بدنه، هیچ نوشتنی انجام نمی‌دهد و کلید در GET یا Log درج نمی‌شود.  
۶. Deployment را Authorize و URL وب‌اپ را که به `/exec` ختم می‌شود در Secret `GAS_URL` بگذارید.  
۷. در استودیو به **Backup** بروید و «اجرای پشتیبان‌گیری Sheets» را بزنید.

**✅ نتیجه مورد انتظار:** تب‌های `mirror_<table>` و `snap_01_<table>` و `backup_log` ساخته می‌شوند. مقدار `21:00` و `1405/07/23` همان متن می‌ماند، چون فرمت `@` پیش از `setValues` اعمال می‌شود. اجرای دوباره، Mirror را حذف نمی‌کند و Snapshotها چرخشی تا N نسخه نگه داشته می‌شوند.

۸. برای بازیابی، در **Backup → بازیابی از JSON** فایل دانلودشده را انتخاب و تأیید کنید. UI فایل را در قطعه‌های حداکثر ۲۵ ردیف و با `next_cursor` ادامه می‌دهد تا سقف ۵۰ کوئری D1 رعایت شود؛ تا پیام موفقیت نهایی صفحه را نبندید. Restore فقط `INSERT OR REPLACE` انجام می‌دهد و هیچ جدولی را خالی نمی‌کند. Secrets، Sessionها و Webhook Eventها در Export نیستند.

**✅ نتیجه مورد انتظار:** تعداد ردیفهای بازیابی‌شده نمایش داده می‌شود، `done:true` در قطعه آخر است و Login/Secretهای فعلی شما تغییر نمی‌کند.

> اگر Google یک Redirect برای احراز نشان داد، نوع Deployment/دسترسی را بررسی کنید. Webhook تلگرام را **هیچ‌وقت** مستقیم به `/exec` وصل نکنید؛ پاسخ 302 برای Webhook مناسب نیست. Webhook تلگرام فقط به Worker می‌رود.

---

## ۹) راه‌اندازی Telegram

۱. در Telegram برای **@BotFather** دستور `/newbot` را بزنید و Token رسمی را در `TG_BOT_TOKEN` قرار دهید.  
۲. با حساب مالک به بات پیام `/start` بدهید. Chat ID عددی حساب خود را از اطلاعات رسمی `getUpdates` یا یک ابزار شناسه‌یاب معتبر بگیرید و در `TG_OWNER_CHAT_ID` قرار دهید. این مقدار را حدس نزنید.  
۳. برای کانال، بات را **Administrator** کنید و Chat ID کانال را در **Settings → کانال تلگرام** وارد کنید (`@channelusername` یا ID عددی معتبر).  
۴. `TG_SECRET` را بسازید و در Secrets بگذارید.  
۵. در استودیو به **Integrations** بروید و «ثبت Webhook از Worker» را بزنید. Worker با `setWebhook` و Secret Header، آدرس `/webhooks/telegram` را ثبت می‌کند؛ Token در URL مرورگر شما قرار نمی‌گیرد.  
۶. به بات `/status`، `/today`، `/queue` و `/inbox` بفرستید.

**✅ نتیجه مورد انتظار:** فقط Chat ID مالک پاسخ می‌گیرد؛ کاربر دیگر نادیده گرفته می‌شود. تکرار یک `update_id` دوباره پردازش نمی‌شود و هر Gate دکمه تأیید/اصلاح/رد دارد. برای Request changes، بات یادداشت پیام بعدی مالک را می‌پذیرد.

۷. برای انتشار کانال، پس از Gate 3 و زمان مقرر، نتیجه را در **Publish Center** ببینید. `sendPhoto`، `sendVideo` و `sendMediaGroup` با Caption حداکثر ۱۰۲۴ کاراکتر استفاده می‌شوند.

**✅ نتیجه مورد انتظار:** انتشار فقط برای محتوایی است که Gate 3 همان `current_version` را Approve کرده است؛ اجرای دوباره Cron یک Package را دوبار منتشر نمی‌کند.

---

## ۱۰) راه‌اندازی Meta / Instagram Graph API

### الف) پیش‌نیاز حساب و App

۱. حساب Instagram را **Business یا Creator** کنید و مالکیت حساب را در Meta Business تأیید کنید. Personal account برای Content Publishing کافی نیست.  
۲. در [Meta for Developers](https://developers.facebook.com/) یک App بسازید و محصول **Instagram** را اضافه کنید. نوع Use case را مطابق انتشار و مدیریت پیام واقعی انتخاب کنید.  
۳. نسخه API پیش‌فرض پروژه `v26.0` است. در Settings استودیو مسیر یکی از دو گزینه زیر را انتخاب کنید.

### ب) Instagram Login — پیش‌فرض

Host: `https://graph.instagram.com`  
Permissionهای متداول مورد نیاز را بر اساس قابلیت‌های واقعی درخواست کنید:

- `instagram_business_basic`
- `instagram_business_content_publish`
- `instagram_business_manage_comments`
- `instagram_business_manage_messages`

Token طولانی را مطابق [مستندات رسمی Instagram Platform](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api) بسازید و `IG_USER_ID` همان Instagram-scoped ID باشد.  
Refresh رسمی در کد استفاده می‌شود:

```text
GET https://graph.instagram.com/refresh_access_token
    ?grant_type=ig_refresh_token
    &access_token=<CURRENT_TOKEN>
```

توکن طولانی معمولاً ۶۰ روزه است و Refresh پس از حداقل ۲۴ ساعت عمر توکن انجام می‌شود؛ پاسخ واقعی `expires_in` مبنای D1 است، نه حدس تاریخ. Token جدید با AES-GCM در `integration_tokens` نگهداری می‌شود.

### ج) Facebook Login — مسیر پشتیبانی‌شده دیگر

Host: `https://graph.facebook.com/v26.0`  
Permissionها را مطابق صفحه فعلی Meta و قابلیت مورد نیاز درخواست کنید؛ از جمله:

- `instagram_basic`
- `instagram_content_publish`
- `instagram_manage_comments`
- `instagram_manage_insights`
- `instagram_manage_messages`
- `pages_show_list`
- `pages_read_engagement`

در این مسیر باید Page مرتبط و Instagram Business/Creator ID درست را از مسیر رسمی Pages بگیرید؛ `IG_USER_ID` را با Facebook Page ID اشتباه نکنید. تازه‌سازی خودکار این مسیر به App ID/Client Token و روش توکن شما وابسته است؛ Worker به‌جای حدس، پیام `MANUAL_REQUIRED` می‌دهد و توکن تازه را در Secrets می‌خواهد. مسیر Instagram Login برای Refresh خودکار کد پیش‌فرض امن است.

**✅ نتیجه مورد انتظار:** در **Integrations → تست اتصال Instagram** فیلدهای `id`/`username`/`account_type` برمی‌گردد. خطای 401/403 به جای انتشار آزمایشی ناموفق، توکن/نوع حساب/permission را توضیح می‌دهد.

### د) Webhook اینستاگرام

۱. در Worker، `IG_APP_SECRET` و یک رشته تصادفی `IG_VERIFY_TOKEN` بگذارید.  
۲. در Meta App → **Webhooks → Instagram → Add Callback URL** مقدار زیر را وارد کنید:

```text
Callback URL: https://<worker-name>.<account>.workers.dev/webhooks/instagram
Verify token: <همان IG_VERIFY_TOKEN>
```

۳. Subscribe را برای **comments** و **messages/messaging** مطابق فیلدهای ارائه‌شده در Dashboard فعال کنید.  
۴. اپ را در همان حساب تست Subscribe کنید و یک کامنت/DM آزمایشی از حساب دوم بفرستید.

**✅ نتیجه مورد انتظار:** GET با `hub.challenge` تأیید می‌شود. POST امضای `X-Hub-Signature-256` را با Raw Body و `IG_APP_SECRET` چک می‌کند، سریع `200` می‌دهد و پردازش در Background انجام می‌شود. کامنت/DM در Inbox ظاهر می‌شود؛ امضای غلط 401 می‌گیرد و Event تکراری دوباره وارد نمی‌شود.

### ه) آماده‌سازی App Review

قبل از Live Mode و درخواست permissionهای پیشرفته، این موارد را آماده کنید:

- Privacy Policy و Terms قابل دسترس و صاحب دامنه مشخص.
- توضیح فارسی/انگلیسی مورد استفاده: تولید پیش‌نویس آموزشی، انتشار با تأیید انسانی، مدیریت کامنت و DM؛ **بدون Scraping و بدون پیام انبوه**.
- ویدیوی Demo یا اسکرین‌شات‌های واضح از: Login مالک، سه Gate، Manual/Scheduled Publish، Inbox پیش‌نویس و دکمه تأیید، Countdown ۷روز/۲۴ساعت، Audit Log و Selftest.
- نمایش دقیق اینکه AI فقط JSON پیش‌نویس می‌دهد و هیچ اقدام خارجی بدون تأیید مالک انجام نمی‌شود.
- برای هر permission یک سناریوی واقعی: چرا لازم است، کدام دکمه/API از آن استفاده می‌کند و داده چطور حذف/بازیابی می‌شود.
- حساب تست و دستورالعمل Reviewer؛ Secret واقعی را در ویدیو یا اسکرین‌شات نشان ندهید.

**✅ نتیجه مورد انتظار:** Reviewer می‌تواند مسیر «ورودی → پیش‌نویس → تأیید مالک → اقدام → Audit» را بدون دسترسی به Secretها تکرار کند و برای هر permission دلیل روشن ببیند.

### و) قواعد انتشار و Inbox در عمل

- **Image/Carousel/Reel/Story:** دو مرحله `POST /{ig-user-id}/media` و سپس `POST /{ig-user-id}/media_publish` انجام می‌شود. برای ویدیو، Container ذخیره و Status تا `FINISHED` پیمایش می‌شود.
- **قبل از انتشار:** `GET /{ig-user-id}/content_publishing_limit` اجباری است. اتمام Quota کار را حذف نمی‌کند؛ `QUEUED_LIMIT` و Retry بعدی می‌شود.
- **Private reply:** فقط یک‌بار تا ۷ روز و با `recipient: {comment_id}`. پاسخ عمومی از `POST /{ig-comment-id}/replies` و Hide از `POST /{ig-comment-id}` با `hide=true|false` است.
- **DM:** پس از آخرین پیام کاربر ۲۴ ساعت فرصت دارید. اگر مالک پاسخ داد، برای Follow-up باید کاربر دوباره پیام بدهد. خارج از پنجره، Worker مسدود می‌کند.
- **مسیر دستی خارج از پنجره:** در Meta Business Suite/Inbox رسمی پاسخ دهید. برچسب **Human Agent** فقط در صورت داشتن دسترسی/تأیید لازم و مطابق [مستندات Messaging](https://developers.facebook.com/documentation/business-messaging/instagram-messaging/overview) برای پشتیبانی انسانی تا ۷ روز است؛ نسرا آن را دورزدن پنجره یا ارسال خودکار نمی‌داند.
- **Insights:** متریک‌های فعلی `views, reach, total_interactions, saved, shares, likes, comments, follows` خوانده می‌شوند. `impressions` برای رسانه‌های جدید جایگزین پیش‌فرض نیست.

**✅ نتیجه مورد انتظار:** هیچ دکمه UI اجازه ارسال خارج از پنجره نمی‌دهد و حتی با ساخت Request دستی هم Worker همان قواعد را دوباره اعمال می‌کند.

---

## ۱۱) راه‌اندازی Rubika رسمی

۱. از مسیر رسمی Rubika Bot API یک بات بسازید و Token را فقط در `RUBIKA_BOT_TOKEN` قرار دهید. از هیچ کتابخانه Session-login، شماره/رمز شخصی یا Scraping استفاده نکنید.  
۲. بات را در کانال خود با دسترسی ارسال لازم قرار دهید. شناسه واقعی Chat را با متدهای رسمی `getMe`، `getChat` یا `getUpdates` و مطابق مدل‌های [مستندات Rubika](https://rubika.ir/botapi/models) بررسی کنید و در Settings وارد کنید.  
۳. در Integrations دکمه «تست بات روبیکا» را بزنید.  
۴. برای متن، Worker از `sendMessage` استفاده می‌کند. برای رسانه، زنجیره `requestSendFile` → Multipart Upload به `upload_url` → `sendFile` انجام می‌شود.

**✅ نتیجه مورد انتظار:** `getMe` موفق است و انتشار متن فقط پس از Gate 3 انجام می‌شود. لینک رسانه باید مستقیم و قابل دانلود باشد و تصویر تا ۱۰MB و ویدیو MP4 تا ۵۰MB باشد.

> **Needs verification / Manual Fallback:** در مستندات Bot API v3 متد بومی آلبوم چندتایی مانند `sendMediaGroup` تأیید نشده است. نسرا endpoint حدسی نمی‌زند؛ Carousel/Album روبیکا به Manual Package می‌رود. می‌توانید آیتم‌ها را دستی یا—پس از تأیید مستندات جدید—با به‌روزرسانی adapter ارسال کنید.

---

## ۱۲) گردش کار روزانه مالک

۱. در **Sources** فیدهای رسمی یا گزارش‌های کانال خود را وارد کنید؛ برای RSS از «خواندن RSSهای فعال» و برای متن کانال از «افزودن آیتم» استفاده کنید.  
۲. در **Pipeline** دکمه «سوژه‌یابی» را بزنید یا منتظر Cron ساعت ۰۷:۰۰ تهران بمانید.  
۳. روی یک Topic «ساخت ایده و شروع گیت ۱» بزنید. در Gate 1 یکی از ۳ تا ۵ ایده را انتخاب کنید.  
۴. سناریو و گزارش Fact Checker را بخوانید؛ Gate 2 را Approve یا با یادداشت Request changes کنید.  
۵. خروجی تولید را بگیرید. پوستر/اینفوگرافیک SVG را در مرورگر به JPEG ۱۰۸۰×۱۳۵۰ دانلود کنید. ویدیو و صدا را با ابزار خودتان بسازید؛ Agents فایل ویدیو/صدا تولید نمی‌کنند.  
۶. لینک‌های HTTPS مستقیم رسانه و Metadata (عرض، ارتفاع، مدت، حجم) را در صفحه محتوا وارد کنید.  
۷. Caption، Alt Text، چک‌لیست Editorial و Tech Advisor را ببینید و Gate 3 را تأیید کنید.  
۸. در Calendar زمان را ببینید و در Publish Center وضعیت هر پلتفرم را دنبال کنید. در Manual Mode کپشن را Copy، بسته JSON را Download و پس از انتشار واقعی URL را ثبت کنید.  
۹. در Inbox پیش‌نویس را ویرایش و فقط با Confirm ارسال کنید. در Analytics داده واقعی جمع کنید و فرضیه‌های زمان/فرمت را آزمایش کنید.

**✅ نتیجه مورد انتظار:** هر تغییر مرحله در Timeline، Versions و Audit Log دیده می‌شود؛ Request changes نسخه جدید می‌سازد و تصمیم دوم روی همان Gate «already decided» برمی‌گرداند.

---

## ۱۳) Selftest و آزمون پیش از انتشار

۱. وارد **آزمون پذیرش** شوید و «اجرای همه تست‌ها» را بزنید. UI پنج بخش `p1` تا `p5` را در پنج Request جدا اجرا می‌کند تا سقف ۵۰ کوئری D1 در هر invocation رعایت شود.  
۲. Endpointهای مستقل نیز موجودند:

```text
GET /api/selftest?part=p1   # تقویم، Validatorها، transition و race گیت
GET /api/selftest?part=p2   # Repair و Prompt Injection
GET /api/selftest?part=p3   # Login rate limit، AI quota، امضا و dedupe
GET /api/selftest?part=p4   # پنجره‌ها، انتشار، Manual Mode و Sheets text
GET /api/selftest?part=p5   # Fact-check block و عدم ساخت Gate 2
```

`GET /api/selftest` هم مجموعه کامل را برای آزمون محلی/تشخیصی برمی‌گرداند؛ در D1 production از دکمه UI یا endpointهای جزئی استفاده کنید تا بودجه Query تقسیم شود.

**✅ نتیجه مورد انتظار:** **۱۶/۱۶ PASS** با این موارد:

- `1403/12/30 ↔ 2025-03-20` و `1405/08/02 ↔ 2026-10-24` و Leap Day.
- همه Validatorهای Caption/Carousel/JPEG/Reel/Story/Hashtag/SRT/URL.
- Illegal Transition، Double/Concurrent Gate و «already decided».
- یک Repair برای JSON نامعتبر، مسدودسازی محتوا با `FACTCHECK_FAIL` و عدم ساخت Gate 2، و نادیده‌گرفتن نمونه Prompt Injection داخل `<untrusted>`.
- Login Rate Limit، AI Quota Retry Job، امضای IG Accept/Reject و Duplicate Event.
- قاعده ۷ روز/یک Private Reply، قاعده ۲۴ ساعت DM و منع Cold Follow-up.
- Publishing Idempotency، Queue هنگام اتمام Limit، Manual Fallback و TEXT بودن تاریخ/ساعت Sheets.

تست‌ها هیچ Post، DM یا Reply واقعی ارسال نمی‌کنند و فقط IDهای مخصوص خود را می‌سازند/پاک می‌کنند.

---

## ۱۴) به‌روزرسانی کد بدون از دست رفتن داده

### Worker

۱. از **Backup → دانلود JSON کامل** یک نسخه بگیرید.  
۲. نسخه جدید `worker.js` را کامل در **Edit code** جایگزین و Deploy کنید.  
۳. `/api/health` و سپس Selftest پنج‌بخشی را اجرا کنید.

**✅ نتیجه مورد انتظار:** داده D1 و Secrets تغییر نمی‌کند؛ فقط کد اجرایی جدید فعال می‌شود.

### Schema

- `schema.sql` idempotent است و Seedها `INSERT OR IGNORE` دارند. برای افزودن جدول جدید می‌توانید آن را دوباره اجرا کنید.
- برای تغییر ستون‌های جدول موجود، Migration مشخص `ALTER TABLE ... ADD COLUMN` جدا بنویسید و پیش از آن Backup بگیرید. پرامپت‌های ویرایش‌شده در UI با Seed دوباره بازنویسی نمی‌شوند.

**✅ نتیجه مورد انتظار:** اجرای دوباره هیچ ردیف تکراری از Agents/Playbook/Sources نمی‌سازد.

### Frontend

فایل‌های `public/` را در GitHub Web جایگزین کنید؛ Pages خودکار Deploy می‌کند. Build command همچنان خالی است. بعد از Deploy با Hard Refresh کش مرورگر را پاک کنید.

### Apps Script

پس از ویرایش `Code.gs`، فقط Save کافی نیست: **Deploy → Manage deployments → Edit → New version → Deploy** را بزنید و همان `/exec` را نگه دارید.

**✅ نتیجه مورد انتظار:** Backup جدید با نسخه جدید نوشته می‌شود و Mirrorهای قدیمی حذف نمی‌شوند.

---

## ۱۵) جدول عیب‌یابی

| علامت | معنی | راه‌حل |
|---|---|---|
| `DB_NOT_CONFIGURED` | Binding دیتابیس نیست یا نامش DB نیست | D1 binding با نام دقیق `DB` بسازید و Deploy کنید |
| Login می‌گوید not configured | یکی از `OWNER_PASSWORD`/`SESSION_SECRET` نیست | هر دو را Secret کنید؛ Variable معمولی کافی نیست |
| `LOGIN_RATE_LIMITED` | ۵ شکست در ۱۵ دقیقه | تا پایان Retry After صبر کنید؛ VPN/IP جدید را راه دورزن ندانید |
| `CSRF_FAILED` | Session قدیمی یا Frontend نسخه قدیمی | Refresh و دوباره Login؛ API_BASE و ALLOWED_ORIGINS را چک کنید |
| CORS/Preflight خطا | Origin Pages مجاز نیست | `ALLOWED_ORIGINS` را با Origin کامل و بدون `/` پایانی تنظیم کنید |
| Agent می‌گوید `NOT_CONFIGURED` | Provider/Key/Binding ناقص است | Integrations و Agents را چک کنید؛ Manual Packageها از بین نمی‌روند |
| `QUOTA_EXHAUSTED` | سقف داخلی یا Provider پر شده | Job و `run_after` را ببینید؛ سقف را فقط آگاهانه تغییر دهید |
| خروجی مدل JSON نیست | Schema یا پاسخ مدل مشکل دارد | یک Repair خودکار انجام می‌شود؛ Raw/Schema را در Agents بررسی و Model را تغییر دهید |
| `ILLEGAL_TRANSITION` | مرحله نامناسب یا داده قدیمی | صفحه را Refresh کنید؛ از Gate معتبر همان نسخه اقدام کنید |
| `already decided` | همان Gate قبلاً تصمیم گرفته | این رفتار امن و مورد انتظار است؛ نسخه بعدی Gate جدید دارد |
| `CONCURRENT_UPDATE` | دو تب/کار هم‌زمان محتوا را عوض کرده | Refresh و تصمیم را روی نسخه جدید بررسی کنید |
| `VALIDATION_FAILED` در Gate 3 | کپشن/رسانه با Spec سازگار نیست | JPEG، نسبت، تعداد اسلاید، مدت، URL مستقیم و Metadata را اصلاح کنید |
| Meta خطای 401/190 | Token منقضی/باطل | Refresh دستی؛ در صورت شکست Token تازه در Secrets |
| Meta خطای 403 | نوع حساب یا Permission/App Review ناقص | Business/Creator، IG_USER_ID، Permissionها و App Mode را بررسی کنید |
| `QUEUED_LIMIT` | ۱۰۰ پست/۲۴ساعت یا Quota حساب پر شده | صف را حذف نکنید؛ Cron با Backoff ادامه می‌دهد |
| ویدیو روی `POLLING_CONTAINER` می‌ماند | Meta هنوز پردازش می‌کند یا Container خطا دارد | چند Tick صبر کنید؛ Codec/حجم/لینک عمومی را بررسی کنید |
| نتیجه `media_publish` نامشخص | پاسخ شبکه پس از ارسال قطع شده | برای جلوگیری از انتشار دوباره به Manual رفته؛ اول پست را در Instagram بررسی کنید |
| `MANUAL_READY` | Credential یا کانال نیست، یا Album روبیکا نیازمند تأیید است | بسته را دانلود و دستی منتشر کنید؛ URL واقعی را ثبت کنید |
| Rubika `NEEDS_VERIFICATION` | آلبوم بومی در Bot API v3 مستند نیست | از Manual Package استفاده کنید؛ endpoint حدسی اضافه نکنید |
| Inbox دکمه DM غیرفعال | ۲۴ ساعت گذشته یا کاربر پیام تازه نداده | از Inbox رسمی Meta و مسیر Human Agent مجاز استفاده کنید |
| Private Reply غیرفعال | ۷ روز گذشته یا یک پاسخ قبلاً رفته | پاسخ عمومی/پشتیبانی دستی؛ ارسال خصوصی دوم ممنوع |
| Telegram برای دیگران جواب نمی‌دهد | محافظ مالک فعال است | فقط `TG_OWNER_CHAT_ID` مجاز است؛ این رفتار امن است |
| Telegram Webhook 401 | Secret Header اشتباه | Webhook را از Integrations دوباره با `TG_SECRET` ثبت کنید |
| Instagram Webhook 401 | امضا با Raw Body مطابقت ندارد | `IG_APP_SECRET` را اصلاح کنید؛ Body را پیش از HMAC تغییر ندهید |
| Backup unauthorized | `GAS_KEY` در Script Properties و Cloudflare یکسان نیست | هر دو را مساوی کنید و New version Deploy بگیرید |
| ساعت `21:00` در Sheets عوض می‌شود | فرمت ستون Text نیست یا نسخه قدیمی GAS | آخرین `Code.gs` را Deploy کنید؛ `@` پیش از `setValues` بررسی شود |
| RSS آیتم نمی‌آورد | URL غیرمستقیم/خصوصی یا Feed معتبر نیست | HTTPS عمومی RSS/Atom واقعی وارد کنید؛ آدرس HTML سایت را با Feed اشتباه نکنید |
| لینک Drive در Meta خطا می‌دهد | لینک صفحه Share است، نه فایل عمومی مستقیم | Direct HTTPS JPEG/MP4 بسازید و در Private Window تست دانلود کنید |

---

## ۱۶) چک‌لیست پیش از Live

- [ ] `schema.sql` اجرا و ۱۲ Agent/۷ Pillar/Brand Kit/Specs دیده شده است.
- [ ] D1 binding `DB` و فقط یک Cron `*/5 * * * *` فعال است.
- [ ] `OWNER_PASSWORD` قوی و `SESSION_SECRET` یکتا در Secrets است؛ هیچ Secret خام در Frontend/D1/Sheets نیست و توکن رمزنگاری‌شده وارد Backup نمی‌شود.
- [ ] Login، Refresh Session، Logout، CSRF و Rate Limit آزمون شده است.
- [ ] Pages بدون Build اجرا و `API_BASE`/`ALLOWED_ORIGINS` درست است.
- [ ] AI Test Run موفق یا not configured شفاف است؛ سقف‌های روزانه تنظیم شده‌اند.
- [ ] هر ۱۶ Acceptance Test از UI سبز است.
- [ ] Telegram Webhook ثبت و `/status /today /queue /inbox` فقط برای مالک کار می‌کند.
- [ ] کانال تلگرام/روبیکا و Chat IDها با مقادیر واقعی تأیید شده‌اند.
- [ ] Instagram Business/Creator، `IG_USER_ID`، Permissionها و App Mode درست است.
- [ ] Instagram GET/POST Webhook، امضا و dedupe آزمون شده است.
- [ ] Token Expiry و Refresh در Integrations دیده می‌شود و خطای Refresh به تلگرام هشدار می‌دهد.
- [ ] رسانه‌ها Direct HTTPS، JPEG/MP4، حجم/نسبت/مدت مناسب و بدون احراز هستند.
- [ ] Content Publishing Limit و حالت `QUEUED_LIMIT` بررسی شده است.
- [ ] هیچ انتشار واقعی بدون Gate 3 و هیچ Reply بدون Confirm مالک انجام نمی‌شود.
- [ ] پنجره ۷روز/۲۴ساعت و مسیر Human Agent برای موارد خارج از پنجره آزمون شده است.
- [ ] Rubika فقط Bot API رسمی است و Album به Manual Fallback می‌رود.
- [ ] JSON Backup دانلود و Restore روی یک کپی آزمون شده است.
- [ ] Apps Script New Version و Mirror/Snapshot با TEXT Dates بررسی شده است.
- [ ] Privacy Policy، App Review Demo و دلیل هر Permission آماده است.
- [ ] مصرف روزانه Workers/D1/AI زیر سقف Free برآورد شده و هشدارهای Telegram فعال‌اند.
- [ ] یک JSON Backup تازه پیش از Live دانلود شده است.

---

## مراجع رسمی اصلی

- Cloudflare Workers: <https://developers.cloudflare.com/workers/platform/limits/>
- Cloudflare D1: <https://developers.cloudflare.com/d1/platform/limits/>
- Cloudflare Pages: <https://developers.cloudflare.com/pages/platform/limits/>
- Workers AI: <https://developers.cloudflare.com/workers-ai/platform/pricing/>
- Gemini Rate Limits: <https://ai.google.dev/gemini-api/docs/rate-limits>
- Instagram Content Publishing: <https://developers.facebook.com/documentation/instagram-platform/content-publishing>
- Instagram Media Reference: <https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user/media>
- Instagram Private Replies: <https://developers.facebook.com/documentation/business-messaging/instagram-messaging/features/private-replies>
- Rubika Bot API: <https://rubika.ir/botapi/methods> و <https://rubika.ir/botapi/models>
- Telegram Bot API: <https://core.telegram.org/bots/api>
