// Admin.gs — فاز ۲: پنل مدیریت داخل بات (فقط ادمین‌ها، فقط در چت خصوصی) با دستور /admin
// بخش‌ها: گزارش‌های در انتظار، منتشرشده‌ها (ویرایش کپشن / حذف از کانال)، جستجوی گزارش با شماره،
// آمار ماهانه شهرستان‌ها، کاربران (فعال/غیرفعال و تغییر شهرستان)، مدرسان (افزودن/تغییر نام/غیرفعال)
// و ادمین‌ها (فقط سوپرادمین: افزودن/حذف دسترسی).
// همه کال‌بک‌های پنل با «A:» شروع می‌شوند (کوتاه‌تر از ۶۴ بایت). حالت‌های ورود متن با «adm:» در Sessions.

var ADM_PER_PAGE = 8;

var USER_STATUS_FA = { pending: 'در انتظار تایید', approved: 'فعال ✅', rejected: 'ردشده ❌', disabled: 'غیرفعال ⛔️' };

// [کد فیلد، برچسب دکمه، نوع ورودی]
var REPORT_EDIT_FIELDS = [
  ['subject', '📄 موضوع', 'text'], ['operation', '💢 نام عملیات', 'text'],
  ['teacher', '👨‍🏫 مدرس/سخنران', 'text'], ['date', '🗓 تاریخ برگزاری', 'text'],
  ['time', '⏰ ساعت برگزاری', 'text'], ['participants', '🚻 تعداد نفرات', 'text'],
  ['place', '⭕️ مکان برگزاری', 'text'], ['class', 'نوع کلاس/برنامه', 'btn'],
  ['audience', '👥 نوع مخاطبین', 'btn'], ['duration', '⏳ مدت زمان', 'btn']
];
var REPORT_EDIT_PROMPTS = {
  subject: 'موضوع جدید را بنویسید:',
  operation: 'نام عملیات جدید را بنویسید.\n(برای خالی‌کردن «نام عملیات» فقط یک خط تیره - بفرستید)',
  teacher: 'نام و نام‌خانوادگی مدرس/سخنران را بنویسید:',
  date: 'تاریخ برگزاری را به شکل 1405/07/23 بنویسید (تاریخ آینده پذیرفته نمی‌شود):',
  time: 'ساعت برگزاری را به شکل 21:00 بنویسید:',
  participants: 'تعداد شرکت‌کنندگان را با عدد بنویسید (۱ تا ۵۰۰۰):',
  place: 'بستر / مکان برگزاری جدید را بنویسید:'
};

// ---------- ابزارهای خالص (در runTests تست می‌شوند) ----------
function parseJalaliInput_(raw) {
  var s = toEnDigits_(raw).trim().replace(/[\-.\s\u066B\u060C]+/g, '/').replace(/\/+/g, '/');
  var m = /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/.exec(s);
  if (!m) return null;
  var jy = parseInt(m[1], 10), jm = parseInt(m[2], 10), jd = parseInt(m[3], 10);
  if (jy < 1300 || jy > 1500 || !isValidJalali_(jy, jm, jd)) return null;
  return { jy: jy, jm: jm, jd: jd };
}
function parseTimeInput_(raw) {
  var s = toEnDigits_(raw).trim().replace(/[.\u066B\uFF1A]/g, ':');
  var m = /^(\d{1,2}):(\d{2})$/.exec(s);
  if (!m) return null;
  var h = parseInt(m[1], 10), mi = parseInt(m[2], 10);
  if (h > 23 || mi > 59) return null;
  return pad2_(h) + ':' + pad2_(mi);
}
// آمار تاییدشده‌های یک ماه (بر اساس تاریخ برگزاری) برای هر شهرستان
function monthStats_(reports, cities, jy, jm) {
  var by = {};
  cities.forEach(function (c) { by[num_(c.city_id)] = { name: c.name, count: 0, people: 0 }; });
  var total = 0, people = 0;
  reports.forEach(function (r) {
    if (r.status !== 'approved' || num_(r.event_year) !== jy || num_(r.event_month) !== jm) return;
    var b = by[num_(r.city_id)];
    if (!b) return;
    b.count++; b.people += num_(r.participants);
    total++; people += num_(r.participants);
  });
  var rows = Object.keys(by).map(function (k) { return by[k]; });
  var active = rows.filter(function (r) { return r.count > 0; })
    .sort(function (a, b) { return b.count - a.count || b.people - a.people; });
  var zero = rows.filter(function (r) { return r.count === 0; }).map(function (r) { return r.name; });
  return { rows: active, zero: zero, total: total, people: people };
}
function prevJalaliMonth_(jy, jm) { return jm === 1 ? { jy: jy - 1, jm: 12 } : { jy: jy, jm: jm - 1 }; }
function nextJalaliMonth_(jy, jm) { return jm === 12 ? { jy: jy + 1, jm: 1 } : { jy: jy, jm: jm + 1 }; }
function cut_(s, n) { s = String(s || ''); return s.length > n ? s.slice(0, n - 1) + '…' : s; }

// ---------- ورود به پنل ----------
function handleAdminCommand_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id;
  if (!isAdminTg_(tg)) { tgSend_(chatId, TXT.NO_ACCESS); return; }
  var s = getSession_(chatId, tg);
  if (String(s.state || '').indexOf('adm:') === 0) clearSession_(chatId, tg);
  showAdminPanel_(chatId, tg, 0);
}
function adminPanelText_() {
  var t = todayJalali_(), reports = readTable_('Reports'), pending = 0, month = 0;
  reports.forEach(function (r) {
    if (r.status === 'pending') pending++;
    if (r.status === 'approved' && num_(r.event_year) === t.jy && num_(r.event_month) === t.jm) month++;
  });
  return '🛠 پنل مدیریت بات نسرا\n' +
    '• گزارش‌های در انتظار بررسی: ' + pending + '\n' +
    '• گزارش‌های تاییدشده ' + JALALI_MONTHS[t.jm - 1] + ' ' + t.jy + ': ' + month + '\n\n' +
    'یک بخش را انتخاب کنید:';
}
function adminPanelKb_(tg) {
  var t = todayJalali_();
  var rows = [
    [btn_('⏳ در انتظار بررسی', 'A:p'), btn_('📢 منتشرشده‌ها', 'A:rl')],
    [btn_('🔎 گزارش با شماره', 'A:rf'), btn_('📊 آمار ماهانه', 'A:s:' + t.jy + ':' + t.jm)],
    [btn_('👤 کاربران', 'A:u'), btn_('👨‍🏫 مدرسان', 'A:t:0')]
  ];
  if (isSuperTg_(tg)) rows.push([btn_('🛡 ادمین‌ها', 'A:a')]);
  rows.push([btn_('✖️ بستن پنل', 'A:x')]);
  return ikb_(rows);
}
function showAdminPanel_(chatId, tg, editId) { panelOut_(chatId, editId, adminPanelText_(), adminPanelKb_(tg)); }
function panelOut_(chatId, editId, text, kb) {
  var res = null;
  if (editId) res = tgEdit_(chatId, editId, text, kb);
  if (!res) res = tgSend_(chatId, text, kb);
  return res;
}
function homeRow_() { return [btn_('🏠 پنل اصلی', 'A:m')]; }

