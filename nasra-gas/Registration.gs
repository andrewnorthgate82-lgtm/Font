// Registration.gs — ‎/start‎، ثبت‌نام با شماره تلفن + لیست مجاز + تایید دستی ادمین، منوی اصلی کارمند

// ادامهٔ ثبت‌نام نیمه‌کاره (شماره دارد ولی شهرستان/نام کامل نشده) — از قدم شهرستان
function resumeRegistration_(chatId, tg, user) {
  var s = getSession_(chatId, tg);
  s.state = 'reg:city';
  s.data = { phone: normalizePhone_(user.phone) || String(user.phone || ''), cityPage: 0 };
  saveSession_(chatId, tg, s);
  tgSend_(chatId, 'ثبت‌نام شما نیمه‌کاره مانده بود؛ از همان‌جا ادامه می‌دهیم 🙂\n' + TXT.ASK_CITY, citiesKb_('rc', 0));
}
function isHalfRegistered_(u) {
  return !!(u && u.status === 'pending' && u.phone && !(num_(u.city_id) > 0));
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
  if (allowed) {
    updateUser_(user.user_id, tg, {
      phone: phone, full_name: allowed.full_name || user.full_name,
      city_id: allowed.city_id, status: 'approved'
    });
    var city = getCityById_(allowed.city_id);
    tgSend_(chatId,
      '✅ ثبت‌نام شما کامل شد!\nنام: ' + (allowed.full_name || user.full_name) +
      '\nشهرستان: ' + (city ? city.name : '') + '\n\nاز منوی زیر می‌توانید گزارش ثبت کنید.', mainMenuKb_());
    return;
  }
  // مسیر تایید دستی: شهرستان ← نام ← ارسال به گروه ادمین‌ها
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
  updateUser_(user.user_id, tg, {
    full_name: name, city_id: num_(session.data.city_id), status: 'pending'
  });
  clearSession_(chatId, tg);
  tgSend_(chatId, TXT.REG_SENT, removeKb_());

  // ارسال درخواست به گروه بررسی (شماره فقط برای ادمین‌ها دیده می‌شود، در Logs نمی‌نویسیم)
  var fresh = getUserByTg_(tg), city = getCityById_(fresh.city_id);
  var text = '👤 درخواست ثبت‌نام جدید\nنام: ' + fresh.full_name +
    '\nشهرستان: ' + (city ? city.name : '') +
    '\nشماره: ' + fresh.phone + // عمداً فقط در همین پیام ادمین‌ها؛ در برگه Logs ثبت نمی‌شود
    '\nآیدی تلگرام: ' + fresh.telegram_id;
  var kb = ikb_([[btn_('✅ تایید ثبت‌نام', 'ga:' + fresh.user_id), btn_('❌ رد ثبت‌نام', 'gd:' + fresh.user_id)]]);
  var groupId = getSettingNum_('review_group_id'), sent = null;
  if (groupId) sent = tgSend_(groupId, text, kb);
  if (sent) updateUser_(fresh.user_id, tg, { reg_group_message_id: sent.message_id });
  else superadminTgs_().forEach(function (a) { tgSend_(a, text, kb); });
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
  if (approve) {
    var city = getCityById_(user.city_id);
    tgSend_(user.telegram_id, '🎉 ثبت‌نام شما تایید شد!\nنام: ' + user.full_name +
      '\nشهرستان: ' + (city ? city.name : '') + '\nاز منوی زیر انتخاب کنید:', mainMenuKb_());
  } else tgSend_(user.telegram_id, TXT.REG_REJECTED);
}

// ---------- منوی اصلی کارمند ----------
function menuNewReport_(msg) {
  var u = denyEmployee_(msg.chat.id, msg.from.id);
  if (!u) return;
  startReport_(msg.chat.id, msg.from.id, u);
}
function menuMyReports_(msg) {
  var u = denyEmployee_(msg.chat.id, msg.from.id);
  if (!u) return;
  var rows = myReports_(msg.from.id, u.city_id, 10);
  if (!rows.length) { tgSend_(msg.chat.id, 'هنوز گزارشی ثبت نکرده‌اید.'); return; }
  var lines = ['📋 آخرین گزارش‌های شما:'];
  rows.forEach(function (r) {
    lines.push((STATUS_EMOJI[r.status] || '') + ' ' + r.subject + ' — ' + String(cellToStr_(r.event_jalali)) + ' — ' + (STATUS_FA[r.status] || r.status));
  });
  lines.push('\n(گزارش ردشده را از پیام رد، با دکمه «✏️ اصلاح و ارسال مجدد» اصلاح کنید.)');
  tgSend_(msg.chat.id, lines.join('\n'));
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
function fallbackPrivate_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id, u = getUserByTg_(tg);
  if (isActiveEmployee_(u) || isAdminTg_(tg)) { tgSend_(chatId, 'از منوی پایین انتخاب کنید:', mainMenuKb_()); return; }
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
