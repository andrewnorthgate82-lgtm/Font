// Automation.gs — فاز ۳: بکاپ شبانه خودکار، کارهای روزانه (یادآوری ماهانه، هشدار گزارش معطل، خلاصه ماه)،
// صف پیام همگانی، و خروجی اکسل واقعی (.xlsx) ماهانه.
// نصب یک‌باره: در ویرایشگر تابع installPhase3 را Run کنید (اجازه دسترسی به Google Drive برای بکاپ لازم است).
// تنظیمات قابل تغییر در برگه Settings (اگر نباشند، مقدار پیش‌فرض استفاده می‌شود):
//   backup_keep = 14            تعداد بکاپ‌هایی که نگه داشته می‌شود (قدیمی‌ترها به سطل زباله Drive می‌روند)
//   reminder_enabled = 1        یادآوری ماهانه روشن (0 = خاموش)
//   reminder_days = 20,27       روزهای ماه شمسی که یادآوری به شهرستان‌های بدون گزارش فرستاده می‌شود
//   pending_alert_hours = 24    گزارشی که بیش از این ساعت در انتظار بماند، در گروه بررسی یادآوری می‌شود (0 = خاموش)

var AUTO = {
  BACKUP_PREFIX: 'بکاپ بات نسرا ',
  BACKUP_FOLDER_NAME: 'Nasra Bot Backups — بکاپ‌های بات نسرا',
  BACKUP_HOUR: 3,
  DAILY_HOUR: 10,
  BC_PROP: 'BC_JOB',
  BC_MAX_CHARS: 2500,
  BC_HANDLER: 'processBroadcastQueue',
  RUN_BUDGET_MS: 270000, // ۴.۵ دقیقه (سقف هر اجرا ۶ دقیقه)
  DEFAULTS: { backup_keep: '14', reminder_enabled: '1', reminder_days: '20,27', pending_alert_hours: '24' }
};
function autoSetting_(key) { var v = getSetting_(key); return v === null ? AUTO.DEFAULTS[key] : v; }

// ---------- ابزارهای خالص (در runTests تست می‌شوند) ----------
function parseDaysList_(s) {
  var out = [];
  toEnDigits_(s || '').split(/[,،\s]+/).forEach(function (x) {
    var n = parseInt(x, 10);
    if (n >= 1 && n <= 31 && out.indexOf(n) === -1) out.push(n);
  });
  return out.sort(function (a, b) { return a - b; });
}
// شهرستان‌هایی که در ماه (بر اساس تاریخ برگزاری) هیچ گزارش «در انتظار» یا «تاییدشده» ندارند + کارمندان فعالشان
function reminderTargets_(reports, users, cities, jy, jm) {
  var has = {};
  reports.forEach(function (r) {
    if ((r.status === 'approved' || r.status === 'pending') && num_(r.event_year) === jy && num_(r.event_month) === jm) has[num_(r.city_id)] = 1;
  });
  var out = [], total = 0;
  cities.forEach(function (c) {
    var cid = num_(c.city_id);
    if (has[cid]) return;
    var rec = users.filter(function (u) { return isActiveEmployee_(u) && num_(u.city_id) === cid; })
      .map(function (u) { return num_(u.telegram_id); });
    total += rec.length;
    out.push({ city_id: cid, name: c.name, recipients: rec });
  });
  return { cities: out, totalRecipients: total };
}
// گزارش‌های «در انتظار» که آخرین ارسالشان قبل از cutoff (رشته yyyy-MM-dd HH:mm:ss تهران) است
function overduePending_(reports, cutoffStr) {
  return reports.filter(function (r) {
    return r.status === 'pending' && String(cellToStr_(r.updated_at || r.created_at)) < cutoffStr;
  }).sort(function (a, b) { return num_(a.report_id) - num_(b.report_id); });
}
// گیرندگان پیام همگانی: all = همه کارمندان فعال، city = کارمندان فعال یک شهرستان، admins = ادمین‌های فعال
function broadcastRecipients_(users, admins, aud, cityId) {
  var ids = [];
  function add(x) { x = num_(x); if (x && ids.indexOf(x) === -1) ids.push(x); }
  if (aud === 'admins') admins.forEach(function (a) { if (String(a.is_active) !== '0') add(a.telegram_id); });
  else users.forEach(function (u) {
    if (isActiveEmployee_(u) && (aud === 'all' || (aud === 'city' && num_(u.city_id) === num_(cityId)))) add(u.telegram_id);
  });
  return ids;
}
// ---------- خروجی اکسل واقعی (.xlsx) — بدون کتابخانه؛ فایل‌های XML با Utilities.zip بسته می‌شوند ----------
// CSV در ویندوزِ فارسی/ایرانی همه ستون‌ها را در یک سلول می‌ریزد (جداکننده فهرست آن‌جا «,» نیست)؛ xlsx همه‌جا درست باز می‌شود.
var XLSX_HEADER = ['تاریخ برگزاری', 'ناحیه برگزار کننده', 'موضوع', 'نام سخنران یا مدرس',
  'مدت زمان کلاس (دقیقه)', 'تعداد نفرات', 'مکان برگزاری', 'لینک منتشرشده در کانال'];
