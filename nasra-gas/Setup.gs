// Setup.gs — راه‌اندازی یک‌باره (setup)، توابع وبهوک، و جادوگر راه‌اندازی داخل بات (/claim ، /here ، کانال، تست، /status)

var HEADER_NOTES = {
  Settings: { key: 'نام تنظیم — دست نزنید', value: 'مقدار تنظیم (کد راه‌اندازی و آیدی گروه/کانال این‌جاست)', note: 'توضیح — اگر ردیف debug با مقدار 1 بسازید، لاگ‌های info هم در Logs نوشته می‌شوند' },
  Cities: { city_id: 'شماره شهرستان — دست نزنید', name: 'نام نمایشی شهرستان', hashtag: 'هشتگ کانال؛ با # شروع شود، بدون فاصله و پرانتز', is_active: '۱ یعنی فعال، ۰ یعنی غیرفعال' },
  Users: { telegram_id: 'آیدی عددی تلگرام', phone: 'شماره موبایل تأییدشده', full_name: 'نام و نام‌خانوادگی', city_id: 'شماره شهرستان', status: 'pending یعنی در انتظار تایید، approved تاییدشده، rejected ردشده، disabled غیرفعال', position: 'نقش در شهرستان (از فهرست کشویی انتخاب کنید)؛ خواهر/برادر فایل اکسل از همین ستون است' },
  Whitelist: { phone: 'شماره موبایل همکار (مثل 09123456789) — همین‌جا در شیت بچسبانید', full_name: 'نام و نام‌خانوادگی', city_name: 'نام شهرستان دقیقاً مثل برگه Cities', city_id: 'اختیاری: اگر خالی باشد بات با city_name پیدا می‌کند' },
  Admins: { telegram_id: 'آیدی عددی ادمین', role: 'superadmin یا admin', is_active: '۱ یعنی فعال' },
  Instructors: { city_id: 'شماره شهرستان', name: 'نام مدرس/سخنران', name_norm: 'فرم نرمال نام برای جلوگیری از تکراری — دست نزنید' },
  Reports: { status: 'pending/approved/rejected/deleted', event_jalali: 'تاریخ برگزاری شمسی', publish_jalali: 'تاریخ درج در کانال (لحظه تایید)', resubmit_count: 'تعداد ارسال مجدد بعد از رد', submitter_position: 'نقش فرستنده در لحظه ارسال گزارش' },
  Sessions: { state: 'قدم فعلی فرم کاربر — دست نزنید', data_json: 'داده‌های فرم نیمه‌کاره — دست نزنید' },
  Logs: { event: 'فقط هشدار و خطا این‌جا ثبت می‌شود؛ آمار آپدیت‌ها در کش و در ‪/status‬ است' }
};

function setup() {
  var active = SpreadsheetApp.getActiveSpreadsheet();
  if (active) setProp_('SPREADSHEET_ID', active.getId()); // از این به بعد ss_() با openById باز می‌کند
  ensureSheets_();
  seedCitiesIfEmpty_();
  var props = PropertiesService.getScriptProperties();
  var made = [];
  if (!props.getProperty('GAS_KEY')) { props.setProperty('GAS_KEY', randomSecret_()); made.push('GAS_KEY'); }
  if (!props.getProperty('TG_SECRET')) { props.setProperty('TG_SECRET', randomSecret_()); made.push('TG_SECRET'); }
  var code = ensureSetupCode_();
  ensureBackupTrigger_(); // تریگرهای بکاپ شبانه و کارهای روزانه (Automation.gs)
  bustCache_();
  Logger.log('✅ setup تمام شد. (SPREADSHEET_ID در Script Properties ذخیره شد)');
  Logger.log('این سه مقدار را الآن کپی کنید و در Cloudflare Worker (Settings ← Variables and Secrets) به‌صورت Secret بگذارید:');
  Logger.log('GAS_KEY = ' + props.getProperty('GAS_KEY') + (made.indexOf('GAS_KEY') === -1 ? '  (از قبل ساخته شده بود)' : ''));
  Logger.log('TG_SECRET = ' + props.getProperty('TG_SECRET') + (made.indexOf('TG_SECRET') === -1 ? '  (از قبل ساخته شده بود)' : ''));
  Logger.log('GAS_URL = آدرس /exec وب‌اپ (بعد از Deploy کپی می‌شود)');
  if (code) Logger.log('کد راه‌اندازی (همچنین در برگه Settings ذخیره شد) — در بات بنویسید:  /claim ' + code);
  if (!props.getProperty('BOT_TOKEN')) Logger.log('⚠️ هنوز BOT_TOKEN را در Script Properties نگذاشته‌اید.');
  if (!props.getProperty('WORKER_URL')) Logger.log('⚠️ هنوز WORKER_URL را در Script Properties نگذاشته‌اید.');
}

