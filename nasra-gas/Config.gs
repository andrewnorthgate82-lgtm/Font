// Config.gs — تنظیمات ثابت، اسکیمای برگه‌ها، بذر شهرستان‌ها، متن‌های فارسی و ابزار نرمال‌سازی
// هیچ رازی (توکن، کلیدها) این‌جا نیست؛ آن‌ها در Script Properties هستند.

var CFG = {
  CLASS_TYPES: ['حضوری', 'مجازی', 'توانمندسازی', 'خلاقانه', 'تولیدات'],
  AUDIENCES: ['عموم', 'گردان'],
  DURATIONS_MIN: [30, 60, 90, 120, 180],
  MINUTES: ['00', '15', '30', '45'],
  MAX_PHOTOS: 5,
  MIN_PARTICIPANTS: 1,
  MAX_PARTICIPANTS: 5000,
  CAPTION_LIMIT: 1024,
  REJECT_REASONS: [
    'نداشتن نماد نسرا در عکس‌ها',
    'دست‌کاری/فتوشاپ نماد نسرا',
    'نداشتن نام مدرس',
    'استفاده نادرست از هشتگ‌ها',
    'سایر'
  ],
  MAX_REPORTS_PER_MINUTE: 3,
  CITIES_PER_PAGE: 10,
  TEACHERS_PER_PAGE: 10,
  CACHE_SECONDS: 300
};

var STATUS_FA = {
  draft: 'پیش‌نویس', pending: 'در انتظار بررسی', approved: 'تایید‌شده',
  rejected: 'ردشده', deleted: 'حذف‌شده'
};
var STATUS_EMOJI = { draft: '📝', pending: '⏳', approved: '✅', rejected: '❌', deleted: '🗑' };

// اسکیمای یازده برگه — هدرها ثابت‌اند؛ ترتیب ستون‌ها را در Sheet عوض نکنید.
var SHEETS = {
  Settings: ['key', 'value', 'note'],
  Cities: ['city_id', 'name', 'hashtag', 'is_active'],
  Users: ['user_id', 'telegram_id', 'phone', 'full_name', 'city_id', 'role', 'status', 'reg_group_message_id', 'created_at', 'updated_at', 'position'],
  Whitelist: ['phone', 'full_name', 'city_name', 'city_id'],
  Admins: ['telegram_id', 'full_name', 'role', 'is_active', 'added_at'],
  Instructors: ['instructor_id', 'city_id', 'name', 'name_norm', 'is_active', 'created_by', 'created_at'],
  Reports: ['report_id', 'submitter_telegram_id', 'employee_name', 'city_id', 'instructor_id', 'instructor_name',
    'status', 'class_type', 'operation_name', 'subject', 'event_jalali', 'event_gregorian', 'event_year',
    'event_month', 'event_day', 'event_time', 'duration_min', 'participants', 'audience', 'place',
    'publish_jalali', 'published_caption', 'channel_message_ids', 'review_chat_id', 'review_album_ids',
    'review_info_id', 'decided_by', 'decided_at', 'resubmit_count', 'created_at', 'updated_at', 'submitter_position', 'extra_channel_msgs'],
  ReportPhotos: ['report_id', 'sort', 'file_id', 'file_unique_id'],
  RejectHistory: ['report_id', 'admin_telegram_id', 'reasons_json', 'manual_text', 'created_at'],
  Sessions: ['key', 'chat_id', 'user_id', 'state', 'data_json', 'updated_at'],
  Logs: ['ts', 'level', 'event', 'detail', 'update_id'],
  Samples: ['class_type', 'items_json', 'updated_by', 'updated_at'] // نمونه گزارش‌ها (پست آموزشی هر نوع برنامه) — از پنل پر می‌شود
};

// دکمه‌های منوی اصلی همکاران (برچسب قدیمی هم پذیرفته می‌شود تا کیبوردهای قدیمی کار کنند)
var MENU = {
  NEW_REPORT: '📝 ارسال گزارش', NEW_REPORT_OLD: '📝 ثبت گزارش جدید', SAMPLES: '📚 نمونه گزارش‌ها',
  MY_REPORTS: '📋 گزارش‌های من', MY_TEACHERS: '👨‍🏫 مدرسان من'
};

