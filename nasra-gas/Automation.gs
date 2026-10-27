// Automation.gs — فاز ۳: بکاپ شبانه خودکار، کارهای روزانه (یادآوری ماهانه، هشدار گزارش معطل، خلاصه ماه)،
// صف پیام همگانی، و خروجی اکسل (CSV) ماهانه.
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
function csvCell_(v) {
  var s = String(v === null || v === undefined ? '' : cellToStr_(v));
  if (/^[=+\-@]/.test(s)) s = "'" + s; // جلوگیری از فرمول‌شدن در اکسل
  return '"' + s.replace(/"/g, '""') + '"';
}
// فایل CSV گزارش‌های تاییدشده یک ماه (بدون BOM؛ BOM هنگام ساخت فایل اضافه می‌شود)
function monthCsv_(reports, cities, jy, jm) {
  var cname = {};
  cities.forEach(function (c) { cname[num_(c.city_id)] = c.name; });
  var head = ['شماره گزارش', 'شهرستان', 'کارمند', 'نوع کلاس/برنامه', 'نام عملیات', 'موضوع', 'مدرس/سخنران',
    'تاریخ برگزاری', 'ساعت', 'مدت (دقیقه)', 'تعداد نفرات', 'نوع مخاطبین', 'مکان', 'تاریخ درج در کانال', 'دفعات ارسال مجدد'];
  var rows = reports.filter(function (r) {
    return r.status === 'approved' && num_(r.event_year) === jy && num_(r.event_month) === jm;
  }).sort(function (a, b) {
    var ca = String(cname[num_(a.city_id)] || ''), cb = String(cname[num_(b.city_id)] || '');
    return ca.localeCompare(cb, 'fa') || num_(a.event_day) - num_(b.event_day) || num_(a.report_id) - num_(b.report_id);
  }).map(function (r) {
    return [r.report_id, cname[num_(r.city_id)] || '', r.employee_name, r.class_type, r.operation_name, r.subject,
      r.instructor_name, r.event_jalali, r.event_time, r.duration_min, r.participants, r.audience, r.place,
      r.publish_jalali, num_(r.resubmit_count)];
  });
  return [head].concat(rows).map(function (row) { return row.map(csvCell_).join(','); }).join('\r\n');
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
function sendMonthCsv_(chatId, jy, jm) {
  var csv = monthCsv_(readTable_('Reports'), getCities_(), jy, jm);
  var n = csv.split('\r\n').length - 1;
  var blob = Utilities.newBlob('\uFEFF' + csv, 'text/csv', 'nasra-' + jy + '-' + pad2_(jm) + '.csv');
  return tgSendDocument_(chatId, blob, '📥 گزارش‌های تاییدشده ' + JALALI_MONTHS[jm - 1] + ' ' + jy + ' — ' + n + ' گزارش\n(با Excel یا Google Sheets باز کنید)');
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
    sendMonthCsv_(tg, jy, jm);
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
