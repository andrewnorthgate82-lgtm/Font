// Registration.gs — ‎/start‎، ثبت‌نام (شماره ← شهرستان ← نام ← نقش) + لیست مجاز + تایید دستی ادمین،
// نقش همکار (یک بار)، منوی اصلی کارمند و «📋 گزارش‌های من» (عملکرد ماهانه برادران/خواهران ناحیه)

// ---------- ثبت‌نام نیمه‌کاره ----------
// نیمه‌کاره = شماره دارد ولی شهرستان ندارد، یا شهرستان و نام دارد ولی نقش ندارد و درخواستش هنوز برای ادمین‌ها نرفته
function isHalfRegistered_(u) {
  if (!(u && u.status === 'pending' && u.phone)) return false;
  if (!(num_(u.city_id) > 0)) return true;
  return !positionOf_(u.position) && !String(u.reg_group_message_id || '');
}
function resumeRegistration_(chatId, tg, user) {
  if (num_(user.city_id) > 0) { // فقط قدم نقش مانده است
    askRole_(chatId, tg, 'reg', 'ثبت‌نام شما نیمه‌کاره مانده بود؛ از همان‌جا ادامه می‌دهیم 🙂\n' + TXT.ASK_ROLE_REG);
    return;
  }
  var s = getSession_(chatId, tg);
  s.state = 'reg:city';
  s.data = { phone: normalizePhone_(user.phone) || String(user.phone || ''), cityPage: 0 };
  saveSession_(chatId, tg, s);
  tgSend_(chatId, 'ثبت‌نام شما نیمه‌کاره مانده بود؛ از همان‌جا ادامه می‌دهیم 🙂\n' + TXT.ASK_CITY, citiesKb_('rc', 0));
}

function handleStart_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id;
  clearSession_(chatId, tg);
  if (!hasSuperadmin_()) { tgSend_(chatId, TXT.SETUP_NO_SUPERADMIN); return; }
  var u = getUserByTg_(tg);
  if (u && u.status === 'disabled') { tgSend_(chatId, TXT.USER_DISABLED); return; }
  if (isActiveEmployee_(u) || isAdminTg_(tg)) { sendMainMenu_(chatId, tg); return; }
  if (isHalfRegistered_(u)) { resumeRegistration_(chatId, tg, u); return; }
  if (u && u.status === 'pending' && u.phone) { tgSend_(chatId, TXT.REG_WAIT); return; }
  tgSend_(chatId, TXT.WELCOME_UNREGISTERED, contactKb_());
}

function handleContact_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id, c = msg.contact;
  // حتماً شماره باید متعلق به خود فرستنده باشد
  if (!c.user_id || num_(c.user_id) !== num_(tg)) { tgSend_(chatId, TXT.CONTACT_NOT_YOURS, contactKb_()); return; }
  var phone = normalizePhone_(c.phone_number);
  if (!phone) { tgSend_(chatId, TXT.PHONE_INVALID, contactKb_()); return; }
  var existing = getUserByTg_(tg);
  if (existing && existing.status === 'disabled') { // غیرفعال: حتی با لیست مجاز هم هیچ تغییری نکن
    tgSend_(chatId, TXT.USER_DISABLED);
    return;
  }
  var owner = getUserByPhone_(phone);
  if (owner && num_(owner.telegram_id) !== num_(tg)) { tgSend_(chatId, TXT.PHONE_USED_BY_OTHER); return; }

  var user = existing || ensureUser_(tg, fullNameOf_(msg.from));
  // ردشدهٔ قبلی هرگز خودکار (بدون بررسی ادمین) برنمی‌گردد؛ فقط مسیر تایید دستی:
  var allowed = (user.status === 'rejected') ? null : findWhitelist_(phone);
  if (allowed) { // لیست مجاز: تایید فوری؛ فقط نقش پرسیده می‌شود (اگر از قبل نداشت)
    updateUser_(user.user_id, tg, {
      phone: phone, full_name: allowed.full_name || user.full_name,
      city_id: allowed.city_id, status: 'approved'
    });
    var city = getCityById_(allowed.city_id), fresh = getUserByTg_(tg);
    var done = '✅ ثبت‌نام شما کامل شد!\nنام: ' + (allowed.full_name || user.full_name) + '\nشهرستان: ' + (city ? city.name : '');
    if (!positionOf_(fresh && fresh.position)) { tgSend_(chatId, done, removeKb_()); askRole_(chatId, tg, 'menu', TXT.ASK_ROLE_REG); return; }
    tgSend_(chatId, done + '\nنقش: ' + positionOf_(fresh.position) + '\n\nاز منوی زیر انتخاب کنید:', mainMenuKb_(tg));
    return;
  }
  // مسیر تایید دستی: شهرستان ← نام ← نقش ← ارسال به گروه ادمین‌ها
  updateUser_(user.user_id, tg, { phone: phone, status: 'pending' });
  var s = getSession_(chatId, tg);
  s.state = 'reg:city'; s.data = { phone: phone, cityPage: 0 };
  saveSession_(chatId, tg, s);
  tgSend_(chatId, TXT.ASK_CITY, citiesKb_('rc', 0));
}