// نقش همکاران در هر شهرستان (ستون position برگه Users). «خواهر/برادر» فایل اکسل از همین نقش تعیین می‌شود؛
// گزارش‌های «مسئول فضای مجازی» و همکاران بدون نقش فقط در «تجمیعی» و «همه شهرستان‌ها» می‌آیند.
var POSITIONS = ['مسئول فضای مجازی', 'فرمانده گردان خواهر', 'مسئول نسرا خواهر', 'فرمانده گردان برادر', 'مسئول نسرا برادر'];
function positionOf_(raw) { // متن نقش ← همان نام استاندارد (با تحمل نیم‌فاصله/فاصله/ی و ک عربی) یا ''
  var n = normCmp_(raw);
  if (!n) return '';
  for (var i = 0; i < POSITIONS.length; i++) if (normCmp_(POSITIONS[i]) === n) return POSITIONS[i];
  return '';
}
function positionSide_(raw) { // 's' خواهر ، 'b' برادر ، 'v' فضای مجازی ، '' بدون نقش
  var p = positionOf_(raw);
  if (!p) return '';
  return p.indexOf('خواهر') !== -1 ? 's' : p.indexOf('برادر') !== -1 ? 'b' : 'v';
}

// ۳۲ شهرستان — هشتگ‌ها عیناً همین‌طور (بدون حذف «و» و بدون تغییر زیرخط)
var CITIES_SEED = [
  [1, 'آران و بیدگل', '#آران_و_بیدگل'], [2, 'امام حسین(ع)', '#امام_حسین'],
  [3, 'امام رضا(ع)', '#امام_رضا'], [4, 'امام صادق(ع)', '#امام_صادق'],
  [5, 'امام علی(ع)', '#امام_علی'], [6, 'اردستان', '#اردستان'],
  [7, 'برخوار', '#برخوار'], [8, 'بویین و میاندشت', '#بویین_و_میاندشت'],
  [9, 'تیران و کرون', '#تیران_و_کرون'], [10, 'جرقویه', '#جرقویه'],
  [11, 'چادگان', '#چادگان'], [12, 'خمینی شهر', '#خمینی_شهر'],
  [13, 'خوانسار', '#خوانسار'], [14, 'خور و بیابانک', '#خور_و_بیابانک'],
  [15, 'درچه', '#درچه'], [16, 'دهاقان', '#دهاقان'],
  [17, 'سمیرم', '#سمیرم'], [18, 'شاهین شهر', '#شاهین_شهر'],
  [19, 'شهرضا', '#شهرضا'], [20, 'فریدن', '#فریدن'],
  [21, 'فریدون شهر', '#فریدون_شهر'], [22, 'فلاورجان', '#فلاورجان'],
  [23, 'کاشان', '#کاشان'], [24, 'کوهپایه', '#کوهپایه'],
  [25, 'گلپایگان', '#گلپایگان'], [26, 'لنجان', '#لنجان'],
  [27, 'مبارکه', '#مبارکه'], [28, 'نایین', '#نایین'],
  [29, 'نجف آباد', '#نجف_آباد'], [30, 'نطنز', '#نطنز'],
  [31, 'ورزنه', '#ورزنه'], [32, 'هرند', '#هرند']
];