// ---------- مسیریاب دکمه‌های پنل ----------
function handleAdminCb_(cb, s, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id, mid = cb.message.message_id;
  if (!isAdminTg_(tg)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  if (!cb.message.chat || cb.message.chat.type !== 'private') { tgAnswerCb_(cb.id, 'پنل مدیریت فقط در چت خصوصی بات کار می‌کند.', true); return; }
  // هر کلیک روی پنل، حالت «منتظر متن» قبلی را لغو می‌کند؛ اگر لازم باشد، خود دکمه حالت تازه می‌سازد
  if (String(s.state || '').indexOf('adm:') === 0) { s.state = ''; s.data = {}; saveSession_(chatId, tg, s); }
  var parts = data.split(':'), p = parts[1], a = parts[2], b = parts[3], c = parts[4];

  if (p === 'm') { tgAnswerCb_(cb.id); showAdminPanel_(chatId, tg, mid); return; }
  if (p === 'x') { tgAnswerCb_(cb.id); tgEdit_(chatId, mid, 'پنل مدیریت بسته شد. برای باز کردن دوباره: /admin', null); return; }

  // گزارش‌ها
  if (p === 'p') { tgAnswerCb_(cb.id); showPendingList_(chatId, mid); return; }
  if (p === 'rl') { tgAnswerCb_(cb.id); showPublishedList_(chatId, mid); return; }
  if (p === 'rf') {
    s.state = 'adm:findReport'; s.data = {}; saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '🔎 شماره گزارش را بنویسید و بفرستید.\n(شماره گزارش در پیام «گزارش جدید برای بررسی» در گروه بررسی نوشته شده است)', ikb_([homeRow_()]));
    return;
  }
  if (p === 'r') { tgAnswerCb_(cb.id); showReportCard_(chatId, num_(a), mid); return; }
  if (p === 're') {
    var rep = getReport_(num_(a));
    if (!rep || rep.status !== 'approved') { tgAnswerCb_(cb.id, 'فقط گزارش «تایید‌شده» را می‌توان ویرایش کرد.', true); return; }
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '✏️ ویرایش کپشن گزارش ' + a + ' در کانال\nکدام بخش عوض شود؟\n(عکس‌ها قابل تغییر نیستند؛ برای عوض‌کردن عکس، گزارش را حذف کنید تا کارمند دوباره ثبت کند)', reportEditKb_(num_(a)));
    return;
  }
  if (p === 'rx') { tgAnswerCb_(cb.id); startReportFieldEdit_(chatId, tg, s, num_(a), b, mid); return; }
  if (p === 'rv') {
    var patch = reportPatchFor_(getReport_(num_(a)), b, num_(c), tg);
    if (patch.error) { tgAnswerCb_(cb.id, patch.error, true); return; }
    tgAnswerCb_(cb.id, 'در حال ویرایش پست کانال…');
    var res = applyReportEdit_(num_(a), patch.patch);
    tgSend_(chatId, res.ok ? '✅ کپشن پست کانال به‌روز شد.' : '⚠️ ' + res.msg);
    showReportCard_(chatId, num_(a), 0);
    return;
  }
  if (p === 'rd') {
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '🗑 حذف گزارش ' + a + ' از کانال؟\nپست (همه عکس‌ها) از کانال پاک می‌شود، وضعیت گزارش «حذف‌شده» می‌شود و به کارمند اطلاع داده می‌شود.\nاین کار برگشت ندارد.',
      ikb_([[btn_('✅ بله، حذف کن', 'A:rD:' + a)], [btn_('↩️ نه، برگرد', 'A:r:' + a)]]));
    return;
  }
  if (p === 'rD') {
    var del = deletePublishedReport_(num_(a), tg);
    if (!del.ok) { tgAnswerCb_(cb.id, del.msg, true); return; }
    tgAnswerCb_(cb.id, 'حذف شد');
    var note = del.failed ? '\n⚠️ ' + del.failed + ' پیام از ' + del.total + ' پیام را بات نتوانست از کانال پاک کند (معمولاً پست‌های قدیمی‌تر از ۴۸ ساعت یا نبود اجازه «حذف پیام»)؛ لطفاً آن‌ها را دستی از کانال پاک کنید.' : '';
    panelOut_(chatId, mid, '🗑 گزارش ' + a + ' از کانال حذف شد و به کارمند اطلاع داده شد.' + note, ikb_([[btn_('📢 منتشرشده‌ها', 'A:rl')], homeRow_()]));
    return;
  }

  // آمار
  if (p === 's') { tgAnswerCb_(cb.id); showMonthStats_(chatId, num_(a), num_(b), mid); return; }

  // کاربران
  if (p === 'u') {
    s.state = 'adm:findUser'; s.data = {}; saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '👤 جستجوی کاربر\nشماره موبایل (مثل 09123456789)، بخشی از نام، یا آیدی عددی تلگرام را بفرستید:', ikb_([homeRow_()]));
    return;
  }
  if (p === 'uc') { tgAnswerCb_(cb.id); showUserCard_(chatId, num_(a), mid); return; }
  if (p === 'ut') { toggleUser_(cb, chatId, num_(a), mid); return; }
  if (p === 'uy') {
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '🏙 شهرستان جدید این کاربر را انتخاب کنید:', adminCitiesKb_('A:us:' + a + ':', 'A:uy:' + a + ':', num_(b), [[btn_('↩️ بازگشت', 'A:uc:' + a)]], null));
    return;
  }
  if (p === 'us') { setUserCity_(cb, chatId, num_(a), num_(b), mid); return; }

  // مدرسان
  if (p === 't') {
    tgAnswerCb_(cb.id);
    var counts = {};
    cachedTable_('instructors', 'Instructors').forEach(function (r) {
      if (String(r.is_active) !== '0') counts[num_(r.city_id)] = (counts[num_(r.city_id)] || 0) + 1;
    });
    panelOut_(chatId, mid, '👨‍🏫 مدیریت مدرسان\nشهرستان را انتخاب کنید (عدد داخل پرانتز = مدرسان فعال):',
      adminCitiesKb_('A:tc:', 'A:t:', num_(a), [homeRow_()], function (cid) { return ':0'; }, counts));
    return;
  }
  if (p === 'tc') { tgAnswerCb_(cb.id); showCityInstructors_(chatId, num_(a), num_(b), mid); return; }
  if (p === 'ti') { tgAnswerCb_(cb.id); showInstructorCard_(chatId, num_(a), mid); return; }
  if (p === 'tr') {
    var ins = getInstructorAny_(num_(a));
    if (!ins) { tgAnswerCb_(cb.id, 'مدرس پیدا نشد.', true); return; }
    s.state = 'adm:tRename'; s.data = { iid: num_(a) }; saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    panelOut_(chatId, mid, '✏️ نام فعلی: ' + ins.name + '\nنام جدید مدرس را بنویسید:\n(نام در گزارش‌های قبلی عوض نمی‌شود؛ فقط از این به بعد)', ikb_([[btn_('↩️ انصراف', 'A:ti:' + a)]]));
    return;
  }
  if (p === 'tt') { toggleInstructor_(cb, chatId, num_(a), mid); return; }
  if (p === 'ta') {
    s.state = 'adm:tAdd'; s.data = { cityId: num_(a) }; saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    var cty = getCityById_(num_(a));
    panelOut_(chatId, mid, '➕ افزودن مدرس به شهرستان ' + (cty ? cty.name : '') + '\nنام و نام‌خانوادگی مدرس را بنویسید:', ikb_([[btn_('↩️ انصراف', 'A:tc:' + a + ':0')]]));
    return;
  }

  // ادمین‌ها (فقط سوپرادمین)
  if (p === 'a' || p === 'aa' || p === 'ax' || p === 'aX') {
    if (!isSuperTg_(tg)) { tgAnswerCb_(cb.id, 'این بخش فقط برای سوپرادمین است.', true); return; }
    if (p === 'a') { tgAnswerCb_(cb.id); showAdminsList_(chatId, tg, mid); return; }
    if (p === 'aa') {
      s.state = 'adm:addAdmin'; s.data = {}; saveSession_(chatId, tg, s);
      tgAnswerCb_(cb.id);
      panelOut_(chatId, mid, '➕ افزودن ادمین جدید\n\nیکی از این دو راه:\n۱) آن شخص در همین بات /myid بزند و «آیدی عددی» را به شما بدهد؛ همان عدد را این‌جا بفرستید.\n۲) یا از 📎 ← Contact، مخاطب تلگرامی او را همین‌جا بفرستید.',
        ikb_([[btn_('↩️ انصراف', 'A:a')]]));
      return;
    }
    if (p === 'ax') {
      tgAnswerCb_(cb.id);
      var ad = findAdminRow_(num_(a));
      panelOut_(chatId, mid, '🚫 دسترسی ادمینِ «' + (ad ? ad.full_name : a) + '» برداشته شود؟', ikb_([[btn_('✅ بله، بردار', 'A:aX:' + a)], [btn_('↩️ نه', 'A:a')]]));
      return;
    }
    var rm = removeAdmin_(num_(a), tg);
    tgAnswerCb_(cb.id, rm.ok ? 'دسترسی برداشته شد' : rm.msg, !rm.ok);
    showAdminsList_(chatId, tg, mid);
    return;
  }
  tgAnswerCb_(cb.id);
}

