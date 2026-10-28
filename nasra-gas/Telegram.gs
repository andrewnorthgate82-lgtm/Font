// Telegram.gs — کلاینت Telegram API (فقط UrlFetchApp)، کیبوردها، doPost/doGet و مسیریاب آپدیت‌ها
// فاز ۲: دستور /admin و همه دکمه‌های «A:» به Admin.gs می‌روند.
// امنیت: doPost فقط وقتی پردازش می‌کند که ?key= برابر GAS_KEY در Script Properties باشد (Worker این کلید را می‌فرستد).

function tgCall_(method, payload, _tried) {
  var token = getProp_('BOT_TOKEN');
  if (!token) throw new Error('BOT_TOKEN در Script Properties تنظیم نشده است');
  bumpFetchCounter_();
  var res;
  try {
    res = UrlFetchApp.fetch('https://api.telegram.org/bot' + token + '/' + method, {
      method: 'post', contentType: 'application/json',
      payload: JSON.stringify(payload || {}), muteHttpExceptions: true
    });
  } catch (e) { // خطای شبکه: یک بار دیگر تلاش کن؛ در شکست دوم null برگردان (نه پرتاب خطا)
    if (!_tried) { Utilities.sleep(1000); return tgCall_(method, payload, true); }
    log_('error', 'telegram:' + method, 'network: ' + (e && e.message), '');
    return null;
  }
  var body = {};
  try { body = JSON.parse(res.getContentText()); } catch (e) { }
  if (res.getResponseCode() === 429 && !_tried) { // محدودیت نرخ تلگرام
    var wait = (body.parameters && body.parameters.retry_after) ? body.parameters.retry_after : 1;
    Utilities.sleep(Math.min(wait, 10) * 1000);
    return tgCall_(method, payload, true);
  }
  if (!body.ok) {
    log_('error', 'telegram:' + method, (body.description || res.getResponseCode()), '');
    return null;
  }
  return body.result;
}
function bumpFetchCounter_() { // برآورد مصرف روزانه UrlFetch برای /status
  try {
    var day = Utilities.formatDate(new Date(), 'Asia/Tehran', 'yyyy-MM-dd'), key = 'fetch_' + day;
    var n = num_(cache_().get(key)) + 1;
    cache_().put(key, String(n), 21600); // سقف انقضای CacheService شش ساعت است
  } catch (e) { }
}
function fetchCountToday_() {
  try {
    var day = Utilities.formatDate(new Date(), 'Asia/Tehran', 'yyyy-MM-dd');
    return num_(cache_().get('fetch_' + day));
  } catch (e) { return 0; }
}
function tgSend_(chatId, text, replyMarkup) {
  var p = { chat_id: chatId, text: text }; // parse_mode فرستاده نمی‌شود: متن خام
  if (replyMarkup) p.reply_markup = replyMarkup;
  return tgCall_('sendMessage', p);
}
function tgEdit_(chatId, messageId, text, replyMarkup) {
  var p = { chat_id: chatId, message_id: messageId, text: text };
  // برای حذف دکمه‌ها null را به کیبورد خالی تبدیل کن (تلگرام null را قابل اعتماد حذف نمی‌کند)
  if (replyMarkup !== undefined) p.reply_markup = (replyMarkup === null ? { inline_keyboard: [] } : replyMarkup);
  return tgCall_('editMessageText', p);
}
function tgEditMarkup_(chatId, messageId, replyMarkup) {
  return tgCall_('editMessageReplyMarkup', { chat_id: chatId, message_id: messageId, reply_markup: replyMarkup });
}
function tgDelete_(chatId, messageId) {
  return tgCall_('deleteMessage', { chat_id: chatId, message_id: messageId });
}
function tgAnswerCb_(cbId, text, alert) {
  return tgCall_('answerCallbackQuery', { callback_query_id: cbId, text: text || '', show_alert: !!alert });
}
// sendMediaGroup تلگرام فقط ۲ تا ۱۰ مورد می‌پذیرد؛ برای ۱ عکس باید sendPhoto زد.
function albumMethodFor_(count) { return count === 1 ? 'sendPhoto' : 'sendMediaGroup'; }
function tgSendAlbum_(chatId, photos, caption) {
  if (!photos || !photos.length) return null;
  if (albumMethodFor_(photos.length) === 'sendPhoto') {
    var p = { chat_id: chatId, photo: photos[0].file_id };
    if (caption) p.caption = caption;
    var one = tgCall_('sendPhoto', p);
    return one ? [one] : null; // در آرایه بپیچ تا فراخواننده‌ها بدون تغییر مثل آلبوم با آن کار کنند
  }
  var media = photos.map(function (p2, i) {
    var m = { type: 'photo', media: p2.file_id };
    if (i === 0 && caption) m.caption = caption; // کپشن روی عکس اول
    return m;
  });
  return tgCall_('sendMediaGroup', { chat_id: chatId, media: media });
}
function tgGetChat_(chatId) { return tgCall_('getChat', { chat_id: chatId }); }