var TXT = {
  WELCOME_UNREGISTERED: 'سلام 👋\nبه بات گزارش‌گیری معاونت نسرا (استان اصفهان) خوش آمدید.\n\nبرای ثبت‌نام و شناسایی، دکمه «📱 ارسال شماره من» را بزنید.\nشماره شما فقط برای شناسایی همکاران است و فقط ادمین‌ها آن را می‌بینند.',
  SETUP_NO_SUPERADMIN: '🔧 بات در حالت راه‌اندازی است و هنوز مدیر اصلی (سوپرادمین) ثبت نشده است.\nاگر کد راه‌اندازی را دارید (برگه Settings در گوگل‌شیت یا Execution log)، این‌طور بنویسید:\n/claim کد',
  CLAIM_OK: '🎉 تبریک! شما سوپرادمین (مدیر اصلی) بات شدید.\n\nحالا قدم‌به‌قدم راه‌اندازی را کامل می‌کنیم. قدم ۱ از ۳:',
  CLAIM_BAD: '❌ کد راه‌اندازی درست نیست یا قبلاً استفاده شده است.',
  CLAIM_ALREADY: 'ℹ️ سوپرادمین قبلاً ثبت شده است و کد راه‌اندازی دیگر کار نمی‌کند.',
  SETUP_STEP1: '📌 قدم ۱: گروه بررسی ادمین‌ها\n\n۱) یک گروه تلگرامی برای بررسی گزارش‌ها بسازید (یا از گروه آماده استفاده کنید).\n۲) این بات را به آن گروه اضافه کنید و بات را «ادمین گروه» کنید.\n۳) در همان گروه بنویسید و ارسال کنید:\n/here\n\nبات خودش آیدی گروه را ذخیره می‌کند.',
  SETUP_STEP2: '📌 قدم ۲: کانال انتشار\n\n۱) کانال انتشار گزارش‌ها را بسازید (یا کانال آماده).\n۲) بات را در کانال «ادمین» کنید با اجازه‌های: ارسال پیام، ویرایش پیام و حذف پیام.\n۳) یک پست از همان کانال را همین‌جا برای من «فوروارد» کنید.\n\nبات آیدی کانال را از پیام فورواردشده می‌خواند.',
  SETUP_STEP3: '📌 قدم ۳: تست کامل راه‌اندازی\n\nدکمه زیر را بزنید؛ بات یک پیام آزمایشی به گروه و کانال می‌فرستد و حذف می‌کند و نتیجه را به شما می‌گوید.',
  SETUP_DONE: '✅ راه‌اندازی کامل شد! بات آماده کار است.\n\nقدم‌های بعدی:\n۱) در گوگل‌شیت، برگه Whitelist را با شماره و نام و شهرستان همکاران پر کنید.\n۲) از چند همکار آزمایشی بخواهید /start بزنند و یک گزارش تستی ثبت کنند.\n۳) وضعیت را هر وقت خواستید با /status ببینید.',
  GROUP_SAVED: '✅ گروه بررسی ذخیره شد. حالا به چت خصوصی بات برگردید و قدم بعدی را انجام دهید.',
  CHANNEL_FORWARD_BAD: '⚠️ از این پیام نتوانستم کانال را تشخیص بدهم.\nلطفاً دقیقاً یک «پست کانال» را فوروارد کنید (نه پیام کاربر یا گروه).',
  SEND_PHONE_BY_BUTTON: '⚠️ شماره را نمی‌توانید تایپ کنید.\nلطفاً دکمه «📱 ارسال شماره من» پایین صفحه را بزنید تا شماره تلگرام خودتان ارسال شود.',
  CONTACT_NOT_YOURS: '❌ این شماره متعلق به حساب تلگرام شما نیست و پذیرفته نمی‌شود.\nلطفاً با دکمه «📱 ارسال شماره من» شماره خودتان را ارسال کنید.',
  PHONE_INVALID: '❌ شماره دریافت‌شده معتبر نیست. لطفاً دوباره با دکمه «📱 ارسال شماره من» تلاش کنید.',
  PHONE_USED_BY_OTHER: '❌ این شماره قبلاً با یک حساب تلگرام دیگر ثبت شده است.\nاگر مشکلی هست با ادمین در تماس باشید.',
  ASK_CITY: 'شماره شما در لیست مجاز نبود؛ مشکلی نیست 🙂\nلطفاً شهرستان خود را انتخاب کنید:',
  ASK_NAME: 'نام و نام‌خانوادگی خود را بنویسید (مثلاً: محمد احمدی):',
  REG_SENT: '✅ درخواست ثبت‌نام شما ارسال شد.\nپس از تایید ادمین، به شما پیام می‌دهم و منوی اصلی باز می‌شود. لطفاً صبور باشید 🙏',
  REG_WAIT: '⏳ درخواست ثبت‌نام شما هنوز در انتظار تایید ادمین است. پس از تایید به شما اطلاع می‌دهم.',
  REG_REJECTED: '❌ متأسفانه درخواست ثبت‌نام شما رد شد.\nاگر فکر می‌کنید اشتباهی شده، با ادمین صحبت کنید و دوباره /start را بزنید.',
  USER_DISABLED: '⛔️ حساب شما غیرفعال شده است. برای پیگیری با ادمین در تماس باشید.',
  NO_ACCESS: '⛔️ شما به این بخش دسترسی ندارید.',
  GENERIC_ERROR: '⚠️ یک خطای غیرمنتظره رخ داد. لطفاً چند دقیقه دیگر دوباره تلاش کنید؛ اگر تکرار شد به ادمین اطلاع دهید.',
  CANCELLED: 'لغو شد. از منوی اصلی انتخاب کنید:',
  FORM_START: '📝 ارسال گزارش\n\nقدم ۱ از ۱۱ — عکس‌ها 📷\n۱ تا ۵ عکس برنامه را بفرستید (تکی یا آلبوم).\nوقتی تمام شد دکمه «✅ پایان ارسال عکس» را بزنید.',
  PHOTO_NEED_ONE: '⚠️ حداقل یک عکس لازم است.',
  SEND_AS_PHOTO: '⚠️ لطفاً عکس را به‌صورت «عکس» بفرستید، نه فایل/سند.',
  ASK_CLASS: 'قدم ۲ از ۱۱ — نوع کلاس/برنامه را انتخاب کنید:',
  ASK_OPERATION: 'قدم ۳ از ۱۱ — نام عملیات را بنویسید؛ اگر ندارد دکمه «ندارد» را بزنید:',
  ASK_SUBJECT: 'قدم ۴ از ۱۱ — موضوع برنامه را بنویسید:',
  ASK_TEACHER: 'قدم ۵ از ۱۱ — مدرس یا سخنران را انتخاب کنید (اجباری). اگر در لیست نیست «➕ مدرس جدید»:',
  ASK_TEACHER_NEW: 'نام و نام‌خانوادگی مدرس جدید را بنویسید:',
  ASK_DATE: 'قدم ۶ از ۱۱ — تاریخ برگزاری:',
  ASK_YEAR: 'سال برگزاری (شمسی) را انتخاب کنید:',
  ASK_MONTH: 'ماه برگزاری را انتخاب کنید:',
  ASK_DAY: 'روز برگزاری را انتخاب کنید:',
  DATE_FUTURE: '⚠️ تاریخ برگزاری نمی‌تواند در آینده باشد.',
  ASK_HOUR: 'قدم ۷ از ۱۱ — ساعت برگزاری را انتخاب کنید (۰۰ تا ۲۳):',
  ASK_MIN: 'دقیقه را انتخاب کنید:',
  ASK_DURATION: 'قدم ۸ از ۱۱ — مدت زمان برگزاری (دقیقه):',
  ASK_PARTICIPANTS: 'قدم ۹ از ۱۱ — تعداد شرکت‌کنندگان را با عدد بنویسید (۱ تا ۵۰۰۰):',
  PARTICIPANTS_BAD: '⚠️ عدد معتبر نیست. تعداد شرکت‌کنندگان را بین ۱ تا ۵۰۰۰ با عدد بنویسید:',
  ASK_AUDIENCE: 'قدم ۱۰ از ۱۱ — نوع مخاطبین:',
  ASK_PLACE: 'قدم ۱۱ از ۱۱ — بستر / مکان برگزاری را بنویسید (چند خط هم می‌شود):',
  PREVIEW_TITLE: '👀 پیش‌نمایش گزارش — اگر درست است «ارسال برای بررسی»، وگرنه «✏️ ویرایش»:',
  EDIT_ASK: 'کدام بخش را می‌خواهید ویرایش کنید؟',
  REPORT_SENT: '✅ گزارش ثبت شد و برای بررسی ارسال شد. نتیجه را همین‌جا به شما اعلام می‌کنم.',
  DECIDED_ALREADY: 'قبلاً درباره این گزارش تصمیم گرفته شده است.',
  REJECT_ASK: 'دلیل‌های رد را انتخاب کنید (چندانتخابی) و بعد «ثبت رد» را بزنید:',
  REJECT_MANUAL: '✉️ متن دلیل رد را در همین گروه بنویسید و ارسال کنید (پیام بعدی شما به‌عنوان دلیل ثبت می‌شود):',
  REJECT_NONE: 'حداقل یک دلیل انتخاب کنید یا توضیح دستی بنویسید.'
};