var XLSX_WIDTHS = [14, 18, 40, 24, 12, 11, 34, 38];
function xmlEsc_(v) {
  return String(v === null || v === undefined ? '' : v)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function colLetter_(i) { return String.fromCharCode(65 + i); } // ستون‌های A تا H
// لینک پست کانال: کانال عمومی ← t.me/نام‌کاربری/شماره ، کانال خصوصی ← t.me/c/شناسه/شماره (فقط برای اعضای کانال باز می‌شود)
function channelPostLink_(channelId, username, msgId) {
  if (!channelId || !msgId) return '';
  if (username) return 'https://t.me/' + String(username).replace(/^@/, '') + '/' + msgId;
  return 'https://t.me/c/' + String(channelId).replace(/^-100/, '') + '/' + msgId;
}
// ردیف‌های اکسل یک ماه: فقط تاییدشده‌ها، به ترتیب روز برگزاری و بعد شهرستان
function monthExcelRows_(reports, cities, jy, jm, channelId, username) {
  var cname = {};
  cities.forEach(function (c) { cname[num_(c.city_id)] = c.name; });
  return reports.filter(function (r) {
    return r.status === 'approved' && num_(r.event_year) === jy && num_(r.event_month) === jm;
  }).sort(function (a, b) {
    return num_(a.event_day) - num_(b.event_day) ||
      String(cname[num_(a.city_id)] || '').localeCompare(String(cname[num_(b.city_id)] || ''), 'fa') ||
      num_(a.report_id) - num_(b.report_id);
  }).map(function (r) {
    var ids = safeJsonArr_(cellToStr_(r.channel_message_ids));
    return [String(cellToStr_(r.event_jalali)), cname[num_(r.city_id)] || '', String(r.subject || ''),
      String(r.instructor_name || ''), num_(r.duration_min), num_(r.participants),
      String(r.place || '').replace(/\s*[\r\n]+\s*/g, ' - '), channelPostLink_(channelId, username, ids[0])];
  });
}
// همه فایل‌های داخل بسته xlsx (کمینه و استاندارد): یک برگه راست‌به‌چپ، سطر عنوان پررنگ و ثابت، لینک قابل کلیک
function xlsxParts_(sheetName, header, rows, widths) {
  var NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
  var RNS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
  var PR = 'http://schemas.openxmlformats.org/package/2006/relationships';
  var head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n';
  var links = [], sd = [];
  function strCell(ref, v, style) {
    return '<c r="' + ref + '" t="inlineStr"' + (style ? ' s="' + style + '"' : '') + '><is><t xml:space="preserve">' + xmlEsc_(v) + '</t></is></c>';
  }
  sd.push('<row r="1">' + header.map(function (h, i) { return strCell(colLetter_(i) + '1', h, 1); }).join('') + '</row>');
  rows.forEach(function (row, ri) {
    var rn = ri + 2;
    sd.push('<row r="' + rn + '">' + row.map(function (v, ci) {
      var ref = colLetter_(ci) + rn;
      if (typeof v === 'number') return '<c r="' + ref + '"><v>' + v + '</v></c>';
      if (/^https?:\/\//.test(v)) { links.push({ ref: ref, url: v }); return strCell(ref, v, 2); }
      return strCell(ref, v, 0);
    }).join('') + '</row>');
  });
  var cols = '<cols>' + (widths || []).map(function (w, i) {
    return '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + w + '" customWidth="1"/>';
  }).join('') + '</cols>';
  var sheet = head + '<worksheet xmlns="' + NS + '" xmlns:r="' + RNS + '">' +
    '<sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>' +
    '<sheetFormatPr defaultRowHeight="15"/>' + ((widths && widths.length) ? cols : '') +
    '<sheetData>' + sd.join('') + '</sheetData>' +
    (links.length ? '<hyperlinks>' + links.map(function (l, i) { return '<hyperlink ref="' + l.ref + '" r:id="rId' + (i + 1) + '"/>'; }).join('') + '</hyperlinks>' : '') +
    '</worksheet>';
  var sheetRels = head + '<Relationships xmlns="' + PR + '">' + links.map(function (l, i) {
    return '<Relationship Id="rId' + (i + 1) + '" Type="' + RNS + '/hyperlink" Target="' + xmlEsc_(l.url) + '" TargetMode="External"/>';
  }).join('') + '</Relationships>';
  var styles = head + '<styleSheet xmlns="' + NS + '">' +
    '<fonts count="3"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font>' +
    '<font><u/><sz val="11"/><color rgb="FF0563C1"/><name val="Tahoma"/></font></fonts>' +
    '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill></fills>' +
    '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
    '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
    '<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
    '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>' +
    '<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>' +
    '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>';
  var safeName = String(sheetName || 'گزارش‌ها').replace(/[\[\]:*?\/\\]/g, ' ').slice(0, 31);
  return [
    { name: '[Content_Types].xml', content: head + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
      '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' +
      '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>' },
    { name: '_rels/.rels', content: head + '<Relationships xmlns="' + PR + '"><Relationship Id="rId1" Type="' + RNS + '/officeDocument" Target="xl/workbook.xml"/></Relationships>' },
    { name: 'xl/workbook.xml', content: head + '<workbook xmlns="' + NS + '" xmlns:r="' + RNS + '"><bookViews><workbookView/></bookViews>' +
      '<sheets><sheet name="' + xmlEsc_(safeName) + '" sheetId="1" r:id="rId1"/></sheets></workbook>' },
    { name: 'xl/_rels/workbook.xml.rels', content: head + '<Relationships xmlns="' + PR + '">' +
      '<Relationship Id="rId1" Type="' + RNS + '/worksheet" Target="worksheets/sheet1.xml"/>' +
      '<Relationship Id="rId2" Type="' + RNS + '/styles" Target="styles.xml"/></Relationships>' },
    { name: 'xl/styles.xml', content: styles },
    { name: 'xl/worksheets/sheet1.xml', content: sheet },
    { name: 'xl/worksheets/_rels/sheet1.xml.rels', content: sheetRels }
  ];
}

// ---------- ارسال بی‌سروصدا (بدون نوشتن هر خطا در Logs) و ارسال فایل ----------
function tgSendQuiet_(chatId, text, replyMarkup) {
  var token = getProp_('BOT_TOKEN'), p = { chat_id: chatId, text: text };
  if (replyMarkup) p.reply_markup = replyMarkup;
  for (var attempt = 0; attempt < 2; attempt++) {
    bumpFetchCounter_();
    var res, body = {};
    try {
      res = UrlFetchApp.fetch('https://api.telegram.org/bot' + token + '/sendMessage', {
        method: 'post', contentType: 'application/json', payload: JSON.stringify(p), muteHttpExceptions: true
      });
      body = JSON.parse(res.getContentText());
    } catch (e) { Utilities.sleep(1000); continue; }
    if (body.ok) return true;
    if (res.getResponseCode() === 429) { Utilities.sleep(Math.min(num_(body.parameters && body.parameters.retry_after) || 1, 30) * 1000); continue; }
    return false; // 403 = کاربر بات را بلاک کرده یا هرگز استارت نزده
  }
  return false;
}
function tgSendDocument_(chatId, blob, caption) {
  bumpFetchCounter_();
  try {
    var res = UrlFetchApp.fetch('https://api.telegram.org/bot' + getProp_('BOT_TOKEN') + '/sendDocument', {
      method: 'post', payload: { chat_id: String(chatId), caption: caption || '', document: blob }, muteHttpExceptions: true
    });
    var body = JSON.parse(res.getContentText());
    if (!body.ok) log_('error', 'telegram:sendDocument', body.description || res.getResponseCode(), '');
    return !!body.ok;
  } catch (e) { log_('error', 'telegram:sendDocument', e && e.message, ''); return false; }
}
function sendMonthExcel_(chatId, jy, jm) {
  var channelId = getSettingNum_('channel_id'), chat = channelId ? tgGetChat_(channelId) : null;
  var rows = monthExcelRows_(readTable_('Reports'), getCities_(), jy, jm, channelId, chat && chat.username ? chat.username : '');
  var parts = xlsxParts_(JALALI_MONTHS[jm - 1] + ' ' + jy, XLSX_HEADER, rows, XLSX_WIDTHS);
  var fileName = 'nasra-' + jy + '-' + pad2_(jm) + '.xlsx';
  var blob = Utilities.zip(parts.map(function (p) { return Utilities.newBlob(p.content, 'application/xml', p.name); }), fileName);
  blob.setContentType('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  return tgSendDocument_(chatId, blob, '📥 گزارش‌های تاییدشده ' + JALALI_MONTHS[jm - 1] + ' ' + jy + ' — ' + rows.length + ' گزارش\n(فایل Excel؛ روی لینک‌ها بزنید تا پست کانال باز شود)');
}

// ---------- بکاپ ----------
function backupFolder_() {
  var id = getSetting_('backup_folder_id');
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) { } }
  var folder = DriveApp.createFolder(AUTO.BACKUP_FOLDER_NAME);
  setSetting_('backup_folder_id', folder.getId(), 'پوشه بکاپ‌های شبانه در Google Drive — دست نزنید');
  return folder;
}
// یک کپی کامل از فایل شیت در پوشه بکاپ + حذف بکاپ‌های قدیمی‌تر از backup_keep
function runBackup_() {
  var folder = backupFolder_();
  var stamp = todayJalaliStr_().replace(/\//g, '-') + ' ' + Utilities.formatDate(new Date(), 'Asia/Tehran', 'HH-mm');
  var copy = DriveApp.getFileById(ss_().getId()).makeCopy(AUTO.BACKUP_PREFIX + stamp, folder);
  var keep = Math.max(3, num_(autoSetting_('backup_keep')) || 14), files = [], it = folder.getFiles();
  while (it.hasNext()) {
    var f = it.next();
    if (f.getName().indexOf(AUTO.BACKUP_PREFIX) === 0) files.push(f);
  }
  files.sort(function (a, b) { return b.getDateCreated().getTime() - a.getDateCreated().getTime(); });
  var trashed = 0;
  files.slice(keep).forEach(function (f) { try { f.setTrashed(true); trashed++; } catch (e) { } });
  setSetting_('last_backup_at', todayJalaliStr_() + ' ساعت ' + Utilities.formatDate(new Date(), 'Asia/Tehran', 'HH:mm') + ' ✅', 'آخرین بکاپ موفق');
  return { name: copy.getName(), url: copy.getUrl(), folderUrl: folder.getUrl(), kept: Math.min(files.length, keep), trashed: trashed };
}
// تریگر شبانه (ساعت ۳ بامداد تهران)
function nightlyBackup() {
  var lock = LockService.getScriptLock();
  var locked = lock.tryLock(60000);
  try {
    runBackup_();
  } catch (e) {
    log_('error', 'nightlyBackup', e && e.message, '');
    superadminTgs_().forEach(function (tg) {
      tgSend_(tg, '⚠️ بکاپ شبانه ناموفق بود: ' + String(e && e.message || e).slice(0, 200) + '\nدر ویرایشگر Apps Script یک بار installPhase3 را Run کنید.');
    });
  } finally { if (locked) lock.releaseLock(); }
}

// ---------- تریگرها ----------
function ensureAutomationTriggers_() {
  var have = {};
  ScriptApp.getProjectTriggers().forEach(function (t) { have[t.getHandlerFunction()] = true; });
  if (!have.nightlyBackup) ScriptApp.newTrigger('nightlyBackup').timeBased().atHour(AUTO.BACKUP_HOUR).nearMinute(0).everyDays(1).inTimezone('Asia/Tehran').create();
  if (!have.dailyJobs) ScriptApp.newTrigger('dailyJobs').timeBased().atHour(AUTO.DAILY_HOUR).nearMinute(0).everyDays(1).inTimezone('Asia/Tehran').create();
}
function ensureBackupTrigger_() { ensureAutomationTriggers_(); } // نام قدیمی؛ setup() همین را صدا می‌زند
function triggerStatus_() {
  var have = {};
  try { ScriptApp.getProjectTriggers().forEach(function (t) { have[t.getHandlerFunction()] = true; }); } catch (e) { }
  return { backup: !!have.nightlyBackup, daily: !!have.dailyJobs };
}

// نصب فاز ۳ — یک بار در ویرایشگر Run کنید
function installPhase3() {
  Object.keys(AUTO.DEFAULTS).forEach(function (k) {
    if (getSetting_(k) === null) setSetting_(k, AUTO.DEFAULTS[k], {
      backup_keep: 'تعداد بکاپ‌هایی که نگه داشته می‌شود',
      reminder_enabled: 'یادآوری ماهانه به شهرستان‌های بدون گزارش: 1 روشن، 0 خاموش',
      reminder_days: 'روزهای ماه شمسی برای یادآوری، با ویرگول (مثلاً 20,27)',
      pending_alert_hours: 'هشدار گزارش معطل در گروه بررسی بعد از این چند ساعت (0 = خاموش)'
    }[k]);
  });
  ensureAutomationTriggers_();
  var r = runBackup_();
  bustCache_();
  Logger.log('✅ فاز ۳ نصب شد.');
  Logger.log('اولین بکاپ ساخته شد: ' + r.name);
  Logger.log('پوشه بکاپ‌ها در Google Drive: ' + r.folderUrl);
  Logger.log('تریگرها: بکاپ شبانه ساعت ' + AUTO.BACKUP_HOUR + ' بامداد و کارهای روزانه ساعت ' + AUTO.DAILY_HOUR + ' صبح (به وقت تهران).');
  Logger.log('حالا runTests را Run کنید و بعد Deploy ← Manage deployments ← Edit ← New version ← Deploy.');
}

// ---------- کارهای روزانه (تریگر ساعت ۱۰ صبح تهران) ----------
function dailyJobs() {
  var t = todayJalali_(), todayKey = formatJalali_(t.jy, t.jm, t.jd);
  var reports = readTable_('Reports');
  var doReminder = false, doSummary = false, pv = prevJalaliMonth_(t.jy, t.jm), pvKey = pv.jy + '-' + pad2_(pv.jm);
  // نشانه‌گذاری زیر قفل (تا با doPost هم‌زمان در Settings ننویسیم)؛ ارسال‌ها بیرون از قفل
  var lock = LockService.getScriptLock(), locked = lock.tryLock(30000);
  try {
    if (autoSetting_('reminder_enabled') !== '0' && parseDaysList_(autoSetting_('reminder_days')).indexOf(t.jd) !== -1 &&
      getSetting_('last_reminder_jdate') !== todayKey) {
      setSetting_('last_reminder_jdate', todayKey, 'آخرین یادآوری ماهانه — دست نزنید');
      doReminder = true;
    }
    if (t.jd === 1 && getSetting_('last_monthly_summary') !== pvKey) {
      setSetting_('last_monthly_summary', pvKey, 'آخرین خلاصه ماهانه ارسال‌شده — دست نزنید');
      doSummary = true;
    }
  } finally { if (locked) lock.releaseLock(); }

  try { pendingAlert_(reports); } catch (e) { log_('error', 'dailyJobs:pending', e && e.message, ''); }
  if (doReminder) { try { sendMonthlyReminder_(reports, t, false); } catch (e) { log_('error', 'dailyJobs:reminder', e && e.message, ''); } }
  if (doSummary) { try { sendMonthlySummary_(reports, pv.jy, pv.jm); } catch (e) { log_('error', 'dailyJobs:summary', e && e.message, ''); } }
}
function pendingAlert_(reports) {
  var hours = num_(autoSetting_('pending_alert_hours')), groupId = getSettingNum_('review_group_id');
  if (!hours || !groupId) return 0;
  var cutoff = Utilities.formatDate(new Date(Date.now() - hours * 3600000), 'Asia/Tehran', 'yyyy-MM-dd HH:mm:ss');
  var list = overduePending_(reports, cutoff);
  if (!list.length) return 0;
  var lines = ['⏰ یادآوری بررسی: ' + list.length + ' گزارش بیش از ' + hours + ' ساعت در انتظار تایید/رد مانده است:'];
  list.slice(0, 15).forEach(function (r) {
    var c = getCityById_(r.city_id);
    lines.push('• #' + r.report_id + ' — ' + (c ? c.name : '') + ' — ' + cut_(r.subject, 35));
  });
  if (list.length > 15) lines.push('… و ' + (list.length - 15) + ' مورد دیگر');
  lines.push('\nدکمه‌های تایید/رد زیر پیام هر گزارش در همین گروه است؛ فهرست کامل: پنل /admin ← ⏳ در انتظار بررسی');
  tgSend_(groupId, lines.join('\n'));
  return list.length;
}
// previewOnlyTg: اگر عدد باشد، فقط پیش‌نمایش برای همان ادمین فرستاده می‌شود (هیچ پیامی به کارمندان نمی‌رود)
function sendMonthlyReminder_(reports, t, previewOnlyTg) {
  var target = reminderTargets_(reports, readTable_('Users'), getCities_(), t.jy, t.jm);
  var monthName = JALALI_MONTHS[t.jm - 1];
  function textFor(cityName) {
    return '🔔 یادآوری\nهمکار گرامی، تا امروز هیچ گزارشی از شهرستان ' + cityName + ' برای ماه ' + monthName + ' ثبت نشده است.\n' +
      'لطفاً گزارش برنامه‌های این ماه را از دکمه «📝 ثبت گزارش جدید» ارسال کنید 🌿';
  }
  var noStaff = target.cities.filter(function (c) { return !c.recipients.length; }).map(function (c) { return c.name; });
  var withStaff = target.cities.filter(function (c) { return c.recipients.length; });
  var summary = '🔔 یادآوری ماهانه (' + monthName + ' ' + t.jy + ')\n' +
    'شهرستان‌های بدون گزارش: ' + target.cities.length + ' — گیرندگان: ' + target.totalRecipients + ' نفر\n' +
    (withStaff.length ? '• ' + withStaff.map(function (c) { return c.name + ' (' + c.recipients.length + ')'; }).join('، ') + '\n' : '') +
    (noStaff.length ? '⚠️ بدون همکار فعال در بات: ' + noStaff.join('، ') + '\n' : '');
  if (previewOnlyTg) {
    tgSend_(previewOnlyTg, '👁 پیش‌نمایش (هنوز برای کسی ارسال نشده)\n\n' + summary +
      '\nمتن نمونه‌ای که برای هر همکار می‌رود:\n—\n' + textFor(withStaff.length ? withStaff[0].name : '…') + '\n—\n' +
      'ارسال خودکار در روزهای ' + parseDaysList_(autoSetting_('reminder_days')).join(' و ') + ' هر ماه ساعت ' + AUTO.DAILY_HOUR + ' صبح انجام می‌شود.');
    return target;
  }
  if (!target.cities.length) return target;
  var ok = 0, fail = 0;
  withStaff.forEach(function (c) {
    c.recipients.forEach(function (tg) {
      if (tgSendQuiet_(tg, textFor(c.name), mainMenuKb_())) ok++; else fail++;
      Utilities.sleep(40);
    });
  });
  superadminTgs_().forEach(function (tg) {
    tgSend_(tg, summary + 'ارسال موفق: ' + ok + (fail ? ' — ناموفق: ' + fail + ' (بات را بلاک کرده‌اند)' : ''));
  });
  return target;
}
function sendMonthlySummary_(reports, jy, jm) {
  var text = monthStatsText_(reports, jy, jm);
  superadminTgs_().forEach(function (tg) {
    tgSend_(tg, '🗓 خلاصه خودکار ماه گذشته\n\n' + text);
    sendMonthExcel_(tg, jy, jm);
  });
}

// ---------- پیام همگانی (صف + تریگر یک‌باره تا doPost معطل نشود) ----------
function broadcastJob_() { var s = getProp_(AUTO.BC_PROP); if (!s) return null; try { return JSON.parse(s); } catch (e) { return null; } }
function enqueueBroadcast_(byTg, text, ids, label) {
  var cur = broadcastJob_();
  if (cur && cur.pos < cur.ids.length) return { ok: false, msg: 'یک پیام همگانی هنوز در حال ارسال است (' + cur.pos + ' از ' + cur.ids.length + '). بعد از پایانش دوباره تلاش کنید.' };
  if (!ids.length) return { ok: false, msg: 'هیچ گیرنده فعالی پیدا نشد.' };
  var job = { by: num_(byTg), text: String(text).slice(0, AUTO.BC_MAX_CHARS), ids: ids, pos: 0, ok: 0, fail: 0, label: label, at: nowStr_() };
  setProp_(AUTO.BC_PROP, JSON.stringify(job));
  ScriptApp.newTrigger(AUTO.BC_HANDLER).timeBased().after(5000).create();
  return { ok: true, count: ids.length };
}
function processBroadcastQueue() {
  ScriptApp.getProjectTriggers().forEach(function (t) { if (t.getHandlerFunction() === AUTO.BC_HANDLER) ScriptApp.deleteTrigger(t); });
  var job = broadcastJob_();
  if (!job) return;
  var start = Date.now(), msg = '📣 پیام از معاونت نسرا:\n\n' + job.text, sentNow = 0;
  while (job.pos < job.ids.length && (Date.now() - start < AUTO.RUN_BUDGET_MS || sentNow === 0)) { // هر اجرا حداقل یک پیام جلو می‌رود
    sentNow++;
    if (tgSendQuiet_(job.ids[job.pos], msg)) job.ok++; else job.fail++;
    job.pos++;
    if (job.pos % 20 === 0) setProp_(AUTO.BC_PROP, JSON.stringify(job)); // ذخیره پیشرفت
    Utilities.sleep(40);
  }
  if (job.pos < job.ids.length) { // وقت این اجرا تمام شد؛ ادامه در اجرای بعدی
    setProp_(AUTO.BC_PROP, JSON.stringify(job));
    ScriptApp.newTrigger(AUTO.BC_HANDLER).timeBased().after(10000).create();
    return;
  }
  PropertiesService.getScriptProperties().deleteProperty(AUTO.BC_PROP);
  log_('warn', 'broadcast-done', job.label + ': ok ' + job.ok + ', fail ' + job.fail + ' (by ' + job.by + ')', '');
  tgSend_(job.by, '✅ پیام همگانی (' + job.label + ') ارسال شد.\nموفق: ' + job.ok + ' نفر' +
    (job.fail ? '\nناموفق: ' + job.fail + ' نفر (معمولاً کسانی که بات را بلاک کرده یا هرگز استارت نزده‌اند)' : ''));
}