function ensureSheets_() {
  var book = ss_();
  Object.keys(SHEETS).forEach(function (name) {
    var sh = book.getSheetByName(name);
    if (!sh) sh = book.insertSheet(name);
    var headers = SHEETS[name];
    var first = sh.getRange(1, 1, 1, headers.length).getValues()[0];
    var needs = headers.some(function (h, i) { return String(first[i]) !== h; });
    if (needs) sh.getRange(1, 1, 1, headers.length).setValues([headers]);
    // همه ستون‌ها (حتی برگه‌های از قبل ساخته‌شده) «متن ساده» تا ساعت 21:00، تاریخ 1405/07/23،
    // کلید نشست 123:456، شمارهٔ با صفر اول و رشته‌های شروع‌شده با = به زمان/تاریخ/عدد/فرمول تبدیل نشوند.
    sh.getRange(1, 1, sh.getMaxRows(), headers.length).setNumberFormat('@');
    sh.setFrozenRows(1);
    try { sh.setRightToLeft(true); } catch (e) { }
    var notes = HEADER_NOTES[name] || {};
    headers.forEach(function (h, i) { if (notes[h]) sh.getRange(1, i + 1).setNote(notes[h]); });
  });
  applyValidations_();
  var def = book.getSheetByName('Sheet1'); // برگهٔ پیش‌فرض خالی را پاک کن اگر هست و خالی است
  if (def && def.getLastRow() === 0) book.deleteSheet(def);
}
function applyValidations_() {
  function listRule(sheetName, col, values) {
    var sh = sheet_(sheetName);
    if (!sh) return;
    var rule = SpreadsheetApp.newDataValidation().requireValueInList(values, true).setAllowInvalid(true).build();
    sh.getRange(2, col, 2000, 1).setDataValidation(rule);
  }
  // ستون‌ها بر اساس ترتیب هدرهای SHEETS
  listRule('Users', 7, ['pending', 'approved', 'rejected', 'disabled']);       // status
  listRule('Users', 6, ['superadmin', 'admin', 'employee']);                    // role
  listRule('Admins', 3, ['superadmin', 'admin']);                               // role
  listRule('Reports', 7, ['draft', 'pending', 'approved', 'rejected', 'deleted']); // status
  listRule('Cities', 4, ['1', '0']);                                            // is_active
  listRule('Users', SHEETS.Users.indexOf('position') + 1, POSITIONS);            // نقش در شهرستان
}
function seedCitiesIfEmpty_() {
  if (readTable_('Cities').length) return;
  var rows = CITIES_SEED.map(function (c) { return [String(c[0]), c[1], c[2], '1']; });
  sheet_('Cities').getRange(2, 1, rows.length, 4).setValues(rows); // نوشتن دسته‌ای
}
function randomSecret_() {
  var chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789', out = '';
  for (var i = 0; i < 32; i++) out += chars.charAt(Math.floor(Math.random() * chars.length));
  return out;
}
function ensureSetupCode_() {
  if (hasSuperadmin_()) return null;
  var existing = getSetting_('setup_code');
  if (existing) return existing;
  var chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', code = '';
  for (var i = 0; i < 8; i++) code += chars.charAt(Math.floor(Math.random() * chars.length));
  setSetting_('setup_code', code, 'کد راه‌اندازی یک‌بارمصرف — در بات بنویسید: ‎/claim‎ کد. پس از استفاده خودکار حذف می‌شود');
  return code;
}
// ensureBackupTrigger_ و nightlyBackup از فاز ۳ در فایل Automation.gs هستند.

// ---------- وبهوک: فقط از مسیر Worker ثبت می‌شود، هرگز مستقیم به /exec ----------
function setWebhook() {
  var worker = getProp_('WORKER_URL'), secret = getProp_('TG_SECRET');
  if (!worker) throw new Error('WORKER_URL در Script Properties نیست');
  var res = tgCall_('setWebhook', {
    url: worker, secret_token: secret, drop_pending_updates: true,
    allowed_updates: ['message', 'callback_query', 'my_chat_member', 'channel_post']
  });
  Logger.log('setWebhook result: ' + JSON.stringify(res));
  Logger.log('حالا تابع checkWebhook را اجرا کنید؛ نتیجه سالم یعنی pending_update_count برابر 0 و بدون last_error_message.');
}
function checkWebhook() {
  var info = tgCall_('getWebhookInfo', {});
  Logger.log(JSON.stringify(info, null, 2));
  if (info) {
    Logger.log('url = ' + info.url);
    Logger.log('pending_update_count = ' + info.pending_update_count + '  (سالم: 0)');
    Logger.log('last_error_message = ' + (info.last_error_message || '(ندارد — سالم است)'));
  }
}
function deleteWebhook() {
  Logger.log(JSON.stringify(tgCall_('deleteWebhook', { drop_pending_updates: true })));
}