function getProp_(key) {
  return PropertiesService.getScriptProperties().getProperty(key);
}
function setProp_(key, value) {
  PropertiesService.getScriptProperties().setProperty(key, String(value));
}

// ---------- نرمال‌سازی ارقام، شماره، نام ----------
function toEnDigits_(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[۰-۹]/g, function (c) { return '۰۱۲۳۴۵۶۷۸۹'.indexOf(c); })
    .replace(/[٠-٩]/g, function (c) { return '٠١٢٣٤٥٦٧٨٩'.indexOf(c); });
}
function normalizePhone_(raw) {
  if (raw === null || raw === undefined) return null;
  var s = toEnDigits_(raw).trim().replace(/[\s\-()]+/g, '');
  if (s.indexOf('+98') === 0) s = '0' + s.slice(3);
  else if (s.indexOf('0098') === 0) s = '0' + s.slice(4);
  else if (s.indexOf('98') === 0 && s.length === 12) s = '0' + s.slice(2);
  else if (s.indexOf('9') === 0 && s.length === 10) s = '0' + s;
  return /^09\d{9}$/.test(s) ? s : null;
}
function normalizeName_(raw) {
  if (raw === null || raw === undefined) return '';
  return String(raw).replace(/ي/g, 'ی').replace(/ك/g, 'ک').replace(/ة/g, 'ه').replace(/ؤ/g, 'و')
    .replace(/[ \t]+/g, ' ').trim();
}
function normCmp_(raw) {
  return normalizeName_(raw).replace(/‌/g, ' ').replace(/\s+/g, ' ').toLowerCase();
}
function parseIntFa_(raw) {
  var s = toEnDigits_(raw).trim().replace(/[,\u066C]/g, '');
  return /^\d+$/.test(s) ? parseInt(s, 10) : null;
}
function validHashtag_(tag) {
  if (!tag) return false;
  tag = String(tag).trim();
  if (tag.charAt(0) !== '#' || tag.length < 2) return false;
  if (tag.indexOf('#', 1) !== -1) return false;
  return !/[\s()]/.test(tag);
}
