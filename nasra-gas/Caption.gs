// Caption.gs — کپشن کانال جداگانه برای هر نوع برنامه + تعریف قدم‌های فرم هر نوع.
// همه‌چیزِ وابسته به «نوع» (قدم‌های فرم، برچسب‌ها، کپشن، بخش‌های ویرایش پنل) فقط از همین‌جا خوانده می‌شود تا همیشه هماهنگ بمانند.
// فقط بات کپشن می‌سازد و هنگام ارسال parse_mode نمی‌فرستد (متن خام).

// گزارش‌های ثبت‌شده پیش از کپشن‌های جداگانه (ستون form_v خالی) با همان قالب قدیمی نمایش و ویرایش می‌شوند.
var FORM_VERSION = '2';

// زیرشاخه هر نوع — ترتیب را عوض نکنید (دکمه‌ها با شماره کار می‌کنند)؛ اضافه کردن به آخر فهرست مشکلی ندارد.
var CLASS_SUBS = {
  'توانمندسازی': ['حضوری', 'مجازی'],
  'خلاقانه': ['#انتشار_شبکه_اجتماعی', '#چاپ_بنر', '#بروشور', '#نماز_جمعه', '#نمایشگاه', '#تلویزیون_شهری', '#صدا_و_سیما'],
  'تولیدات': ['#صفحه_word', '#اسلاید', '#کلیپ', '#موشن_گرافیک', '#اینفوگرافیک', '#پوستر', '#سایر']
};
var ONLINE_PLATFORMS = ['اسکای روم', 'ایتا', 'بله', 'روبیکا', 'آپارات', 'اینستاگرام']; // بستر برگزاری کلاس مجازی (همکار می‌تواند بنویسد)
var SOCIAL_PLATFORMS = ['اینستاگرام', 'ایتا', 'روبیکا', 'بله', 'تلگرام', 'آپارات']; // بستر انتشار «انتشار شبکه اجتماعی»
var MAX_VIEWS = 10000000;

var FLOW_INPERSON = ['operation', 'subject', 'teacher', 'date', 'hour', 'duration', 'participants', 'audience', 'place'];
var FLOW_ONLINE = ['subject', 'teacher', 'date', 'duration', 'participants', 'audience', 'platform', 'link'];
var FLOW_EMPOWER_ONLINE = ['subject', 'teacher', 'date', 'hour', 'duration', 'participants', 'audience', 'platform', 'link'];
var STEP_PARENT = { teacherNew: 'teacher', year: 'date', month: 'date', day: 'date', minute: 'hour' };