// ---------- متن‌های ورودی در حالت‌های پنل ----------
function handleAdminText_(msg, s) {
  var chatId = msg.chat.id, tg = msg.from.id, text = cleanText_(msg.text);
  if (!isAdminTg_(tg)) { clearSession_(chatId, tg); tgSend_(chatId, TXT.NO_ACCESS); return; }
  var st = s.state, d = s.data || {};

  if (st === 'adm:findReport') {
    var rid = parseIntFa_(text.replace(/^#/, ''));
    if (!rid || !getReport_(rid)) { tgSend_(chatId, 'گزارشی با این شماره پیدا نشد. شماره را دوباره بنویسید یا /admin بزنید.'); return; }
    showReportCard_(chatId, rid, 0);
    return; // حالت می‌ماند تا بتوان شماره بعدی را هم فرستاد
  }
  if (st === 'adm:repField') {
    var rep = getReport_(num_(d.rid));
    var patch = reportPatchFor_(rep, d.field, text, tg);
    if (patch.error) { tgSend_(chatId, '⚠️ ' + patch.error + '\nدوباره بنویسید یا /admin بزنید.'); return; }
    clearSession_(chatId, tg);
    var res = applyReportEdit_(num_(d.rid), patch.patch);
    tgSend_(chatId, res.ok ? '✅ کپشن پست کانال به‌روز شد.' : '⚠️ ' + res.msg);
    showReportCard_(chatId, num_(d.rid), 0);
    return;
  }
  if (st === 'adm:findUser') {
    var hits = findUsers_(text);
    if (!hits.list.length) { tgSend_(chatId, 'کاربری پیدا نشد. دوباره جستجو کنید یا /admin بزنید.'); return; }
    var rows = hits.list.map(function (u) {
      var cty = getCityById_(u.city_id);
      return [btn_(cut_(u.full_name || 'بدون نام', 22) + ' · ' + (cty ? cty.name : '—') + ' · ' + (USER_STATUS_FA[u.status] || u.status), 'A:uc:' + u.user_id)];
    });
    rows.push(homeRow_());
    tgSend_(chatId, '👤 ' + hits.total + ' کاربر پیدا شد' + (hits.total > hits.list.length ? ' (' + hits.list.length + ' مورد اول نمایش داده شد؛ دقیق‌تر جستجو کنید)' : '') + ':', ikb_(rows));
    return; // حالت می‌ماند برای جستجوی بعدی
  }
  if (st === 'adm:tRename') {
    var r1 = renameInstructor_(num_(d.iid), text);
    if (!r1.ok) { tgSend_(chatId, '⚠️ ' + r1.msg + '\nدوباره بنویسید یا /admin بزنید.'); return; }
    clearSession_(chatId, tg);
    tgSend_(chatId, '✅ نام مدرس تغییر کرد.');
    showInstructorCard_(chatId, num_(d.iid), 0);
    return;
  }
  if (st === 'adm:tAdd') {
    var r2 = addInstructorAdmin_(num_(d.cityId), text, tg);
    if (!r2.ok) { tgSend_(chatId, '⚠️ ' + r2.msg + '\nدوباره بنویسید یا /admin بزنید.'); return; }
    clearSession_(chatId, tg);
    tgSend_(chatId, '✅ ' + r2.msg);
    showCityInstructors_(chatId, num_(d.cityId), 0, 0);
    return;
  }
  if (st === 'adm:addAdmin') {
    if (!isSuperTg_(tg)) { clearSession_(chatId, tg); tgSend_(chatId, TXT.NO_ACCESS); return; }
    var digits = toEnDigits_(text).trim();
    if (!/^\d{5,15}$/.test(digits)) { tgSend_(chatId, '⚠️ فقط «آیدی عددی» (همان عددی که /myid نشان می‌دهد) را بفرستید، یا مخاطب تلگرامی را از 📎 ارسال کنید.'); return; }
    finishAddAdmin_(chatId, tg, num_(digits), '');
    return;
  }
  clearSession_(chatId, tg);
  showAdminPanel_(chatId, tg, 0);
}
// مخاطب فرستاده‌شده در حالت افزودن ادمین
function handleAdminContact_(msg, s) {
  var chatId = msg.chat.id, tg = msg.from.id, c = msg.contact;
  if (!isSuperTg_(tg)) { clearSession_(chatId, tg); tgSend_(chatId, TXT.NO_ACCESS); return; }
  if (!c.user_id) { tgSend_(chatId, '⚠️ این مخاطب حساب تلگرام ندارد (یا تلگرام آیدی‌اش را نداد). راه دیگر: خود آن شخص در بات /myid بزند و عدد را برایتان بفرستد.'); return; }
  finishAddAdmin_(chatId, tg, num_(c.user_id), normalizeName_(((c.first_name || '') + ' ' + (c.last_name || '')).trim()));
}
function finishAddAdmin_(chatId, tg, newTg, nameHint) {
  var r = addAdmin_(newTg, nameHint, true);
  if (!r.ok) { tgSend_(chatId, '⚠️ ' + r.msg); return; }
  clearSession_(chatId, tg);
  tgSend_(chatId, '✅ «' + r.name + '» ادمین شد.\n' +
    (r.notified ? 'به خودش هم خبر دادم.' : 'نتوانستم به خودش پیام بدهم (هنوز بات را استارت نکرده)؛ بگویید یک بار /start بزند.') +
    '\n⚠️ یادتان باشد او را عضو گروه بررسی «ادمین‌ها» هم بکنید تا گزارش‌ها را ببیند.');
  showAdminsList_(chatId, tg, 0);
}

// ---------- گزارش‌ها ----------
function showPendingList_(chatId, mid) {
  var list = readTable_('Reports').filter(function (r) { return r.status === 'pending'; })
    .sort(function (a, b) { return num_(a.report_id) - num_(b.report_id); });
  var lines = ['⏳ گزارش‌های در انتظار بررسی: ' + list.length];
  var rows = [];
  list.slice(0, 15).forEach(function (r) {
    var cty = getCityById_(r.city_id);
    lines.push('• #' + r.report_id + ' — ' + (cty ? cty.name : '') + ' — ' + cut_(r.subject, 30) + ' — آخرین ارسال: ' + String(cellToStr_(r.updated_at)).slice(0, 16));
    rows.push([btn_('#' + r.report_id + ' · ' + (cty ? cty.name : '') + ' · ' + cut_(r.subject, 20), 'A:r:' + r.report_id)]);
  });
  if (list.length > 15) lines.push('… و ' + (list.length - 15) + ' مورد دیگر');
  if (list.length) lines.push('\nتایید/رد این گزارش‌ها با دکمه‌های همان پیام در گروه بررسی انجام می‌شود.');
  else lines.push('\nهمه گزارش‌ها بررسی شده‌اند 👌');
  rows.push(homeRow_());
  panelOut_(chatId, mid, lines.join('\n'), ikb_(rows));
}
function showPublishedList_(chatId, mid) {
  var list = readTable_('Reports').filter(function (r) { return r.status === 'approved'; })
    .sort(function (a, b) { return num_(b.report_id) - num_(a.report_id); }).slice(0, ADM_PER_PAGE);
  var rows = list.map(function (r) {
    var cty = getCityById_(r.city_id);
    return [btn_('#' + r.report_id + ' · ' + (cty ? cty.name : '') + ' · ' + cut_(r.subject, 22), 'A:r:' + r.report_id)];
  });
  rows.push([btn_('🔎 گزارش با شماره', 'A:rf')]);
  rows.push(homeRow_());
  panelOut_(chatId, mid, list.length ? '📢 آخرین گزارش‌های منتشرشده در کانال — برای ویرایش یا حذف یکی را انتخاب کنید:' : 'هنوز گزارشی منتشر نشده است.', ikb_(rows));
}
function reportCardText_(rep) {
  var cty = getCityById_(rep.city_id);
  var lines = [
    (STATUS_EMOJI[rep.status] || '') + ' گزارش شماره ' + rep.report_id + ' — ' + (STATUS_FA[rep.status] || rep.status),
    'کارمند: ' + rep.employee_name,
    'شهرستان: ' + (cty ? cty.name : '—'),
    'نوع: ' + rep.class_type + ' — مخاطب: ' + rep.audience,
    'موضوع: ' + rep.subject,
    'مدرس: ' + rep.instructor_name,
    'برگزاری: ' + String(cellToStr_(rep.event_jalali)) + ' ساعت ' + String(cellToStr_(rep.event_time)) + ' — ' + rep.duration_min + ' دقیقه',
    'تعداد: ' + rep.participants + ' نفر',
    'دفعات ارسال مجدد: ' + num_(rep.resubmit_count)
  ];
  if (rep.status === 'approved') lines.push('تاریخ درج در کانال: ' + String(cellToStr_(rep.publish_jalali)));
  if (rep.status === 'pending') lines.push('\nتصمیم درباره این گزارش در گروه بررسی گرفته می‌شود.');
  return lines.join('\n');
}
function reportCardKb_(rep) {
  var rows = [];
  if (rep.status === 'approved') {
    rows.push([btn_('✏️ ویرایش کپشن در کانال', 'A:re:' + rep.report_id)]);
    rows.push([btn_('🗑 حذف از کانال', 'A:rd:' + rep.report_id)]);
  }
  rows.push([btn_('📢 منتشرشده‌ها', 'A:rl'), btn_('🏠 پنل اصلی', 'A:m')]);
  return ikb_(rows);
}
function showReportCard_(chatId, rid, mid) {
  var rep = getReport_(rid);
  if (!rep) { panelOut_(chatId, mid, 'گزارش ' + rid + ' پیدا نشد.', ikb_([homeRow_()])); return; }
  panelOut_(chatId, mid, reportCardText_(rep), reportCardKb_(rep));
}
function reportEditKb_(rid) {
  var rows = [], row = [];
  REPORT_EDIT_FIELDS.forEach(function (f) {
    row.push(btn_(f[1], 'A:rx:' + rid + ':' + f[0]));
    if (row.length === 2) { rows.push(row); row = []; }
  });
  if (row.length) rows.push(row);
  rows.push([btn_('↩️ بازگشت به گزارش', 'A:r:' + rid)]);
  return ikb_(rows);
}
function fieldOptions_(field) {
  if (field === 'class') return CFG.CLASS_TYPES;
  if (field === 'audience') return CFG.AUDIENCES;
  if (field === 'duration') return CFG.DURATIONS_MIN.map(function (x) { return x + ' دقیقه'; });
  return null;
}
function fieldOptionsKb_(rid, field) {
  var opts = fieldOptions_(field) || [], rows = [], row = [];
  opts.forEach(function (o, i) {
    row.push(btn_(o, 'A:rv:' + rid + ':' + field + ':' + i));
    if (row.length === 3) { rows.push(row); row = []; }
  });
  if (row.length) rows.push(row);
  rows.push([btn_('↩️ بازگشت', 'A:re:' + rid)]);
  return ikb_(rows);
}
function startReportFieldEdit_(chatId, tg, s, rid, field, mid) {
  var rep = getReport_(rid);
  if (!rep || rep.status !== 'approved') { panelOut_(chatId, mid, 'فقط گزارش «تایید‌شده» را می‌توان ویرایش کرد.', ikb_([homeRow_()])); return; }
  if (fieldOptions_(field)) {
    panelOut_(chatId, mid, 'مقدار جدید را انتخاب کنید:', fieldOptionsKb_(rid, field));
    return;
  }
  if (!REPORT_EDIT_PROMPTS[field]) { showReportCard_(chatId, rid, mid); return; }
  s.state = 'adm:repField'; s.data = { rid: rid, field: field }; saveSession_(chatId, tg, s);
  panelOut_(chatId, mid, '✏️ گزارش ' + rid + '\n' + REPORT_EDIT_PROMPTS[field], ikb_([[btn_('↩️ انصراف', 'A:re:' + rid)]]));
}
// ورودی ادمین را به تغییرات ستون‌های Reports تبدیل می‌کند: {patch} یا {error}
function reportPatchFor_(rep, field, raw, adminTg) {
  if (!rep || rep.status !== 'approved') return { error: 'این گزارش دیگر در وضعیت «تایید‌شده» نیست.' };
  var t = typeof raw === 'string' ? raw.trim() : raw, opts;
  if (field === 'class' || field === 'audience' || field === 'duration') {
    opts = field === 'class' ? CFG.CLASS_TYPES : field === 'audience' ? CFG.AUDIENCES : CFG.DURATIONS_MIN;
    var v = opts[num_(raw)];
    if (v === undefined) return { error: 'گزینه نامعتبر است.' };
    if (field === 'class') return { patch: { class_type: v } };
    if (field === 'audience') return { patch: { audience: v } };
    return { patch: { duration_min: v } };
  }
  if (field === 'subject') return t ? { patch: { subject: t } } : { error: 'موضوع نمی‌تواند خالی باشد.' };
  if (field === 'place') return t ? { patch: { place: t } } : { error: 'مکان نمی‌تواند خالی باشد.' };
  if (field === 'operation') return { patch: { operation_name: (t === '-' || t === '—' || t === 'ندارد') ? '' : t } };
  if (field === 'participants') {
    var n = parseIntFa_(t);
    if (n === null || n < CFG.MIN_PARTICIPANTS || n > CFG.MAX_PARTICIPANTS) return { error: 'عدد معتبر نیست؛ تعداد را بین ۱ تا ۵۰۰۰ بنویسید.' };
    return { patch: { participants: n } };
  }
  if (field === 'time') {
    var tm = parseTimeInput_(t);
    return tm ? { patch: { event_time: tm } } : { error: 'ساعت را به شکل 21:00 بنویسید (۰۰ تا ۲۳).' };
  }
  if (field === 'date') {
    var j = parseJalaliInput_(t);
    if (!j) return { error: 'تاریخ معتبر نیست؛ به شکل 1405/07/23 بنویسید.' };
    if (isFutureJalali_(j.jy, j.jm, j.jd)) return { error: TXT.DATE_FUTURE };
    return { patch: {
      event_jalali: formatJalali_(j.jy, j.jm, j.jd), event_gregorian: jalaliToGregStr_(j.jy, j.jm, j.jd),
      event_year: j.jy, event_month: j.jm, event_day: j.jd
    } };
  }
  if (field === 'teacher') {
    var name = normalizeName_(t);
    if (name.length < 3) return { error: 'نام مدرس را کامل بنویسید.' };
    var ins = getOrCreateInstructor_(rep.city_id, name, adminTg); // مدرس در فهرست همان شهرستان هم ثبت/پیدا می‌شود
    return { patch: { instructor_id: ins.instructor_id, instructor_name: ins.name } };
  }
  return { error: 'بخش نامعتبر.' };
}
// کپشن جدید را با «تاریخ درج» اصلی می‌سازد، پست کانال را ویرایش و ردیف را به‌روز می‌کند
function applyReportEdit_(rid, patch) {
  var rep = getReport_(rid);
  if (!rep || rep.status !== 'approved') return { ok: false, msg: 'این گزارش دیگر در وضعیت «تایید‌شده» نیست.' };
  var ids = safeJsonArr_(cellToStr_(rep.channel_message_ids)), channelId = getSettingNum_('channel_id');
  if (!ids.length || !channelId) return { ok: false, msg: 'شناسه پست کانالِ این گزارش پیدا نشد.' };
  var merged = {};
  Object.keys(rep).forEach(function (k) { merged[k] = rep[k]; });
  Object.keys(patch).forEach(function (k) { merged[k] = patch[k]; });
  var caption = captionFromReport_(merged, String(cellToStr_(rep.publish_jalali)));
  if (caption.length > CFG.CAPTION_LIMIT) return { ok: false, msg: 'کپشن جدید از حد مجاز ' + CFG.CAPTION_LIMIT + ' حرف بلندتر می‌شود؛ متن را کوتاه‌تر کنید.' };
  if (caption !== String(rep.published_caption || '')) {
    var res = tgCall_('editMessageCaption', { chat_id: channelId, message_id: num_(ids[0]), caption: caption });
    if (!res) return { ok: false, msg: 'ویرایش پست در کانال ناموفق بود. مطمئن شوید بات در کانال ادمین است و اجازه «ویرایش پیام» دارد.' };
  }
  var full = {};
  Object.keys(patch).forEach(function (k) { full[k] = patch[k]; });
  full.published_caption = caption;
  updateReport_(rid, full);
  return { ok: true, caption: caption };
}
function deletePublishedReport_(rid, adminTg) {
  var rep = getReport_(rid);
  if (!rep || rep.status !== 'approved') return { ok: false, msg: 'این گزارش در وضعیت «تایید‌شده» نیست.' };
  var ids = safeJsonArr_(cellToStr_(rep.channel_message_ids)), channelId = getSettingNum_('channel_id'), failed = 0;
  ids.forEach(function (id) { if (!channelId || !tgDelete_(channelId, num_(id))) failed++; });
  updateReport_(rid, { status: 'deleted' });
  log_('warn', 'admin-delete', 'report ' + rid + ' by ' + adminTg + (failed ? ' (not deleted from channel: ' + failed + ')' : ''), '');
  tgSend_(rep.submitter_telegram_id, '🗑 گزارش شما با موضوع «' + rep.subject + '» توسط ادمین از کانال حذف شد.\nبرای پیگیری با ادمین در تماس باشید.');
  return { ok: true, failed: failed, total: ids.length };
}

// ---------- آمار ----------
function showMonthStats_(chatId, jy, jm, mid) {
  var t = todayJalali_();
  if (!jy || jm < 1 || jm > 12) { jy = t.jy; jm = t.jm; }
  var reports = readTable_('Reports'), st = monthStats_(reports, getCities_(), jy, jm);
  var pending = reports.filter(function (r) { return r.status === 'pending'; }).length;
  var lines = ['📊 گزارش‌های تاییدشده — ' + JALALI_MONTHS[jm - 1] + ' ' + jy + ' (بر اساس تاریخ برگزاری)',
    'جمع: ' + st.total + ' گزارش — ' + st.people + ' نفر شرکت‌کننده', ''];
  st.rows.forEach(function (r, i) {
    lines.push((i === 0 ? '🏆 ' : (i + 1) + ') ') + r.name + ': ' + r.count + ' گزارش (' + r.people + ' نفر)');
  });
  if (st.zero.length) lines.push('', '⚪️ بدون گزارش (' + st.zero.length + ' شهرستان): ' + st.zero.join('، '));
  lines.push('', '⏳ در انتظار بررسی (همه ماه‌ها): ' + pending);
  var pv = prevJalaliMonth_(jy, jm), nx = nextJalaliMonth_(jy, jm), nav = [btn_('⬅️ ' + JALALI_MONTHS[pv.jm - 1], 'A:s:' + pv.jy + ':' + pv.jm)];
  if (nx.jy < t.jy || (nx.jy === t.jy && nx.jm <= t.jm)) nav.push(btn_(JALALI_MONTHS[nx.jm - 1] + ' ➡️', 'A:s:' + nx.jy + ':' + nx.jm));
  panelOut_(chatId, mid, lines.join('\n'), ikb_([nav, homeRow_()]));
}

// ---------- کاربران ----------
function findUsers_(q) {
  var digits = toEnDigits_(q).replace(/[\s\-()+]/g, ''), phone = normalizePhone_(q), nq = normCmp_(q);
  var isNum = /^\d+$/.test(digits);
  var hits = readTable_('Users').filter(function (u) {
    if (phone && normalizePhone_(u.phone) === phone) return true;
    if (isNum && digits.length >= 5 && String(u.telegram_id) === digits) return true;
    return !isNum && nq.length >= 2 && normCmp_(u.full_name).indexOf(nq) !== -1;
  });
  return { total: hits.length, list: hits.slice(0, ADM_PER_PAGE) };
}
function showUserCard_(chatId, uid, mid) {
  var u = getUserById_(uid);
  if (!u) { panelOut_(chatId, mid, 'کاربر پیدا نشد.', ikb_([homeRow_()])); return; }
  var cty = getCityById_(u.city_id), mine = readTable_('Reports').filter(function (r) { return num_(r.submitter_telegram_id) === num_(u.telegram_id); });
  function cnt(st) { return mine.filter(function (r) { return r.status === st; }).length; }
  var text = '👤 ' + (u.full_name || 'بدون نام') +
    '\nشهرستان: ' + (cty ? cty.name : '— (تعیین نشده)') +
    '\nشماره: ' + (u.phone || '—') +
    '\nآیدی تلگرام: ' + u.telegram_id +
    '\nوضعیت: ' + (USER_STATUS_FA[u.status] || u.status) +
    (isAdminTg_(u.telegram_id) ? '\nادمین: بله 🛡' : '') +
    '\nگزارش‌ها: تاییدشده ' + cnt('approved') + ' — در انتظار ' + cnt('pending') + ' — ردشده ' + cnt('rejected') + ' — حذف‌شده ' + cnt('deleted') +
    '\nعضویت از: ' + String(cellToStr_(u.created_at)).slice(0, 10);
  var rows = [];
  if (u.status === 'approved') rows.push([btn_('⛔️ غیرفعال‌کردن', 'A:ut:' + uid)]);
  else rows.push([btn_(u.status === 'disabled' ? '✅ فعال‌سازی دوباره' : '✅ تایید و فعال‌سازی', 'A:ut:' + uid)]);
  rows.push([btn_('🏙 تغییر شهرستان', 'A:uy:' + uid + ':0')]);
  rows.push([btn_('🔎 جستجوی دیگر', 'A:u'), btn_('🏠 پنل اصلی', 'A:m')]);
  panelOut_(chatId, mid, text, ikb_(rows));
}
function toggleUser_(cb, chatId, uid, mid) {
  var u = getUserById_(uid);
  if (!u) { tgAnswerCb_(cb.id, 'کاربر پیدا نشد.', true); return; }
  if (u.status === 'approved') {
    updateUser_(uid, u.telegram_id, { status: 'disabled' });
    tgAnswerCb_(cb.id, 'کاربر غیرفعال شد؛ دیگر نمی‌تواند گزارش ثبت کند.', true);
  } else {
    if (!(num_(u.city_id) > 0)) { tgAnswerCb_(cb.id, 'اول با «🏙 تغییر شهرستان» شهرستان این کاربر را تعیین کنید.', true); return; }
    updateUser_(uid, u.telegram_id, { status: 'approved' });
    var cty = getCityById_(u.city_id);
    tgSend_(u.telegram_id, '✅ حساب شما توسط ادمین فعال شد.\nنام: ' + u.full_name + '\nشهرستان: ' + (cty ? cty.name : '') + '\nاز منوی زیر انتخاب کنید:', mainMenuKb_());
    tgAnswerCb_(cb.id, 'کاربر فعال شد و به او اطلاع داده شد.', true);
  }
  showUserCard_(chatId, uid, mid);
}
function setUserCity_(cb, chatId, uid, cityId, mid) {
  var u = getUserById_(uid), cty = getCityById_(cityId);
  if (!u || !cty) { tgAnswerCb_(cb.id, 'کاربر یا شهرستان پیدا نشد.', true); return; }
  updateUser_(uid, u.telegram_id, { city_id: cityId });
  var us = getSession_(num_(u.telegram_id), num_(u.telegram_id));
  if (us.state) clearSession_(num_(u.telegram_id), num_(u.telegram_id)); // فرم نیمه‌کاره با شهرستان قبلی ادامه پیدا نکند
  if (u.status === 'approved') tgSend_(u.telegram_id, 'ℹ️ شهرستان حساب شما توسط ادمین به «' + cty.name + '» تغییر کرد.', mainMenuKb_());
  tgAnswerCb_(cb.id, 'شهرستان به ' + cty.name + ' تغییر کرد.');
  showUserCard_(chatId, uid, mid);
}
// کیبورد شهرستان‌ها برای پنل: pickPrefix + city_id (+ پسوند اختیاری)، navPrefix + page
function adminCitiesKb_(pickPrefix, navPrefix, page, tailRows, suffixFn, counts) {
  var p = paginate_(getCities_(), page || 0, CFG.CITIES_PER_PAGE), rows = [], row = [];
  p.chunk.forEach(function (c) {
    var label = c.name + (counts ? ' (' + (counts[num_(c.city_id)] || 0) + ')' : '');
    row.push(btn_(label, pickPrefix + c.city_id + (suffixFn ? suffixFn(c.city_id) : '')));
    if (row.length === 2) { rows.push(row); row = []; }
  });
  if (row.length) rows.push(row);
  var nav = [];
  if (p.page > 0) nav.push(btn_('⬅️ قبلی', navPrefix + (p.page - 1)));
  if (p.page < p.total - 1) nav.push(btn_('بعدی ➡️', navPrefix + (p.page + 1)));
  if (nav.length) rows.push(nav);
  return ikb_(rows.concat(tailRows || []));
}

// ---------- مدرسان ----------
function getInstructorAny_(iid) {
  return findOne_('Instructors', function (r) { return num_(r.instructor_id) === num_(iid); });
}
function showCityInstructors_(chatId, cityId, page, mid) {
  var cty = getCityById_(cityId);
  var all = readTable_('Instructors').filter(function (r) { return num_(r.city_id) === num_(cityId); })
    .sort(function (a, b) {
      var ia = String(a.is_active) === '0' ? 1 : 0, ib = String(b.is_active) === '0' ? 1 : 0;
      return ia - ib || String(a.name).localeCompare(String(b.name), 'fa');
    });
  var pg = paginate_(all, page, ADM_PER_PAGE);
  var rows = pg.chunk.map(function (r) {
    return [btn_((String(r.is_active) === '0' ? '🚫 ' : '') + r.name, 'A:ti:' + r.instructor_id)];
  });
  var nav = [];
  if (pg.page > 0) nav.push(btn_('⬅️ قبلی', 'A:tc:' + cityId + ':' + (pg.page - 1)));
  if (pg.page < pg.total - 1) nav.push(btn_('بعدی ➡️', 'A:tc:' + cityId + ':' + (pg.page + 1)));
  if (nav.length) rows.push(nav);
  rows.push([btn_('➕ افزودن مدرس', 'A:ta:' + cityId)]);
  rows.push([btn_('↩️ شهرستان‌ها', 'A:t:0'), btn_('🏠 پنل اصلی', 'A:m')]);
  var activeN = all.filter(function (r) { return String(r.is_active) !== '0'; }).length;
  panelOut_(chatId, mid, '👨‍🏫 مدرسان ' + (cty ? cty.name : '') + ': ' + activeN + ' فعال' +
    (all.length > activeN ? '، ' + (all.length - activeN) + ' غیرفعال (🚫)' : '') +
    (all.length ? '\nبرای تغییر نام یا غیرفعال‌کردن، روی مدرس بزنید:' : '\nهنوز مدرسی ثبت نشده است.'), ikb_(rows));
}
function showInstructorCard_(chatId, iid, mid) {
  var ins = getInstructorAny_(iid);
  if (!ins) { panelOut_(chatId, mid, 'مدرس پیدا نشد.', ikb_([homeRow_()])); return; }
  var cty = getCityById_(ins.city_id), active = String(ins.is_active) !== '0';
  var used = readTable_('Reports').filter(function (r) { return num_(r.instructor_id) === num_(iid) && r.status === 'approved'; }).length;
  panelOut_(chatId, mid, '👨‍🏫 ' + ins.name + '\nشهرستان: ' + (cty ? cty.name : '') +
    '\nوضعیت: ' + (active ? 'فعال ✅ (در فرم کارمندان دیده می‌شود)' : 'غیرفعال 🚫 (در فرم دیده نمی‌شود)') +
    '\nگزارش‌های تاییدشده با این مدرس: ' + used,
    ikb_([
      [btn_('✏️ تغییر نام', 'A:tr:' + iid), btn_(active ? '🚫 غیرفعال‌کردن' : '✅ فعال‌کردن', 'A:tt:' + iid)],
      [btn_('↩️ مدرسان ' + (cty ? cty.name : ''), 'A:tc:' + ins.city_id + ':0'), btn_('🏠 پنل اصلی', 'A:m')]
    ]));
}
function toggleInstructor_(cb, chatId, iid, mid) {
  var ins = getInstructorAny_(iid);
  if (!ins) { tgAnswerCb_(cb.id, 'مدرس پیدا نشد.', true); return; }
  var active = String(ins.is_active) !== '0';
  if (!active) { // فعال‌سازی: نباید هم‌نام فعال دیگری در همان شهرستان باشد
    var clash = listInstructors_(ins.city_id).filter(function (r) { return String(r.name_norm) === String(ins.name_norm) && num_(r.instructor_id) !== num_(iid); })[0];
    if (clash) { tgAnswerCb_(cb.id, 'مدرس فعالی با همین نام در این شهرستان هست.', true); return; }
  }
  updateRow_('Instructors', ins._row, { is_active: active ? '0' : '1' });
  bustCache_();
  tgAnswerCb_(cb.id, active ? 'غیرفعال شد' : 'فعال شد');
  showInstructorCard_(chatId, iid, mid);
}
function renameInstructor_(iid, raw) {
  var ins = getInstructorAny_(iid);
  if (!ins) return { ok: false, msg: 'مدرس پیدا نشد.' };
  var name = normalizeName_(raw), norm = normCmp_(name);
  if (name.length < 3) return { ok: false, msg: 'نام را کامل بنویسید (حداقل ۳ حرف).' };
  var clash = readTable_('Instructors').filter(function (r) {
    return num_(r.city_id) === num_(ins.city_id) && String(r.name_norm) === norm && num_(r.instructor_id) !== num_(iid);
  })[0];
  if (clash) return { ok: false, msg: 'مدرس دیگری با همین نام در این شهرستان ثبت شده است' + (String(clash.is_active) === '0' ? ' (غیرفعال؛ می‌توانید همان را فعال کنید).' : '.') };
  updateRow_('Instructors', ins._row, { name: name, name_norm: norm });
  bustCache_();
  return { ok: true };
}
function addInstructorAdmin_(cityId, raw, adminTg) {
  if (!getCityById_(cityId)) return { ok: false, msg: 'شهرستان پیدا نشد.' };
  var name = normalizeName_(raw), norm = normCmp_(name);
  if (name.length < 3) return { ok: false, msg: 'نام را کامل بنویسید (حداقل ۳ حرف).' };
  var same = readTable_('Instructors').filter(function (r) { return num_(r.city_id) === num_(cityId) && String(r.name_norm) === norm; })[0];
  if (same && String(same.is_active) !== '0') return { ok: false, msg: 'این مدرس از قبل در فهرست هست.' };
  if (same) { updateRow_('Instructors', same._row, { is_active: '1' }); bustCache_(); return { ok: true, msg: 'مدرس «' + same.name + '» دوباره فعال شد.' }; }
  var ins = getOrCreateInstructor_(cityId, name, adminTg);
  return { ok: true, msg: 'مدرس «' + ins.name + '» اضافه شد.' };
}

// ---------- ادمین‌ها ----------
function findAdminRow_(tg) {
  return findOne_('Admins', function (r) { return num_(r.telegram_id) === num_(tg); });
}
function showAdminsList_(chatId, viewerTg, mid) {
  var list = readTable_('Admins');
  var lines = ['🛡 ادمین‌های بات:'], rows = [];
  list.forEach(function (a) {
    var active = String(a.is_active) !== '0';
    lines.push((active ? '• ' : '• (غیرفعال) ') + (a.full_name || '—') + ' — ' + (a.role === 'superadmin' ? 'سوپرادمین 👑' : 'ادمین') + ' — ' + a.telegram_id);
    if (active && a.role !== 'superadmin' && num_(a.telegram_id) !== num_(viewerTg)) {
      rows.push([btn_('🚫 برداشتن دسترسی ' + cut_(a.full_name || a.telegram_id, 20), 'A:ax:' + a.telegram_id)]);
    }
  });
  rows.push([btn_('➕ افزودن ادمین', 'A:aa')]);
  rows.push(homeRow_());
  panelOut_(chatId, mid, lines.join('\n') + '\n\nادمین‌ها گزارش‌ها را در گروه بررسی تایید/رد می‌کنند و به این پنل دسترسی دارند (به‌جز همین بخش ادمین‌ها).', ikb_(rows));
}
function addAdmin_(newTg, nameHint, notify) {
  if (!(num_(newTg) > 0)) return { ok: false, msg: 'آیدی نامعتبر است.' };
  var row = findAdminRow_(newTg);
  if (row && String(row.is_active) !== '0') return { ok: false, msg: 'این شخص از قبل ادمین است.' };
  var u = getUserByTg_(newTg);
  var name = nameHint || (u && u.full_name) || (row && row.full_name) || ('ادمین ' + newTg);
  if (row) updateRow_('Admins', row._row, { is_active: '1', full_name: name, role: row.role === 'superadmin' ? 'superadmin' : 'admin' });
  else appendRow_('Admins', { telegram_id: newTg, full_name: name, role: 'admin', is_active: '1', added_at: nowStr_() });
  bustCache_();
  var notified = false;
  if (notify) notified = !!tgSend_(newTg, '🛡 شما به‌عنوان ادمین بات گزارش‌گیری نسرا اضافه شدید.\n• پنل مدیریت: /admin\n• برای تایید/رد گزارش‌ها باید عضو گروه بررسی ادمین‌ها هم باشید.', mainMenuKb_());
  return { ok: true, name: name, notified: notified };
}
function removeAdmin_(targetTg, byTg) {
  var row = findAdminRow_(targetTg);
  if (!row || String(row.is_active) === '0') return { ok: false, msg: 'این شخص ادمین فعال نیست.' };
  if (row.role === 'superadmin') return { ok: false, msg: 'دسترسی سوپرادمین از داخل بات برداشته نمی‌شود (فقط از برگه Admins).' };
  if (num_(targetTg) === num_(byTg)) return { ok: false, msg: 'نمی‌توانید دسترسی خودتان را بردارید.' };
  updateRow_('Admins', row._row, { is_active: '0' });
  bustCache_();
  return { ok: true };
}