function handleRegCityCb_(cb, session, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id;
  var u = getUserByTg_(tg);
  if (u && u.status === 'disabled') { tgAnswerCb_(cb.id, TXT.USER_DISABLED, true); return; }
  if (u && u.status === 'approved') { tgAnswerCb_(cb.id, 'شما قبلاً ثبت‌نام و تایید شده‌اید. از منوی اصلی استفاده کنید.', true); return; }
  if (session.state !== 'reg:city') { // دکمهٔ قدیمی یا نشستِ تمام‌شده
    tgAnswerCb_(cb.id, 'این دکمه دیگر فعال نیست. برای ادامهٔ ثبت‌نام ‎/start‎ را بزنید.', true);
    return;
  }
  if (data.indexOf('pg:rc:') === 0) {
    var page = num_(data.split(':')[2]);
    session.data.cityPage = page; saveSession_(chatId, tg, session);
    tgAnswerCb_(cb.id);
    tgEditMarkup_(chatId, cb.message.message_id, citiesKb_('rc', page));
    return;
  }
  var cityId = num_(data.split(':')[1]);
  session.data.city_id = cityId;
  session.state = 'reg:name';
  saveSession_(chatId, tg, session);
  tgAnswerCb_(cb.id);
  tgSend_(chatId, TXT.ASK_NAME);
}

function handleRegName_(msg, session) {
  var chatId = msg.chat.id, tg = msg.from.id;
  var user = getUserByTg_(tg);
  if (!user) { // رکورد کاربر پاک شده/پیدا نشد: بی‌خطا از اول شروع کن
    clearSession_(chatId, tg);
    tgSend_(chatId, 'اطلاعات ثبت‌نام قبلی پیدا نشد؛ از اول شروع می‌کنیم.');
    tgSend_(chatId, TXT.WELCOME_UNREGISTERED, contactKb_());
    return;
  }
  if (user.status === 'disabled') { tgSend_(chatId, TXT.USER_DISABLED); return; }
  var name = normalizeName_(msg.text);
  if (name.length < 3) { tgSend_(chatId, 'نام و نام‌خانوادگی را کامل بنویسید:'); return; }
  updateUser_(user.user_id, tg, { full_name: name, city_id: num_(session.data.city_id), status: 'pending', reg_group_message_id: '' });
  askRole_(chatId, tg, 'reg', TXT.ASK_ROLE_REG); // قدم آخر ثبت‌نام
}

// درخواست کامل (شهرستان + نام + نقش) ← گروه بررسی (شماره فقط برای ادمین‌ها دیده می‌شود، در Logs نمی‌نویسیم)
function submitRegistration_(chatId, tg) {
  var fresh = getUserByTg_(tg), city = getCityById_(fresh.city_id);
  tgSend_(chatId, TXT.REG_SENT, removeKb_());
  var text = '👤 درخواست ثبت‌نام جدید\nنام: ' + fresh.full_name +
    '\nشهرستان: ' + (city ? city.name : '') +
    '\nنقش: ' + (positionOf_(fresh.position) || '—') +
    '\nشماره: ' + fresh.phone + // عمداً فقط در همین پیام ادمین‌ها؛ در برگه Logs ثبت نمی‌شود
    '\nآیدی تلگرام: ' + fresh.telegram_id;
  var kb = ikb_([[btn_('✅ تایید ثبت‌نام', 'ga:' + fresh.user_id), btn_('❌ رد ثبت‌نام', 'gd:' + fresh.user_id)]]);
  var groupId = getSettingNum_('review_group_id'), sent = null;
  if (groupId) sent = tgSend_(groupId, text, kb);
  if (sent) updateUser_(fresh.user_id, tg, { reg_group_message_id: sent.message_id });
  else {
    updateUser_(fresh.user_id, tg, { reg_group_message_id: 'pm' }); // گروه تنظیم نشده: به سوپرادمین‌ها (درخواست رفته است)
    superadminTgs_().forEach(function (a) { tgSend_(a, text, kb); });
  }
}