function subLabel_(sub) { return String(sub || '').replace(/^#/, '').replace(/_/g, ' '); }
function hasSubs_(type) { return !!CLASS_SUBS[type]; }
function validSub_(type, sub) { return !!CLASS_SUBS[type] && CLASS_SUBS[type].indexOf(String(sub || '')) !== -1; }
function isOnlineClass_(type, sub) { return type === 'مجازی' || (type === 'توانمندسازی' && sub === 'مجازی'); }
function isV2_(rep) { return String(cellToStr_(rep && rep.form_v) || '') === FORM_VERSION; }

// قدم‌های بعد از «نوع کلاس/برنامه» برای هر نوع
function typeFlow_(type, sub) {
  if (type === 'مجازی') return FLOW_ONLINE;
  if (type === 'توانمندسازی') return ['sub'].concat(sub === 'مجازی' ? FLOW_EMPOWER_ONLINE : FLOW_INPERSON);
  if (type === 'خلاقانه') return ['sub', 'subject', 'date', 'views', 'platform', 'link'];
  if (type === 'تولیدات') return ['sub', 'teacher', 'subject']; // تاریخ = روز ارسال (خودکار)
  return FLOW_INPERSON; // حضوری
}
function formFlow_(d) { return ['photos', 'class'].concat(d && d.class_type ? typeFlow_(d.class_type, d.class_sub) : []); }
function stepFilled_(d, step) {
  switch (step) {
    case 'photos': return (d.photos || []).length > 0;
    case 'class': return CFG.CLASS_TYPES.indexOf(String(d.class_type || '')) !== -1;
    case 'sub': return validSub_(d.class_type, d.class_sub);
    case 'operation': return d.operation_name !== undefined && d.operation_name !== null;
    case 'subject': return !!String(d.subject || '').trim();
    case 'teacher': return !!String(d.instructor_name || '').trim();
    case 'date': return num_(d.event_year) > 0 && num_(d.event_month) > 0 && num_(d.event_day) > 0;
    case 'hour': return /^\d\d:\d\d$/.test(String(d.event_time || ''));
    case 'duration': return num_(d.duration_min) > 0;
    case 'participants': case 'views': return num_(d.participants) > 0;
    case 'audience': return !!d.audience;
    case 'place': case 'platform': return !!String(d.place || '').trim();
    case 'link': return d.link !== undefined && d.link !== null; // '' یعنی «ندارد»
  }
  return true;
}
function firstMissing_(d) {
  var f = formFlow_(d);
  if (!d.class_type) return stepFilled_(d, 'photos') ? 'class' : 'photos';
  for (var i = 0; i < f.length; i++) if (!stepFilled_(d, f[i])) return f[i];
  return '';
}
function nextStep_(d, step) {
  var f = formFlow_(d), i = f.indexOf(STEP_PARENT[step] || step);
  return i === -1 || i + 1 >= f.length ? 'preview' : f[i + 1];
}
function prevStep_(d, step) {
  if (step === 'teacherNew') return 'teacher';
  if (step === 'year') return 'date';
  if (step === 'month') return 'year';
  if (step === 'day') return 'month';
  if (step === 'minute') return 'hour';
  var f = formFlow_(d), i = f.indexOf(step);
  if (step === 'preview') return f[f.length - 1];
  return i > 0 ? f[i - 1] : 'photos';
}
// «قدم ۴ از ۱۱» — تا وقتی نوع انتخاب نشده تعداد کل معلوم نیست
function stepNo_(d, step) {
  var f = formFlow_(d), i = f.indexOf(STEP_PARENT[step] || step);
  var k = faNum_(i + 1);
  return d && d.class_type ? 'قدم ' + k + ' از ' + faNum_(f.length) : 'قدم ' + k;
}
function faNum_(n) { return String(n).replace(/\d/g, function (c) { return '۰۱۲۳۴۵۶۷۸۹'.charAt(+c); }); }
// برچسب هر بخش (دکمه‌های ویرایش همکار و پنل ادمین)
function stepLabel_(type, step) {
  var L = {
    photos: '📷 عکس‌ها', class: '⭕️ نوع کلاس/برنامه',
    sub: type === 'توانمندسازی' ? '🔀 حضوری یا مجازی' : type === 'تولیدات' ? '🎞 نوع محتوا' : '🎨 بخش خلاقانه',
    operation: '💢 نام عملیات', subject: type === 'تولیدات' ? '📄 موضوع محتوا' : '📄 موضوع',
    teacher: type === 'تولیدات' ? '👨‍🏫 تولید کننده' : '👨‍🏫 مدرس/سخنران',
    date: type === 'خلاقانه' ? '📆 تاریخ انتشار' : '📆 تاریخ برگزاری', hour: '⏰ ساعت برگزاری', duration: '⏳ مدت زمان',
    participants: '🚻 تعداد شرکت‌کنندگان', views: '👁 تعداد بازدید', audience: '👥 نوع مخاطبین',
    place: '🏫 مکان برگزاری', platform: type === 'خلاقانه' ? '📢 بستر انتشار' : '💻 بستر برگزاری',
    link: type === 'خلاقانه' ? '🔗 لینک پست' : '🔗 لینک برگزاری'
  };
  return L[step] || step;
}
// نام کامل نوع برای پیام‌ها و کارت گزارش: «خلاقانه — چاپ بنر»
function classFullLabel_(type, sub) { return String(type || '') + (sub ? ' — ' + subLabel_(sub) : ''); }
// لینک: آدرس اینترنتی یا t.me ؛ خروجی: {link} یا {error}
function parseLinkInput_(raw) {
  var t = toEnDigits_(String(raw || '')).trim();
  if (/^(ندارد|نداره|ندارم|-|—)$/.test(t)) return { link: '' };
  t = t.replace(/\s+/g, '');
  if (!t || t.length > 300) return { error: 'لینک معتبر نیست.' };
  if (!/^(https?:\/\/)?([\w\u0600-\u06FF-]+\.)+[a-z\u0600-\u06FF]{2,}(\/\S*)?$/i.test(t)) return { error: 'لینک معتبر نیست؛ لینک را کامل کپی کنید (مثلاً https://…) یا «ندارد» را بزنید.' };
  return { link: t };
}

// ---------- کپشن‌ها ----------
function capLine_(label, v) {
  v = String(v === undefined || v === null ? '' : v).trim();
  return v.indexOf('\n') !== -1 ? label + ':\n' + v : label + ': ' + v; // متن چندخطی (مثلاً مکان) از خط بعد
}
function buildCaption_(f) {
  // f: publish_jalali, city_hashtag, class_type, class_sub, operation_name, subject, instructor_name, event_year, event_month,
  //    event_day, event_time, duration_min, participants, audience, place, link
  var t = String(f.class_type || ''), sub = String(f.class_sub || '');
  var date = formatJalali_(num_(f.event_year), num_(f.event_month), num_(f.event_day));
  var L = [
    capLine_('📅 تاریخ درج در کانال', f.publish_jalali),
    '✅ نام استان: #اصفهان',
    '✅ معاونت: #نسرا',
    '🗓 ماه: #' + monthTag_(num_(f.event_year), num_(f.event_month)),
    capLine_('📍 ناحیه برگزار کننده', f.city_hashtag)
  ];
  var link = f.link ? String(f.link) : 'ندارد';
  if (t === 'خلاقانه') {
    L.push(capLine_('⭕️ نوع فعالیت', '#خلاقانه ' + sub), capLine_('📄 موضوع', f.subject), capLine_('📆 تاریخ انتشار', date),
      capLine_('👁 تعداد بازدید', f.participants), capLine_('📢 بستر انتشار', f.place), capLine_('🔗 لینک پست', link));
  } else if (t === 'تولیدات') {
    L.push('⭕️ نوع فعالیت: #تولیدات', capLine_('👨‍🏫 تولید کننده', f.instructor_name), capLine_('📄 موضوع محتوا', f.subject),
      capLine_('🎞 نوع محتوا', sub));
  } else if (isOnlineClass_(t, sub)) {
    L.push(capLine_('⭕️ نوع کلاس', t === 'مجازی' ? '#مجازی' : '#توانمندسازی (مجازی)'), capLine_('📄 موضوع', f.subject),
      capLine_('👨‍🏫 مدرس یا سخنران', f.instructor_name), capLine_('📆 تاریخ برگزاری', date));
    if (t === 'توانمندسازی') L.push(capLine_('⏰ ساعت برگزاری', f.event_time));
    L.push(capLine_('⏳ مدت زمان برگزاری', f.duration_min + ' دقیقه'), capLine_('🚻 تعداد نفرات شرکت کننده', f.participants + ' نفر'),
      capLine_('👥 نوع مخاطبین', '#' + f.audience), capLine_('💻 بستر برگزاری', f.place), capLine_('🔗 لینک برگزاری', link));
  } else { // حضوری و توانمندسازی حضوری
    L.push(capLine_('⭕️ نوع کلاس/ برنامه', t === 'توانمندسازی' ? '#توانمندسازی (حضوری)' : '#' + t),
      capLine_('💢 نام عملیات', f.operation_name || 'ندارد'), capLine_('📄 موضوع', f.subject),
      capLine_('👨‍🏫 مدرس یا سخنران', f.instructor_name), capLine_('📆 تاریخ برگزاری', date),
      capLine_('⏰ ساعت برگزاری', f.event_time), capLine_('⏳ مدت زمان برگزاری', f.duration_min + ' دقیقه'),
      capLine_('🚻 تعداد نفرات شرکت کننده', f.participants + ' نفر'), capLine_('👥 نوع مخاطبین', '#' + f.audience),
      capLine_('🏫 بستر / مکان برگزاری', f.place));
  }
  return L.join('\n');
}

// قالب قدیمی (گزارش‌های پیش از کپشن‌های جداگانه) — دست نزنید تا ویرایش پست‌های قدیمی همان شکل بماند
function buildCaptionV1_(f) {
  var lines = [
    'تاریخ درج در کانال :' + f.publish_jalali,
    '⚜️ نام استان : #اصفهان',
    '✅  معاونت : #نسرا',
    '🗓  ماه : #' + monthTag_(num_(f.event_year), num_(f.event_month)),
    '⚜️ ناحیه برگزار کننده : ' + f.city_hashtag,
    'نوع کلاس/ برنامه:  #' + f.class_type,
    '💢 نام عملیات : ' + (f.operation_name || ''),
    '📄 موضوع : ' + f.subject,
    '👨‍🏫مدرس یا سخنران : ' + f.instructor_name,
    'تاریخ برگزاری:' + formatJalali_(num_(f.event_year), num_(f.event_month), num_(f.event_day)),
    '⏰ساعت برگزاری : ' + f.event_time,
    '⏳مدت زمان برگزاری :' + f.duration_min + ' دقیقه',
    '🚻 تعداد نفرات شرکت کننده :' + f.participants + ' نفر',
    '👥 نوع مخاطبین : #' + f.audience,
    '⭕️ بستر / مکان برگزاری : ',
    String(f.place || '')
  ];
  return lines.join('\n');
}

function captionFields_(src, cityHashtag, publishJalali) {
  return {
    publish_jalali: publishJalali, city_hashtag: cityHashtag,
    class_type: src.class_type, class_sub: src.class_sub || '', operation_name: src.operation_name || '',
    subject: src.subject, instructor_name: src.instructor_name,
    event_year: src.event_year, event_month: src.event_month, event_day: src.event_day,
    event_time: src.event_time, duration_min: src.duration_min,
    participants: src.participants, audience: src.audience, place: src.place, link: src.link || ''
  };
}
function captionFromData_(data, cityHashtag, publishJalali) { // فرم همکار (همیشه قالب تازه)
  return buildCaption_(captionFields_(data, cityHashtag, publishJalali));
}
function captionFromReport_(rep, publishJalali) {
  var city = getCityById_(rep.city_id), f = captionFields_(rep, city ? city.hashtag : '', publishJalali);
  return isV2_(rep) ? buildCaption_(f) : buildCaptionV1_(f);
}
