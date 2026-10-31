// Automation.gs — فاز ۳: بکاپ شبانه خودکار، کارهای روزانه (یادآوری ماهانه، هشدار گزارش معطل، خلاصه ماه)،
// صف پیام همگانی، و خروجی اکسل واقعی (.xlsx) ماهانه.
// فاز ۴: دوره گزارش ماهانه ۲۱ تا ۲۰، خواهر/برادر از روی نقش، اکسل پنج‌برگه‌ای مثل فایل نمونه (نصب: installPhase4).
// نصب یک‌باره: در ویرایشگر تابع installPhase3 را Run کنید (اجازه دسترسی به Google Drive برای بکاپ لازم است).
// تنظیمات قابل تغییر در برگه Settings (اگر نباشند، مقدار پیش‌فرض استفاده می‌شود):
//   backup_keep = 14            تعداد بکاپ‌هایی که نگه داشته می‌شود (قدیمی‌ترها به سطل زباله Drive می‌روند)
//   reminder_enabled = 1        یادآوری ماهانه روشن (0 = خاموش)
//   reminder_days = 15,18       روزهای ماه شمسی که یادآوری به شهرستان‌های بدون گزارش فرستاده می‌شود (دوره هر ماه روز ۲۰ تمام می‌شود)
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
  DEFAULTS: { backup_keep: '14', reminder_enabled: '1', reminder_days: '15,18', pending_alert_hours: '24' }
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
// شهرستان‌هایی که در دوره گزارش (۲۱ ماه قبل تا ۲۰ این ماه، بر اساس تاریخ برگزاری) هیچ گزارش «در انتظار» یا «تاییدشده» ندارند + کارمندان فعالشان
function reminderTargets_(reports, users, cities, jy, jm) {
  var has = {};
  reports.forEach(function (r) {
    if ((r.status === 'approved' || r.status === 'pending') && inPeriod_(r, jy, jm)) has[num_(r.city_id)] = 1;
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
// ---------- دوره گزارش ماهانه — قانون کلی: «گزارش آبان» = برنامه‌هایی که تاریخ برگزاری‌شان ۲۱ مهر تا ۲۰ آبان است ----------
// (روز ۲۰ هر ماه آخرین روز همان ماه است و از روز ۲۱، برنامه‌ها جزو گزارش ماه بعد حساب می‌شوند؛ مثل فایل نمونه شهریور که از 1405/05/21 شروع می‌شود)
var PERIOD_END_DAY = 20;
function reportPeriodOf_(jy, jm, jd) { // تاریخ برگزاری ← گزارش کدام ماه
  jy = num_(jy); jm = num_(jm); jd = num_(jd);
  if (!jy || jm < 1 || jm > 12 || !jd) return null;
  return jd > PERIOD_END_DAY ? nextJalaliMonth_(jy, jm) : { jy: jy, jm: jm };
}
function reportPeriodOfRow_(r) {
  var y = num_(r.event_year), m = num_(r.event_month), d = num_(r.event_day);
  if (!(y && m && d)) {
    var p = parseJalaliInput_(String(cellToStr_(r.event_jalali) || ''));
    if (p) { y = p.jy; m = p.jm; d = p.jd; }
  }
  return reportPeriodOf_(y, m, d);
}
function inPeriod_(r, jy, jm) { var p = reportPeriodOfRow_(r); return !!p && p.jy === num_(jy) && p.jm === num_(jm); }
function periodRange_(jy, jm) {
  var pv = prevJalaliMonth_(jy, jm);
  return { from: formatJalali_(pv.jy, pv.jm, PERIOD_END_DAY + 1), to: formatJalali_(jy, jm, PERIOD_END_DAY) };
}
function periodName_(jy, jm) { return JALALI_MONTHS[jm - 1] + ' ' + jy; }
function periodRangeText_(jy, jm) { var r = periodRange_(jy, jm); return 'از ' + r.from + ' تا ' + r.to; }
function currentPeriod_() { var t = todayJalali_(); return reportPeriodOf_(t.jy, t.jm, t.jd); }

// ---------- خواهر / برادر از روی نقش فرستنده ----------
function positionsByTg_(users) {
  var m = {};
  users.forEach(function (u) { m[num_(u.telegram_id)] = u.position || ''; });
  return m;
}
// نقشِ ثبت‌شده در خود گزارش (لحظه ارسال)؛ برای گزارش‌های قدیمی‌تر، نقش فعلی فرستنده
function reportSide_(r, posByTg) {
  return positionSide_(positionOf_(r.submitter_position) || (posByTg ? posByTg[num_(r.submitter_telegram_id)] : ''));
}
// تاییدشده‌های یک دوره — cityId=0 یعنی همه شهرستان‌ها؛ side: 'a' تجمیعی (همه)، 's' فقط خواهران، 'b' فقط برادران
function excelPick_(reports, posByTg, jy, jm, cityId, side) {
  return reports.filter(function (r) {
    if (r.status !== 'approved' || !inPeriod_(r, jy, jm)) return false;
    if (num_(cityId) && num_(r.city_id) !== num_(cityId)) return false;
    return (side === 's' || side === 'b') ? reportSide_(r, posByTg) === side : true;
  });
}
function sideCounts_(reports, posByTg, jy, jm, cityId) {
  var c = { s: 0, b: 0, v: 0, none: 0, all: 0 };
  excelPick_(reports, posByTg, jy, jm, cityId, 'a').forEach(function (r) {
    var sd = reportSide_(r, posByTg);
    c[sd || 'none']++; c.all++;
  });
  return c;
}

// ---------- خروجی اکسل واقعی (.xlsx) — بدون کتابخانه؛ فایل‌های XML با Utilities.zip بسته می‌شوند ----------
// CSV در ویندوزِ فارسی همه ستون‌ها را در یک سلول می‌ریزد؛ xlsx همه‌جا درست باز می‌شود.
// پنج برگه دقیقاً مثل فایل نمونه؛ هر گزارش بر اساس هشتگ «نوع کلاس/برنامه» در برگه خودش می‌رود.
// هر ستون: [عنوان، عرض، فیلد]. فیلد خالی = این اطلاعات در فرم بات پرسیده نمی‌شود و ستون برای پرکردن دستی خالی می‌ماند.
// sub = زیرشاخه (حضوری/مجازی توانمندسازی، نوع محتوای تولیدات) ؛ cplace = بخش خلاقانه + بستر انتشار.
var XL_SHEETS = [
  { type: 'حضوری', name: 'سوادرسانه حضوری', cols: [['ردیف', 7, 'n'], ['تاریخ', 13, 'date'], ['نام ناحیه', 18, 'city'],
    ['موضوع', 34, 'subject'], ['نام سخنران / مدرس', 22, 'teacher'], ['مدت (دقیقه)', 12, 'dur'], ['تعداد نفرات', 12, 'ppl'],
    ['مکان برگزاری', 30, 'place'], ['لینک مستندات', 34, 'link']] },
  { type: 'مجازی', name: 'سوادرسانه مجازی (لایو)', cols: [['ردیف', 7, 'n'], ['تاریخ', 13, 'date'], ['نام ناحیه', 18, 'city'],
    ['موضوع', 34, 'subject'], ['نام سخنران / مدرس', 22, 'teacher'], ['مدت (دقیقه)', 12, 'dur'], ['تعداد نفرات', 12, 'ppl'],
    ['بستر برگزاری', 30, 'place'], ['لینک مستندات', 34, 'link']] },
  { type: 'توانمندسازی', name: 'توانمندسازی گردان', cols: [['ردیف', 7, 'n'], ['تاریخ', 13, 'date'], ['برگزار کننده', 18, 'city'],
    ['موضوع', 34, 'subject'], ['نام سخنران / مدرس', 22, 'teacher'], ['مدت (دقیقه)', 12, 'dur'], ['تعداد نفرات', 12, 'ppl'],
    ['مکان برگزاری', 30, 'place'], ['لینک مستندات', 34, 'link'], ['نوع کلاس', 16, 'sub']] },
  { type: 'خلاقانه', name: 'سواد رسانه خلاقانه', cols: [['ردیف', 7, 'n'], ['تاریخ', 13, 'date'], ['ناحیه برگزار کننده', 18, 'city'],
    ['موضوع', 34, 'subject'], ['تعداد نفرات / تعداد بازدید', 18, 'ppl'], ['بستر برگزاری', 30, 'cplace'], ['لینک مستندات', 34, 'link']] },
  { type: 'تولیدات', name: 'تولیدات', cols: [['ردیف', 7, 'n'], ['تاریخ محتوای تولید شده', 16, 'date'], ['تولید کننده', 18, 'city'],
    ['موضوع', 34, 'subject'], ['نام تولید کننده محتوا', 22, 'teacher'], ['نوع تولید محتوا', 16, 'sub'], ['تعداد صفحات', 12, ''],
    ['لینک مستندات', 34, 'link']] }
];
var EXCEL_SIDE_FA = { a: 'تجمیعی', s: 'خواهران', b: 'برادران' };
function xmlEsc_(v) {
  return String(v === null || v === undefined ? '' : v)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function colLetter_(i) { return String.fromCharCode(65 + i); } // ستون‌های A تا J
// لینک پست کانال: کانال عمومی ← t.me/نام‌کاربری/شماره ، کانال خصوصی ← t.me/c/شناسه/شماره (فقط برای اعضای کانال باز می‌شود)
function channelPostLink_(channelId, username, msgId) {
  if (!channelId || !msgId) return '';
  if (username) return 'https://t.me/' + String(username).replace(/^@/, '') + '/' + msgId;
  return 'https://t.me/c/' + String(channelId).replace(/^-100/, '') + '/' + msgId;
}
// گزارش‌ها ← پنج برگه (به ترتیب تاریخ برگزاری، بعد شهرستان، بعد شماره گزارش)
function excelSheets_(list, cities, channelId, username) {
  var cname = {};
  cities.forEach(function (c) { cname[num_(c.city_id)] = c.name; });
  function dateOf(r) {
    var p = parseJalaliInput_(String(cellToStr_(r.event_jalali) || ''));
    if (!p && num_(r.event_year)) p = { jy: num_(r.event_year), jm: num_(r.event_month), jd: num_(r.event_day) };
    return p ? formatJalali_(p.jy, p.jm, p.jd) : String(cellToStr_(r.event_jalali) || '');
  }
  var sorted = list.slice().sort(function (a, b) {
    return (dateOf(a) < dateOf(b) ? -1 : dateOf(a) > dateOf(b) ? 1 : 0) ||
      String(cname[num_(a.city_id)] || '').localeCompare(String(cname[num_(b.city_id)] || ''), 'fa') ||
      num_(a.report_id) - num_(b.report_id);
  });
  return XL_SHEETS.map(function (def) {
    var rows = sorted.filter(function (r) { return String(r.class_type || '').replace(/^#/, '') === def.type; }).map(function (r, i) {
      var ids = safeJsonArr_(cellToStr_(r.channel_message_ids));
      var x = {
        n: i + 1, date: dateOf(r), city: cname[num_(r.city_id)] || '', subject: String(r.subject || ''),
        teacher: String(r.instructor_name || ''), dur: num_(r.duration_min) || '', ppl: num_(r.participants) || '',
        place: String(r.place || '').replace(/\s*[\r\n]+\s*/g, ' - '), link: channelPostLink_(channelId, username, ids[0]),
        sub: subLabel_(r.class_sub) // حضوری/مجازیِ توانمندسازی ، نوع محتوای تولیدات
      };
      x.cplace = x.sub ? x.sub + (x.place ? ' — ' + x.place : '') : x.place; // خلاقانه: «چاپ بنر — میدان امام»
      return def.cols.map(function (c) { return c[2] ? x[c[2]] : ''; });
    });
    return { type: def.type, name: def.name, cols: def.cols, rows: rows };
  });
}
// همه فایل‌های داخل بسته xlsx: چند برگه راست‌به‌چپ، سطر عنوان ثابت، ظاهر مثل فایل نمونه (قلم B Titr، عنوان طلایی، کادر نازک)، لینک قابل کلیک
function xlsxBook_(sheets) {
  var NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
  var RNS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
  var PR = 'http://schemas.openxmlformats.org/package/2006/relationships';
  var CT = 'application/vnd.openxmlformats-officedocument.spreadsheetml.';
  var head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n';
  function strCell(ref, v, style) {
    return '<c r="' + ref + '" s="' + style + '" t="inlineStr"><is><t xml:space="preserve">' + xmlEsc_(v) + '</t></is></c>';
  }
  var parts = [], overrides = [], wbSheets = [], wbRels = [], used = {};
  sheets.forEach(function (sh, si) {
    var n = si + 1, links = [], sd = [];
    sd.push('<row r="1" ht="28.5" customHeight="1">' + sh.cols.map(function (c, i) { return strCell(colLetter_(i) + '1', c[0], 1); }).join('') + '</row>');
    sh.rows.forEach(function (row, ri) {
      var rn = ri + 2;
      sd.push('<row r="' + rn + '">' + row.map(function (v, ci) {
        var ref = colLetter_(ci) + rn;
        if (v === '' || v === null || v === undefined) return '<c r="' + ref + '" s="2"/>';
        if (typeof v === 'number') return '<c r="' + ref + '" s="2"><v>' + v + '</v></c>';
        if (/^https?:\/\//.test(v)) { links.push({ ref: ref, url: v }); return strCell(ref, v, 3); }
        return strCell(ref, v, 2);
      }).join('') + '</row>');
    });
    var cols = '<cols>' + sh.cols.map(function (c, i) { return '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + c[1] + '" customWidth="1"/>'; }).join('') + '</cols>';
    parts.push({ name: 'xl/worksheets/sheet' + n + '.xml', content: head + '<worksheet xmlns="' + NS + '" xmlns:r="' + RNS + '">' +
      '<sheetViews><sheetView rightToLeft="1"' + (n === 1 ? ' tabSelected="1"' : '') + ' workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>' +
      '<sheetFormatPr defaultRowHeight="20.25"/>' + cols + '<sheetData>' + sd.join('') + '</sheetData>' +
      (links.length ? '<hyperlinks>' + links.map(function (l, i) { return '<hyperlink ref="' + l.ref + '" r:id="rId' + (i + 1) + '"/>'; }).join('') + '</hyperlinks>' : '') +
      '</worksheet>' });
    parts.push({ name: 'xl/worksheets/_rels/sheet' + n + '.xml.rels', content: head + '<Relationships xmlns="' + PR + '">' + links.map(function (l, i) {
      return '<Relationship Id="rId' + (i + 1) + '" Type="' + RNS + '/hyperlink" Target="' + xmlEsc_(l.url) + '" TargetMode="External"/>';
    }).join('') + '</Relationships>' });
    overrides.push('<Override PartName="/xl/worksheets/sheet' + n + '.xml" ContentType="' + CT + 'worksheet+xml"/>');
    var nm = String(sh.name || ('برگه ' + n)).replace(/[\[\]:*?\/\\]/g, ' ').trim().slice(0, 31);
    while (used[nm]) nm = nm.slice(0, 28) + ' ' + n; // نام برگه‌ها باید یکتا باشد
    used[nm] = 1;
    wbSheets.push('<sheet name="' + xmlEsc_(nm) + '" sheetId="' + n + '" r:id="rId' + n + '"/>');
    wbRels.push('<Relationship Id="rId' + n + '" Type="' + RNS + '/worksheet" Target="worksheets/sheet' + n + '.xml"/>');
  });
  var border = '<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>';
  var styles = head + '<styleSheet xmlns="' + NS + '">' +
    '<fonts count="4"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font>' +
    '<font><sz val="14"/><name val="B Titr"/><charset val="178"/></font>' +
    '<font><sz val="10"/><name val="B Titr"/><charset val="178"/></font>' +
    '<font><u/><sz val="11"/><color rgb="FF0563C1"/><name val="Calibri"/><family val="2"/></font></fonts>' +
    '<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFFFD966"/><bgColor indexed="64"/></patternFill></fill>' +
    '<fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/><bgColor indexed="64"/></patternFill></fill></fills>' +
    '<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>' + border + '</borders>' +
    '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
    '<cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
    '<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1" readingOrder="2"/></xf>' +
    '<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1" readingOrder="2"/></xf>' +
    '<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf></cellXfs>' +
    '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>';
  var stylesId = sheets.length + 1;
  return [
    { name: '[Content_Types].xml', content: head + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/xl/workbook.xml" ContentType="' + CT + 'sheet.main+xml"/>' + overrides.join('') +
      '<Override PartName="/xl/styles.xml" ContentType="' + CT + 'styles+xml"/></Types>' },
    { name: '_rels/.rels', content: head + '<Relationships xmlns="' + PR + '"><Relationship Id="rId1" Type="' + RNS + '/officeDocument" Target="xl/workbook.xml"/></Relationships>' },
    { name: 'xl/workbook.xml', content: head + '<workbook xmlns="' + NS + '" xmlns:r="' + RNS + '"><bookViews><workbookView activeTab="0"/></bookViews>' +
      '<sheets>' + wbSheets.join('') + '</sheets></workbook>' },
    { name: 'xl/_rels/workbook.xml.rels', content: head + '<Relationships xmlns="' + PR + '">' + wbRels.join('') +
      '<Relationship Id="rId' + stylesId + '" Type="' + RNS + '/styles" Target="styles.xml"/></Relationships>' },
    { name: 'xl/styles.xml', content: styles }
  ].concat(parts);
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
function safeFileName_(s) { return String(s).replace(/[\\\/:*?"<>|\r\n]+/g, ' ').replace(/\s+/g, ' ').trim(); }
// فایل اکسل یک دوره — cityId=0 همه شهرستان‌ها (همیشه تجمیعی)؛ side: 'a' تجمیعی، 's' خواهران، 'b' برادران
function sendExcel_(chatId, jy, jm, cityId, side) {
  cityId = num_(cityId);
  side = (cityId && (side === 's' || side === 'b')) ? side : 'a';
  var channelId = getSettingNum_('channel_id'), chat = channelId ? tgGetChat_(channelId) : null;
  var reports = readTable_('Reports'), posByTg = positionsByTg_(readTable_('Users'));
  var city = cityId ? getCityById_(cityId) : null;
  var list = excelPick_(reports, posByTg, jy, jm, cityId, side);
  var sheets = excelSheets_(list, cachedTable_('cities', 'Cities'), channelId, chat && chat.username ? chat.username : '');
  var scope = cityId ? (city ? city.name : 'شهرستان ' + cityId) + ' - ' + EXCEL_SIDE_FA[side] : 'همه شهرستان‌ها';
  var fileName = safeFileName_('گزارش ' + periodName_(jy, jm) + ' - ' + scope) + '.xlsx';
  var parts = xlsxBook_(sheets);
  var blob = Utilities.zip(parts.map(function (p) { return Utilities.newBlob(p.content, 'application/xml', p.name); }), fileName);
  blob.setContentType('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  var placed = 0, per = sheets.map(function (sh) { placed += sh.rows.length; return sh.type + ' ' + sh.rows.length; });
  var note = '';
  if (cityId) {
    var sc = sideCounts_(reports, posByTg, jy, jm, cityId), other = sc.v + sc.none;
    if (side === 'a' && other) note = '\nℹ️ شامل ' + (sc.v ? sc.v + ' گزارش مسئول فضای مجازی' : '') + (sc.v && sc.none ? ' و ' : '') + (sc.none ? sc.none + ' گزارش همکار بدون نقش' : '');
    if (side !== 'a' && other) note = '\nℹ️ ' + other + ' گزارش این شهرستان (مسئول فضای مجازی یا همکار بدون نقش) فقط در فایل «تجمیعی» است.';
  }
  if (placed < list.length) note += '\n⚠️ ' + (list.length - placed) + ' گزارش نوع کلاس نامعتبر داشت و در هیچ برگه‌ای نیامد.';
  return tgSendDocument_(chatId, blob, '📥 گزارش ' + periodName_(jy, jm) + ' — ' + scope +
    '\n🗓 برنامه‌های ' + periodRangeText_(jy, jm) + ' (بر اساس تاریخ برگزاری)' +
    '\nجمع: ' + list.length + ' گزارش تاییدشده — ' + per.join('، ') + note +
    '\n(فایل Excel با ۵ برگه؛ روی لینک‌ها بزنید تا پست کانال باز شود)');
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
      reminder_days: 'روزهای ماه شمسی برای یادآوری، با ویرگول؛ دوره هر ماه روز ۲۰ تمام می‌شود (مثلاً 15,18)',
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

// نصب فاز ۴ (دوره ۲۱ تا ۲۰، نقش‌ها، اکسل پنج‌برگه‌ای) — یک بار در ویرایشگر Run کنید؛ تکرارش بی‌خطر است.
function installPhase4() {
  ensureSheets_(); // ستون‌های تازه: Users.position (با فهرست کشویی نقش‌ها) و Reports.submitter_position
  if (String(getSetting_('reminder_days') || '').replace(/\s/g, '') === '20,27') { // پیش‌فرض قدیمی با دوره ۲۱ تا ۲۰ جور نیست
    setSetting_('reminder_days', AUTO.DEFAULTS.reminder_days, 'روزهای ماه شمسی برای یادآوری، با ویرگول؛ دوره هر ماه روز ۲۰ تمام می‌شود (مثلاً 15,18)');
    Logger.log('روزهای یادآوری از 20,27 به ' + AUTO.DEFAULTS.reminder_days + ' تغییر کرد (چون دوره گزارش هر ماه روز ۲۰ تمام می‌شود).');
  }
  bustCache_();
  var active = readTable_('Users').filter(isActiveEmployee_);
  var noPos = active.filter(function (u) { return !positionOf_(u.position); }).length;
  var cur = currentPeriod_();
  Logger.log('✅ فاز ۴ نصب شد.');
  Logger.log('دوره فعلی: گزارش ' + periodName_(cur.jy, cur.jm) + ' — برنامه‌های ' + periodRangeText_(cur.jy, cur.jm));
  Logger.log('ستون‌های class_sub ، link و form_v (کپشن جداگانه هر نوع) به برگه Reports اضافه شد.');
  Logger.log('برگه Samples (نمونه گزارش‌ها) آماده است؛ نمونه‌ها را از پنل /admin ← 📚 نمونه گزارش‌ها ثبت کنید.');
  Logger.log('همکاران فعال: ' + active.length + ' — بدون نقش: ' + noPos + ' (نقش را در برگه Users، ستون position از فهرست کشویی بگذارید، یا در پنل /admin ← کاربران؛ وگرنه بات موقع اولین گزارش از خودشان می‌پرسد)');
  Logger.log('حالا runTests را Run کنید و بعد Deploy ← Manage deployments ← Edit ← New version ← Deploy.');
}

// ---------- کارهای روزانه (تریگر ساعت ۱۰ صبح تهران) ----------
function dailyJobs() {
  var t = todayJalali_(), todayKey = formatJalali_(t.jy, t.jm, t.jd);
  var reports = readTable_('Reports');
  // خلاصه دوره: از روز ۲۱ (اولین اجرای بعد از پایان دوره ۲۱ تا ۲۰) برای همان ماه
  var doReminder = false, doSummary = false, pv = { jy: t.jy, jm: t.jm }, pvKey = pv.jy + '-' + pad2_(pv.jm);
  // نشانه‌گذاری زیر قفل (تا با doPost هم‌زمان در Settings ننویسیم)؛ ارسال‌ها بیرون از قفل
  var lock = LockService.getScriptLock(), locked = lock.tryLock(30000);
  try {
    if (autoSetting_('reminder_enabled') !== '0' && parseDaysList_(autoSetting_('reminder_days')).indexOf(t.jd) !== -1 &&
      getSetting_('last_reminder_jdate') !== todayKey) {
      setSetting_('last_reminder_jdate', todayKey, 'آخرین یادآوری ماهانه — دست نزنید');
      doReminder = true;
    }
    if (t.jd > PERIOD_END_DAY && getSetting_('last_monthly_summary') !== pvKey) {
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
  var per = reportPeriodOf_(t.jy, t.jm, t.jd);
  var target = reminderTargets_(reports, readTable_('Users'), getCities_(), per.jy, per.jm);
  var monthName = JALALI_MONTHS[per.jm - 1], range = periodRangeText_(per.jy, per.jm);
  function textFor(cityName) {
    return '🔔 یادآوری\nهمکار گرامی، تا امروز هیچ گزارشی از شهرستان ' + cityName + ' برای «گزارش ' + monthName + '» (برنامه‌های ' + range + ') ثبت نشده است.\n' +
      'لطفاً گزارش برنامه‌های این دوره را از دکمه «' + MENU.NEW_REPORT + '» بفرستید 🌿';
  }
  var noStaff = target.cities.filter(function (c) { return !c.recipients.length; }).map(function (c) { return c.name; });
  var withStaff = target.cities.filter(function (c) { return c.recipients.length; });
  var summary = '🔔 یادآوری ماهانه (گزارش ' + monthName + ' ' + per.jy + ' — ' + range + ')\n' +
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
    tgSend_(tg, '🗓 خلاصه خودکار — دوره گزارش ' + periodName_(jy, jm) + ' تمام شد\n\n' + text);
    sendExcel_(tg, jy, jm, 0, 'a');
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
    if (tgSendQuiet_(job.ids[job.pos], msg, mainMenuKb_())) job.ok++; else job.fail++; // منوی تازه هم همراه پیام می‌رسد
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