// ---------- کیبوردها ----------
function btn_(text, data) { return { text: text, callback_data: data }; }
function ikb_(rows) { return { inline_keyboard: rows }; }
function paginate_(items, page, per) {
  var total = Math.max(1, Math.ceil(items.length / per));
  page = Math.max(0, Math.min(page, total - 1));
  return { chunk: items.slice(page * per, (page + 1) * per), page: page, total: total };
}
function citiesKb_(prefix, page) {
  var p = paginate_(getCities_(), page || 0, CFG.CITIES_PER_PAGE), rows = [], row = [];
  p.chunk.forEach(function (c) {
    row.push(btn_(c.name, prefix + ':' + c.city_id));
    if (row.length === 2) { rows.push(row); row = []; }
  });
  if (row.length) rows.push(row);
  var nav = [];
  if (p.page > 0) nav.push(btn_('⬅️ قبلی', 'pg:' + prefix + ':' + (p.page - 1)));
  if (p.page < p.total - 1) nav.push(btn_('بعدی ➡️', 'pg:' + prefix + ':' + (p.page + 1)));
  if (nav.length) rows.push(nav);
  rows.push([btn_('❌ انصراف', 'cx')]);
  return ikb_(rows);
}
function contactKb_() {
  return { keyboard: [[{ text: '📱 ارسال شماره من', request_contact: true }]], resize_keyboard: true, one_time_keyboard: true };
}
function mainMenuKb_() {
  return {
    keyboard: [[{ text: MENU.NEW_REPORT }, { text: MENU.SAMPLES }], [{ text: MENU.MY_REPORTS }, { text: MENU.MY_TEACHERS }]],
    resize_keyboard: true
  };
}
function removeKb_() { return { remove_keyboard: true }; }
function backCancelRows_() { return [[btn_('↩️ بازگشت', 'bk'), btn_('❌ انصراف', 'cx')]]; }
function setupBtnKb_(data, text) { return ikb_([[btn_(text || 'بررسی کردم، ادامه', data)]]); }