// ---------- جادوگر راه‌اندازی داخل بات ----------
function handleClaim_(msg, code) {
  var chatId = msg.chat.id, tg = msg.from.id;
  if (hasSuperadmin_()) { tgSend_(chatId, TXT.CLAIM_ALREADY); return; }
  var saved = getSetting_('setup_code');
  if (!code || !saved || code.trim() !== saved) { tgSend_(chatId, TXT.CLAIM_BAD); return; }
  var user = ensureUser_(tg, fullNameOf_(msg.from));
  updateUser_(user.user_id, tg, { status: 'approved' });
  appendRow_('Admins', { telegram_id: tg, full_name: user.full_name || fullNameOf_(msg.from), role: 'superadmin', is_active: 1, added_at: nowStr_() });
  deleteSetting_('setup_code'); // کد فقط یک‌بار
  bustCache_();
  log_('warn', 'claim', 'superadmin registered tg=' + tg, '');
  tgSend_(chatId, TXT.CLAIM_OK);
  tgSend_(chatId, TXT.SETUP_STEP1, setupBtnKb_('s1'));
}
function handleHereGroup_(msg) {
  var tg = msg.from.id;
  if (!isAdminTg_(tg)) { tgSend_(msg.chat.id, TXT.NO_ACCESS); return; }
  setSetting_('review_group_id', msg.chat.id, 'آیدی گروه بررسی ادمین‌ها');
  tgSend_(msg.chat.id, TXT.GROUP_SAVED);
  tgSend_(tg, TXT.SETUP_STEP2, setupBtnKb_('s2')); // قدم بعدی در خصوصی (اگر بات را استارت نکرده باشند، دکمه «بررسی کردم، ادامه» در خصوصی کار می‌کند)
}
function handleChannelForward_(msg) {
  var chatId = msg.chat.id, tg = msg.from.id;
  if (!isAdminTg_(tg)) return; // فقط ادمین در حال راه‌اندازی
  var session = getSession_(chatId, tg);
  var inSetup = getSetting_('setup_done') !== '1';           // هنوز راه‌اندازی اولیه تمام نشده
  var explicitChange = session.state === 'setup:channel';    // دستور صریح /setchannel (فقط سوپرادمین)
  if (!inSetup && !explicitChange) {
    tgSend_(chatId, 'ℹ️ کانال قبلاً تنظیم شده و بدون دستور تغییر نمی‌کند.\nاگر سوپرادمین هستید و می‌خواهید کانال را عوض کنید، اول بنویسید:\n/setchannel\nبعد یک پست از کانال جدید فوروارد کنید.');
    return;
  }
  var found = channelFromForward_(msg);
  if (!found) {
    if (!getSettingNum_('channel_id') || explicitChange) tgSend_(chatId, TXT.CHANNEL_FORWARD_BAD);
    return;
  }
  setSetting_('channel_id', found.id, 'آیدی کانال انتشار');
  setSetting_('channel_title', found.title, 'نام کانال انتشار');
  tgSend_(chatId, '✅ کانال «' + (found.title || 'کانال') + '» ذخیره شد.');
  if (explicitChange) {
    clearSession_(chatId, tg);
    tgSend_(chatId, 'از این به بعد گزارش‌های تاییدشده در همین کانال منتشر می‌شوند.', mainMenuKb_());
    return;
  }
  tgSend_(chatId, TXT.SETUP_STEP3, setupBtnKb_('st', '✅ تست کامل راه‌اندازی'));
}
function testSendDelete_(chatId, label, fixHint) {
  var m = tgSend_(chatId, '🤖 پیام آزمایشی راه‌اندازی بات نسرا (حذف می‌شود)');
  if (!m) return '❌ ' + label + ': بات نتوانست پیام بفرستد. راه‌حل: ' + fixHint;
  if (!tgDelete_(chatId, m.message_id)) {
    return '⚠️ ' + label + ': پیام ارسال شد ولی حذف نشد؛ اجازه «حذف پیام» را به بات بدهید (پیام آزمایشی را دستی حذف کنید).';
  }
  return '✅ ' + label + ': ارسال و حذف پیام آزمایشی موفق بود.';
}
function handleSetupCb_(cb, data) {
  var tg = cb.from.id, chatId = cb.message.chat.id;
  if (!isAdminTg_(tg)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  if (data === 's1') {
    if (getSettingNum_('review_group_id')) { tgAnswerCb_(cb.id, 'گروه بررسی ذخیره شده است ✅'); tgSend_(chatId, TXT.SETUP_STEP2, setupBtnKb_('s2')); }
    else tgAnswerCb_(cb.id, 'هنوز گروه ذخیره نشده: بات را به گروه بررسی اضافه و ادمین کنید و در همان گروه /here بفرستید.', true);
    return;
  }
  if (data === 's2') {
    if (getSettingNum_('channel_id')) { tgAnswerCb_(cb.id, 'کانال ذخیره شده است ✅'); tgSend_(chatId, TXT.SETUP_STEP3, setupBtnKb_('st', '✅ تست کامل راه‌اندازی')); }
    else tgAnswerCb_(cb.id, 'هنوز کانال ذخیره نشده: بات را در کانال ادمین کنید و یک پست کانال را اینجا فوروارد کنید.', true);
    return;
  }
  if (data === 'st') {
    tgAnswerCb_(cb.id, 'در حال تست…');
    var groupId = getSettingNum_('review_group_id'), channelId = getSettingNum_('channel_id');
    var lines = ['نتیجه تست راه‌اندازی:'], ok = true, r;
    if (groupId) {
      r = testSendDelete_(groupId, 'گروه بررسی', 'بات را به گروه اضافه کنید، ادمین گروهش کنید و دوباره در گروه /here بفرستید.');
      lines.push(r); ok = ok && r.indexOf('✅') === 0;
    } else { lines.push('❌ گروه بررسی ثبت نشده است؛ بات را به گروه اضافه و ادمین کنید و در گروه /here بفرستید.'); ok = false; }
    if (channelId) {
      r = testSendDelete_(channelId, 'کانال انتشار', 'در کانال، بات را از بخش مدیران (Administrators) ادمین کنید و اجازه‌های ارسال، ویرایش و حذف پیام را روشن کنید؛ بعد یک پست کانال را دوباره اینجا فوروارد کنید.');
      lines.push(r); ok = ok && r.indexOf('✅') === 0;
    } else { lines.push('❌ کانال ثبت نشده است؛ بات را در کانال ادمین کنید و یک پست کانال را اینجا فوروارد کنید.'); ok = false; }
    tgSend_(chatId, lines.join('\n'));
    if (ok) { setSetting_('setup_done', '1', 'راه‌اندازی کامل شده است'); tgSend_(chatId, TXT.SETUP_DONE, mainMenuKb_()); }
  }
}
function handleStatus_(msg) {
  var tg = msg.from.id;
  if (!isAdminTg_(tg)) { tgSend_(msg.chat.id, TXT.NO_ACCESS); return; }
  var groupId = getSettingNum_('review_group_id'), channelId = getSettingNum_('channel_id');
  function probe(id) {
    if (!id) return 'ثبت نشده ❌';
    var c = tgGetChat_(id);
    return c ? 'متصل ✅ «' + (c.title || '') + '»' : 'ثبت شده ولی در دسترس نیست ⚠️ (بات باید در گروه/کانال عضو و ادمین بماند)';
  }
  var approved = readTable_('Users').filter(function (u) { return u.status === 'approved'; }).length;
  var st = getExecStats24h_();
  tgSend_(msg.chat.id,
    '📊 وضعیت سیستم\n' +
    '• سوپرادمین ثبت شده: ' + (hasSuperadmin_() ? 'بله ✅' : 'خیر ❌ (حالت راه‌اندازی)') + '\n' +
    '• گروه بررسی: ' + probe(groupId) + '\n' +
    '• کانال انتشار: ' + probe(channelId) + '\n' +
    '• شهرستان‌های فعال: ' + getCities_().length + '\n' +
    '• کاربران تاییدشده: ' + approved + '\n' +
    '• گزارش‌های در انتظار بررسی: ' + countReports_('pending') + '\n' +
    '• گزارش‌های تاییدشده: ' + countReports_('approved') + '\n' +
    '• آپدیت‌های پردازش‌شده امروز: ' + st.updatesToday + '\n' +
    '• زمان اجرای آپدیت (۲۴ ساعت اخیر): میانگین ' + st.avgSec + ' ثانیه — بیشترین ' + st.maxSec + ' ثانیه (سقف هر اجرا ۶ دقیقه)\n' +
    '• تایم‌اوت قفل در ۲۴ ساعت اخیر: ' + st.lockTimeouts + ' (ورکر خودش دوباره تلاش می‌کند)\n' +
    '• برآورد تماس‌های امروز به تلگرام (سقف رایگان گوگل حدود ۲۰٬۰۰۰ در روز): ' + fetchCountToday_() + '\n' +
    '• آخرین بکاپ: ' + (getSetting_('last_backup_at') || '— هنوز گرفته نشده (در ویرایشگر یک بار installPhase3 را Run کنید)') + '\n' +
    '• بکاپ شبانه و کارهای روزانه: ' + (triggerStatus_().backup && triggerStatus_().daily ? 'فعال ✅' : 'کامل نصب نشده ⚠️ (installPhase3)') + '\n' +
    'برای سلامت وبهوک در ویرایشگر Apps Script تابع checkWebhook را Run کنید.');
}
