# شبیه‌ساز محلی (فقط برای توسعه — در Apps Script کپی نشود)
کد `nasra-gas/*.gs` را با نسخهٔ حافظه‌ای سرویس‌های گوگل و یک تلگرام ساختگی اجرا می‌کند.

    node tools/sim/run.js            # runTests() ← باید «N passed» چاپ شود
    node tools/sim/scenario_samples.js
    node tools/sim/scenario_form.js
    node tools/sim/scenario_types.js
    node tools/sim/scenario_media.js
    node tools/sim/scenario_score.js
    node tools/sim/scenario_roles.js   # نقش در ثبت‌نام، منوی فضای مجازی، گزارش‌های من
    node tools/sim/scenario_users.js   # پنل ← کاربران: جستجو با شماره / ناحیه ← نقش
    node tools/sim/load_order.js       # ترتیب بارگذاری فایل‌ها (باید 0 باشد)