// ---------- نقطه ورود Web App ----------
function doGet(e) {
  return ContentService.createTextOutput('Nasra bot is running.').setMimeType(ContentService.MimeType.TEXT);
}
function doPost(e) {
  var startedAt = Date.now();
  try {
    var key = e && e.parameter ? e.parameter.key : '';
    if (!key || key !== getProp_('GAS_KEY')) return ContentService.createTextOutput('forbidden');
    var update = JSON.parse(e.postData.contents);
    var lock = LockService.getScriptLock();
    if (!lock.tryLock(25000)) { // قفل گرفته نشد: آپدیت seen نمی‌شود تا ورکر دوباره تلاش کند
      recordUpdateStats_(0, true);
      log_('warn', 'lock-timeout', '', update.update_id);
      return ContentService.createTextOutput('retry');
    }
    try {
      if (seenUpdate_(update.update_id)) return ContentService.createTextOutput('ok'); // تکراری (تلاش مجدد ورکر) — داخل قفل
      log_('info', 'update', Object.keys(update).filter(function (k) { return k !== 'update_id'; }).join(','), update.update_id);
      routeUpdate_(update);
      recordUpdateStats_(Date.now() - startedAt, false);
    } finally { lock.releaseLock(); }
  } catch (err) {
    log_('error', 'doPost', err && err.message, '');
    notifyError_(err, e);
  }
  return ContentService.createTextOutput('ok');
}
function notifyError_(err, e) {
  try {
    superadminTgs_().forEach(function (tg) {
      var rlKey = 'errpm_' + tg; // حداکثر یک پیام خطا در ۵ دقیقه برای هر سوپرادمین
      if (cache_().get(rlKey)) return;
      try { cache_().put(rlKey, '1', 300); } catch (ignored) { }
      tgSend_(tg, '⚠️ خطای بات: ' + String(err && err.message || err).slice(0, 200));
    });
    var update = JSON.parse(e.postData.contents);
    var msg = update.message || (update.callback_query && update.callback_query.message);
    if (msg && msg.chat && msg.chat.type === 'private') tgSend_(msg.chat.id, TXT.GENERIC_ERROR);
  } catch (ignored) { }
}

// ---------- مسیریاب ----------
function cleanText_(s) {
  // حذف نویسه‌های نادیدنیِ جهت‌نما (LRM/RLM و…) که هنگام کپی‌کردن دستورها از متن‌های فارسی می‌چسبند
  // و مقایسه دقیق دستورها (مثل ‎/status‎) را خراب می‌کنند. نیم‌فاصله (ZWNJ) و ZWJ سالم می‌مانند.
  return String(s || '').replace(/[\u200E\u200F\u202A-\u202E\u2066-\u2069\uFEFF]/g, '').trim();
}

function routeUpdate_(u) {
  if (u.callback_query) return routeCallback_(u.callback_query);
  if (!u.message) return; // my_chat_member و channel_post در فاز ۱ پردازش نمی‌شوند
  var msg = u.message, chatType = msg.chat && msg.chat.type;
  if (chatType === 'private') return routePrivateMessage_(msg);
  if (chatType === 'group' || chatType === 'supergroup') return routeGroupMessage_(msg);
}