function handleRegDecisionCb_(cb, data) {
  var tg = cb.from.id;
  if (!isAdminTg_(tg)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  var approve = data.indexOf('ga:') === 0;
  var user = getUserById_(num_(data.split(':')[1]));
  if (!user) { tgAnswerCb_(cb.id, 'کاربر یافت نشد.', true); return; }
  if (user.status !== 'pending') { tgAnswerCb_(cb.id, 'قبلاً درباره این درخواست تصمیم گرفته شده است.', true); return; }
  updateUser_(user.user_id, user.telegram_id, { status: approve ? 'approved' : 'rejected' });
  var verdict = approve ? '✅ ثبت‌نام تایید شد' : '❌ ثبت‌نام رد شد';
  tgAnswerCb_(cb.id, verdict);
  tgEdit_(cb.message.chat.id, cb.message.message_id,
    (cb.message.text || '') + '\n\n' + verdict + ' — توسط ' + fullNameOf_(cb.from), { inline_keyboard: [] });
  if (!approve) { tgSend_(user.telegram_id, TXT.REG_REJECTED); return; }
  var city = getCityById_(user.city_id), pos = positionOf_(user.position), ut = num_(user.telegram_id);
  var text = '🎉 ثبت‌نام شما تایید شد!\nنام: ' + user.full_name + '\nشهرستان: ' + (city ? city.name : '');
  if (!pos) { tgSend_(ut, text, removeKb_()); askRole_(ut, ut, 'menu', TXT.ASK_ROLE); return; } // درخواست‌های قدیمیِ بدون نقش
  tgSend_(ut, text + '\nنقش: ' + pos + '\n' + (canSubmitReports_(user) ? 'از منوی زیر انتخاب کنید:' : TXT.NO_REPORT_ROLE), mainMenuKb_(ut));
}

// ---------- نقش همکار (فقط یک بار؛ تغییرِ بعدی فقط با ادمین) ----------
// next: 'reg' ثبت‌نام ← ارسال درخواست به ادمین‌ها ، 'menu' منوی اصلی ، 'report' شروع فرم گزارش ، 'my' گزارش‌های من
function rolesKb_() { return ikb_(POSITIONS.map(function (p, i) { return [btn_(p, 'po:' + i)]; })); }
function askRole_(chatId, tg, next, text) {
  var s = getSession_(chatId, tg);
  s.state = 'role:ask'; s.data = { next: next };
  saveSession_(chatId, tg, s);
  tgSend_(chatId, text || TXT.ASK_ROLE, rolesKb_());
}
function handleRoleCb_(cb, s, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id, mid = cb.message.message_id;
  var pos = /^po:\d$/.test(data) ? POSITIONS[num_(data.split(':')[1])] : null;
  var u = getUserByTg_(tg), next = s.data && s.data.next;
  var okState = s.state === 'role:ask' && u && (next === 'reg' ? u.status === 'pending' : isActiveEmployee_(u));
  if (!pos || !okState) { tgAnswerCb_(cb.id, 'این دکمه دیگر فعال نیست؛ ‎/start‎ را بزنید.', true); return; }
  if (next === 'reg' || !positionOf_(u.position)) updateUser_(u.user_id, tg, { position: pos }); // اگر ادمین در این فاصله نقش گذاشته، همان می‌ماند
  var fresh = getUserByTg_(tg);
  clearSession_(chatId, tg);
  tgAnswerCb_(cb.id, 'ثبت شد');
  tgEdit_(chatId, mid, '🎖 نقش شما: ' + (positionOf_(fresh.position) || pos) + ' ✅', null);
  if (next === 'reg') { submitRegistration_(chatId, tg); return; }
  if (next === 'report') { startReport_(chatId, tg, fresh); return; }
  if (next === 'my') { tgSend_(chatId, 'منوی شما به‌روز شد 👇', mainMenuKb_(tg)); showMyMonths_(chatId, tg, 0); return; }
  sendMainMenu_(chatId, tg);
}
function sendNoReportRole_(chatId, tg) { tgSend_(chatId, TXT.NO_REPORT_ROLE, mainMenuKb_(tg)); }

// ---------- منوی اصلی کارمند ----------
function menuNewReport_(msg) {
  var u = denyEmployee_(msg.chat.id, msg.from.id);
  if (!u) return;
  startReport_(msg.chat.id, msg.from.id, u); // نقش ندارد ← پرسیده می‌شود ؛ مسئول فضای مجازی ← پیام «ارسال گزارش ندارد»
}
function menuMyTeachers_(msg) {
  var u = denyEmployee_(msg.chat.id, msg.from.id);
  if (!u) return;
  var rows = listInstructors_(u.city_id);
  if (!rows.length) {
    tgSend_(msg.chat.id, 'هنوز مدرسی برای شهرستان شما ثبت نشده است. هنگام ثبت گزارش می‌توانید مدرس جدید اضافه کنید.');
    return;
  }
  var city = getCityById_(u.city_id);
  tgSend_(msg.chat.id, '👨‍🏫 مدرسان شهرستان ' + (city ? city.name : '') + ':\n' +
    rows.map(function (t) { return '• ' + t.name; }).join('\n'));
}

// ---------- 📋 گزارش‌های من: ماه ← (مسئول فضای مجازی: برادران/خواهران) ← عملکرد ----------
// برادران = گزارش‌های «فرمانده گردان برادر» و «مسئول نسرا برادر» همان ناحیه؛ خواهران همین‌طور.
// نقشِ ثبت‌شده در خود گزارش (لحظه ارسال) ملاک است؛ مثل فایل اکسل خواهر/برادر. فقط گزارش‌های تاییدشده شمرده می‌شوند.
var MY_MONTHS = 6;
var SIDE_FA = { b: 'برادران', s: 'خواهران' };
var SIDE_ROLES_FA = { b: 'فرمانده گردان برادر و مسئول نسرا برادر', s: 'فرمانده گردان خواهر و مسئول نسرا خواهر' };
var PRODUCT_ROWS = [['#پوستر', 'پوستر'], ['#اینفوگرافیک', 'اینفوگرافیک'], ['#کلیپ', 'کلیپ'], ['#اسلاید', 'اسلاید'], ['#صفحه_word', 'صفحه word'], ['#موشن_گرافیک', 'موشن گرافیک']];

function menuMyReports_(msg) {
  var u = denyEmployee_(msg.chat.id, msg.from.id);
  if (!u) return;
  if (!positionOf_(u.position)) { askRole_(msg.chat.id, msg.from.id, 'my'); return; }
  showMyMonths_(msg.chat.id, msg.from.id, 0);
}
function myMonthsKb_() {
  var t = currentPeriod_(), list = [], m = { jy: t.jy, jm: t.jm };
  for (var i = 0; i < MY_MONTHS; i++) { list.push(btn_(periodName_(m.jy, m.jm), 'mr:' + m.jy + ':' + m.jm)); m = prevJalaliMonth_(m.jy, m.jm); }
  var rows = [];
  for (var j = 0; j < list.length; j += 2) rows.push(list.slice(j, j + 2));
  return ikb_(rows);
}
function showMyMonths_(chatId, tg, mid) {
  var text = '📋 گزارش‌های من\nماه گزارش را انتخاب کنید:\n(هر ماه = برنامه‌های ۲۱ ماه قبل تا ۲۰ همان ماه)';
  if (mid && tgEdit_(chatId, mid, text, myMonthsKb_())) return;
  tgSend_(chatId, text, myMonthsKb_());
}
function handleMyReportsCb_(cb, s, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id, mid = cb.message.message_id;
  var u = getUserByTg_(tg);
  if (!isActiveEmployee_(u)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  var mySide = positionSide_(u.position);
  if (!mySide) { tgAnswerCb_(cb.id); askRole_(chatId, tg, 'my'); return; }
  var parts = data.split(':');
  if (parts[1] === 'm') { tgAnswerCb_(cb.id); showMyMonths_(chatId, tg, mid); return; }
  var jy = num_(parts[1]), jm = num_(parts[2]), t = currentPeriod_();
  if (!(jy >= 1400 && jm >= 1 && jm <= 12) || jy * 12 + jm > t.jy * 12 + t.jm) { tgAnswerCb_(cb.id, 'این دکمه دیگر فعال نیست.', true); return; }
  var side = mySide === 'v' ? parts[3] : mySide; // فقط مسئول فضای مجازی بین برادران و خواهران انتخاب می‌کند
  var back = [btn_('↩️ ماه دیگر', 'mr:m')];
  tgAnswerCb_(cb.id);
  if (side !== 'b' && side !== 's') {
    tgEdit_(chatId, mid, '📋 گزارش ' + periodName_(jy, jm) + '\nعملکرد کدام بخش را می‌خواهید؟',
      ikb_([[btn_('👨 برادران', 'mr:' + jy + ':' + jm + ':b'), btn_('👩 خواهران', 'mr:' + jy + ':' + jm + ':s')], back]));
    return;
  }
  var city = getCityById_(u.city_id), reports = readTable_('Reports');
  var text = myReportText_(reports, positionsByTg_(readTable_('Users')), u.city_id, city ? city.name : '', jy, jm, side);
  var rows = [];
  if (mySide === 'v') { var other = side === 'b' ? 's' : 'b'; rows.push([btn_('🔁 ' + SIDE_FA[other], 'mr:' + jy + ':' + jm + ':' + other)]); }
  rows.push(back);
  tgEdit_(chatId, mid, text, ikb_(rows));
}
// آمار یک ناحیه، یک دوره، یک بخش (برادران/خواهران)
function sideReportStats_(reports, posByTg, cityId, jy, jm, side) {
  var st = { 'حضوری': { n: 0, p: 0 }, 'مجازی': { n: 0, p: 0 }, 'توانمندسازی': { n: 0, p: 0 }, 'خلاقانه': { n: 0, p: 0 }, products: {}, pending: 0 };
  reports.forEach(function (r) {
    if (num_(r.city_id) !== num_(cityId) || !inPeriod_(r, jy, jm) || reportSide_(r, posByTg) !== side) return;
    if (r.status === 'pending') { st.pending++; return; }
    if (r.status !== 'approved') return;
    var type = String(r.class_type || '').replace(/^#/, '');
    if (type === 'تولیدات') {
      var known = PRODUCT_ROWS.some(function (x) { return x[0] === String(r.class_sub || ''); });
      var key = known ? String(r.class_sub) : 'other';
      st.products[key] = (st.products[key] || 0) + 1;
      return;
    }
    if (!st[type]) return;
    st[type].n++; st[type].p += num_(r.participants); // خلاقانه: تعداد بازدید/نفرات
  });
  return st;
}
function myReportText_(reports, posByTg, cityId, cityName, jy, jm, side) {
  var st = sideReportStats_(reports, posByTg, cityId, jy, jm, side);
  function cls(title, x) { return [title, 'تعداد کلاس برگزار شده: ' + x.n, 'تعداد نفرات آموزش داده شده: ' + x.p, '']; }
  var L = [
    '📋 گزارش عملکرد ' + periodName_(jy, jm) + ' — ' + SIDE_FA[side],
    '📍 ناحیه: ' + cityName,
    '👥 گزارش‌های ' + SIDE_ROLES_FA[side],
    '🗓 برنامه‌های ' + periodRangeText_(jy, jm) + ' — فقط گزارش‌های تاییدشده',
    ''
  ].concat(
    cls('🏫 سواد رسانه عمومی:', st['حضوری']),
    cls('💻 کلاس‌های مجازی:', st['مجازی']),
    cls('💪 توانمندسازی گردان:', st['توانمندسازی']),
    ['🎨 سواد رسانه خلاقانه:', 'تعداد نفرات آموزش داده شده: ' + st['خلاقانه'].p, ''],
    ['🎞 بخش تولیدات:'],
    PRODUCT_ROWS.map(function (x) { return x[1] + ': ' + (st.products[x[0]] || 0); })
  );
  if (st.products.other) L.push('سایر: ' + st.products.other);
  if (st.pending) L.push('', '⏳ در انتظار بررسی (هنوز حساب نشده): ' + st.pending);
  var sc = scoreOf_(monthScores_(reports, jy, jm), cityId);
  L.push('', sc.scored ? '⭐️ نمره ماه ناحیه (همه نقش‌ها): ' + sc.score + ' از 5 (' + sc.cover + ' بخش از 5 کار شده)'
    : '⭐️ نمره ماه ناحیه: هنوز هیچ گزارشی امتیاز نگرفته است');
  return L.join('\n');
}

function fallbackPrivate_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id, u = getUserByTg_(tg);
  if (isActiveEmployee_(u) || isAdminTg_(tg)) { tgSend_(chatId, 'از منوی پایین انتخاب کنید:', mainMenuKb_(tg)); return; }
  if (u && u.status === 'disabled') { tgSend_(chatId, TXT.USER_DISABLED); return; }
  if (isHalfRegistered_(u)) { resumeRegistration_(chatId, tg, u); return; }
  if (u && u.status === 'pending' && u.phone) { tgSend_(chatId, TXT.REG_WAIT); return; }
  if (/^\+?[\d\s\-()۰-۹٠-٩]{7,}$/.test(msg.text.trim())) tgSend_(chatId, TXT.SEND_PHONE_BY_BUTTON, contactKb_());
  else tgSend_(chatId, TXT.WELCOME_UNREGISTERED, contactKb_());
}
function fullNameOf_(from) {
  if (!from) return '';
  return normalizeName_(((from.first_name || '') + ' ' + (from.last_name || '')).trim());
}