function routePrivateMessage_(msg) {
  var chatId = msg.chat.id, tg = msg.from && msg.from.id;
  if (!tg) return;
  var session = getSession_(chatId, tg);

  // ادمین در حال ثبت «نمونه گزارش»: هر پیام (متن/عکس/آلبوم/فوروارد کانال) جزو نمونه است؛ جز دستورها و دکمه‌های منو
  if (session.state === 'adm:sample') {
    var raw = String(msg.text || '').trim(), isMenu = Object.keys(MENU).some(function (k) { return MENU[k] === raw; });
    if (raw.charAt(0) !== '/' && !isMenu) { handleSampleMsg_(msg, session); return; }
  }
  if (isChannelForward_(msg, session, tg)) { handleChannelForward_(msg); return; }
  if (msg.contact) {
    if (session.state === 'adm:addAdmin') { handleAdminContact_(msg, session); return; } // فاز ۲: افزودن ادمین با مخاطب
    handleContact_(msg); return;
  }

  if (msg.photo) {
    if (session.state === 'form:photos') { handleFormPhoto_(msg, session); return; }
    tgSend_(chatId, 'برای ارسال گزارش، از دکمه «' + MENU.NEW_REPORT + '» شروع کنید.', mainMenuKb_());
    return;
  }
  if (msg.document && session.state === 'form:photos') { tgSend_(chatId, TXT.SEND_AS_PHOTO); return; }
  if (!msg.text) return;

  var text = cleanText_(msg.text);
  if (text.indexOf('/claim') === 0) { handleClaim_(msg, text.replace('/claim', '').trim()); return; }
  if (text === '/start') { handleStart_(msg); return; }
  if (text === '/status') { handleStatus_(msg); return; }
  if (text === '/admin' || text === '/panel') { handleAdminCommand_(msg); return; } // فاز ۲: پنل مدیریت
  if (text === '/myid') {
    tgSend_(chatId, '🆔 آیدی عددی تلگرام شما: ' + tg + '\n(برای افزودن دستی در برگه‌های Users/Admins از همین عدد استفاده می‌شود.)');
    return;
  }
  if (text === '/reload') { // فقط ادمین: پاک‌کردن کش‌های مرجع
    if (!isAdminTg_(tg)) { tgSend_(chatId, TXT.NO_ACCESS); return; }
    bustCache_();
    tgSend_(chatId, '✅ کش‌ها پاک شدند؛ شهرستان‌ها، تنظیمات، مدرسان، ادمین‌ها و لیست مجاز از برگه‌ها تازه خوانده می‌شوند.');
    return;
  }
  if (text === '/setchannel') { // فقط سوپرادمین: اجازه تغییر کانال پس از پایان راه‌اندازی
    if (!isSuperTg_(tg)) { tgSend_(chatId, TXT.NO_ACCESS); return; }
    var sc = getSession_(chatId, tg);
    sc.state = 'setup:channel'; saveSession_(chatId, tg, sc);
    tgSend_(chatId, 'حالت تغییر کانال فعال شد.\nبات را در کانال جدید ادمین کنید (ارسال+ویرایش+حذف پیام) و یک پست از همان کانال را همین‌جا فوروارد کنید تا جایگزین شود.\nبرای لغو: /cancel');
    return;
  }
  if (text === '/cancel') {
    clearSession_(chatId, tg);
    var u = getUserByTg_(tg);
    tgSend_(chatId, TXT.CANCELLED, isActiveEmployee_(u) || isAdminTg_(tg) ? mainMenuKb_() : removeKb_());
    return;
  }
  if (text === MENU.NEW_REPORT || text === MENU.NEW_REPORT_OLD) { menuNewReport_(msg); return; }
  if (text === MENU.SAMPLES) { menuSamples_(msg); return; }
  if (text === MENU.MY_REPORTS) { menuMyReports_(msg); return; }
  if (text === MENU.MY_TEACHERS) { menuMyTeachers_(msg); return; }

  if (session.state === 'reg:name') { handleRegName_(msg, session); return; }
  if (session.state.indexOf('adm:') === 0) { handleAdminText_(msg, session); return; } // فاز ۲: ورودی‌های پنل
  if (session.state.indexOf('form:') === 0) { handleFormText_(msg, session); return; }
  fallbackPrivate_(msg);
}

function routeGroupMessage_(msg) {
  var text = cleanText_(msg.text);
  if (text === '/here' || text.indexOf('/here@') === 0) { handleHereGroup_(msg); return; }
  var tg = msg.from && msg.from.id;
  if (!tg || !text) return;
  var session = getSession_(msg.chat.id, tg);
  if (session.state === 'review:manual') handleReviewManualText_(msg, session);
}

function routeCallback_(cb) {
  var data = String(cb.data || '');
  var chatId = cb.message && cb.message.chat && cb.message.chat.id;
  var tg = cb.from && cb.from.id;
  if (!chatId || !tg) { tgAnswerCb_(cb.id); return; }
  var session = getSession_(chatId, tg);

  if (data.indexOf('A:') === 0) return handleAdminCb_(cb, session, data); // فاز ۲: پنل مدیریت
  if (data === 's1' || data === 's2' || data === 'st') return handleSetupCb_(cb, data);
  if (data.indexOf('rc:') === 0 || data.indexOf('pg:rc:') === 0) return handleRegCityCb_(cb, session, data);
  if (data.indexOf('ga:') === 0 || data.indexOf('gd:') === 0) return handleRegDecisionCb_(cb, data);
  if (data.indexOf('re:') === 0) return handleResubmitCb_(cb, session, data);
  if (data.indexOf('sm:') === 0) return handleSampleCb_(cb, data); // نمونه گزارش‌ها
  if (/^(ok|rj|mn|rs|rd|rb|rm):/.test(data)) return handleReviewCb_(cb, session, data);
  return handleFormCb_(cb, session, data); // بقیه کال‌بک‌ها مال فرم گزارش‌اند
}

function isChannelForward_(msg, session, tg) {
  // فقط فورواردِ ادمین (در حال راه‌اندازی/تغییر کانال) «فوروارد کانال برای راه‌اندازی» است؛
  // عکس فورواردشده از کانال برای کارمند در فرم گزارش، عکس عادی فرم است و این‌جا گیر نمی‌کند.
  if (!isAdminTg_(tg)) return false;
  if (msg.photo && !(session && session.state === 'adm:addChannel')) return false; // فوروارد پست عکس‌دار فقط هنگام «افزودن کانال» در پنل
  if (session && String(session.state || '').indexOf('form:') === 0) return false;
  var o = msg.forward_origin;
  if (o && o.type === 'channel' && o.chat) return true;
  return !!(msg.forward_from_chat && msg.forward_from_chat.type === 'channel');
}
function channelFromForward_(msg) {
  var o = msg.forward_origin;
  if (o && o.type === 'channel' && o.chat) return { id: o.chat.id, title: o.chat.title || '' };
  if (msg.forward_from_chat && msg.forward_from_chat.type === 'channel') {
    return { id: msg.forward_from_chat.id, title: msg.forward_from_chat.title || '' };
  }
  return null;
}

// کارمند فعال را برمی‌گرداند؛ وگرنه پیام مناسب می‌دهد و null برمی‌گرداند.
function denyEmployee_(chatId, tg) {
  var u = getUserByTg_(tg);
  if (isActiveEmployee_(u)) { u._city = getCityById_(u.city_id); return u; }
  if (u && u.status === 'disabled') tgSend_(chatId, TXT.USER_DISABLED);
  else if (u && u.status === 'pending' && u.phone && !(num_(u.city_id) > 0)) resumeRegistration_(chatId, tg, u); // نیمه‌کاره: ادامه ثبت‌نام
  else if (u && u.status === 'pending' && u.phone) tgSend_(chatId, TXT.REG_WAIT);
  else if (u && u.status === 'rejected') tgSend_(chatId, TXT.REG_REJECTED);
  else if (isAdminTg_(tg)) tgSend_(chatId, 'شما ادمین هستید ولی شهرستانی برای حسابتان ثبت نشده است؛ برای تست ثبت گزارش، با یک حساب کارمند آزمایشی وارد شوید یا ثبت‌نام کارمندی را با دکمه «📱 ارسال شماره من» کامل کنید.');
  else tgSend_(chatId, TXT.WELCOME_UNREGISTERED, contactKb_());
  return null;
}
function sendMainMenu_(chatId, tg) {
  var u = getUserByTg_(tg);
  var name = (u && u.full_name) || 'همکار گرامی';
  var city = u ? getCityById_(u.city_id) : null;
  if (isAdminTg_(tg) && !(u && num_(u.city_id) > 0)) {
    tgSend_(chatId, 'سلام ' + name + ' 👋\nشما دسترسی ادمین دارید.\n• پنل مدیریت: /admin\n• وضعیت سیستم: /status\n• لیست مجاز شماره‌ها در گوگل‌شیت، برگه Whitelist است.', mainMenuKb_());
  } else {
    tgSend_(chatId, 'سلام ' + name + ' 👋\nشهرستان شما: ' + (city ? city.name : '—') +
      (isAdminTg_(tg) ? '\n🛠 پنل مدیریت: /admin' : '') + '\nاز منوی زیر انتخاب کنید:', mainMenuKb_());
  }
}
