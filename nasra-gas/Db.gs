// Db.gs — لایه داده روی برگه‌های Google Sheets + CacheService + Sessions + لاگ و حذف تکراری آپدیت
// قانون: همه کوئری‌های کارمند (مدرس‌ها، گزارش‌های من، اصلاحیه) با city_id خودش فیلتر می‌شوند.
// همه ستون‌ها در setup با فرمت «متن ساده» تنظیم می‌شوند؛ ساعت و تاریخ و شماره رشته می‌مانند.

function ss_() {
  var id = getProp_('SPREADSHEET_ID');
  if (id) return SpreadsheetApp.openById(id);
  return SpreadsheetApp.getActiveSpreadsheet();
}
function sheet_(name) { return ss_().getSheetByName(name); }
function nowStr_() { return Utilities.formatDate(new Date(), 'Asia/Tehran', 'yyyy-MM-dd HH:mm:ss'); }
function cache_() { return CacheService.getScriptCache(); }
function num_(v) { var n = Number(v); return isNaN(n) ? 0 : n; }
function cellToStr_(v) { // اگر Sheets مقداری را Date کرده بود، رشته‌اش کن
  if (v instanceof Date) return Utilities.formatDate(v, 'Asia/Tehran', 'yyyy-MM-dd HH:mm:ss');
  return v;
}
function escCell_(v) {
  // گوگل‌شیت هر رشته‌ای که با '=' شروع شود «فرمول» تفسیر می‌کند (حتی با فرمت '@')؛ پیشوند "'" یعنی «متن حسابش کن»
  // و خود آپاستروف جزو مقدار نیست — هنگام خواندن دقیقاً همان رشتهٔ اصلی برمی‌گردد.
  return (typeof v === 'string' && v.charAt(0) === '=') ? "'" + v : v;
}

// خواندن یک جدول کامل به‌صورت آرایه‌ای از آبجکت‌ها (با شماره ردیف در _row)
function readTable_(name) {
  var sh = sheet_(name);
  if (!sh || sh.getLastRow() < 1) return [];
  var values = sh.getDataRange().getValues();
  var headers = SHEETS[name];
  var out = [];
  for (var r = 1; r < values.length; r++) {
    var obj = { _row: r + 1 }, empty = true;
    for (var c = 0; c < headers.length; c++) {
      var v = cellToStr_(values[r][c]);
      obj[headers[c]] = v;
      if (v !== '' && v !== null && v !== undefined) empty = false;
    }
    if (!empty) out.push(obj);
  }
  return out;
}
function appendRow_(name, obj) {
  var headers = SHEETS[name];
  var row = headers.map(function (h) { var v = obj[h]; return v === undefined || v === null ? '' : escCell_(v instanceof Date ? cellToStr_(v) : v); });
  // مهم: از appendRow خودِ Sheets استفاده نمی‌کنیم؛ آن متد حتی روی ستون «متن ساده» هم 21:00 و
  // 1405/07/23 و 0912… را ساعت/تاریخ/عدد تفسیر می‌کند. روی دقیقاً ردیف مقصد فرمت '@' می‌گذاریم
  // و با setValues می‌نویسیم تا رشته‌ها همیشه رشته بمانند.
  var sh = sheet_(name);
  var target = Math.max(sh.getLastRow() + 1, 2); // ردیف ۱ همیشه هدر است
  var range = sh.getRange(target, 1, 1, headers.length);
  range.setNumberFormat('@');
  range.setValues([row]);
  return target;
}
function updateRow_(name, rowNum, partial) {
  var sh = sheet_(name), headers = SHEETS[name];
  var range = sh.getRange(rowNum, 1, 1, headers.length);
  var cur = range.getValues()[0];
  for (var c = 0; c < headers.length; c++) {
    if (partial[headers[c]] !== undefined) {
      var pv = partial[headers[c]];
      cur[c] = (pv instanceof Date) ? cellToStr_(pv) : pv;
    }
  }
  cur = cur.map(function (v) { return escCell_(v); }); // متن‌های شروع‌شده با '=' فرمول نشوند (هم مقادیر جدید، هم خوانده‌شده‌ها)
  range.setNumberFormat('@'); // همان قفل متنِ appendRow_ — قبل از نوشتن، تا رشته‌ها تفسیر نشوند
  range.setValues([cur]); // نوشتن یکجا (batch)
}
function findOne_(name, pred) {
  var rows = readTable_(name);
  for (var i = 0; i < rows.length; i++) if (pred(rows[i])) return rows[i];
  return null;
}
function bustCache_() {
  try { cache_().removeAll(['cities', 'instructors', 'admins', 'settingsMap', 'whitelist']); } catch (e) { }
}
function cachedTable_(key, name) {
  var c = cache_().get(key);
  if (c) { try { return JSON.parse(c); } catch (e) { } }
  var rows = readTable_(name);
  try { cache_().put(key, JSON.stringify(rows), CFG.CACHE_SECONDS); } catch (e) { } // سقف هر مقدار کش ۱۰۰KB است
  return rows;
}
function getCities_() { return cachedTable_('cities', 'Cities').filter(function (r) { return String(r.is_active) !== '0' && r.is_active !== false; }); }
function getCityById_(cityId) {
  var cs = getCities_();
  for (var i = 0; i < cs.length; i++) if (num_(cs[i].city_id) === num_(cityId)) return cs[i];
  return null;
}
function getAdmins_() { return cachedTable_('admins', 'Admins'); }
function isAdminTg_(tg) {
  return getAdmins_().some(function (a) { return num_(a.telegram_id) === num_(tg) && String(a.is_active) !== '0' && a.is_active !== false; });
}
function isSuperTg_(tg) {
  return getAdmins_().some(function (a) { return num_(a.telegram_id) === num_(tg) && a.role === 'superadmin' && String(a.is_active) !== '0'; });
}
function superadminTgs_() {
  return getAdmins_().filter(function (a) { return a.role === 'superadmin' && String(a.is_active) !== '0'; })
    .map(function (a) { return num_(a.telegram_id); });
}
function hasSuperadmin_() { return superadminTgs_().length > 0; }

// ---------- Settings ----------
function getSettingsMap_() {
  var c = cache_().get('settingsMap');
  if (c) { try { return JSON.parse(c); } catch (e) { } }
  var map = {};
  readTable_('Settings').forEach(function (r) { map[String(r.key)] = String(cellToStr_(r.value)); });
  try { cache_().put('settingsMap', JSON.stringify(map), CFG.CACHE_SECONDS); } catch (e) { }
  return map;
}
function getSetting_(key) { var v = getSettingsMap_()[key]; return v === undefined || v === '' ? null : v; }
function getSettingNum_(key) { var v = getSetting_(key); return v === null ? null : num_(v); }
function setSetting_(key, value, note) {
  var row = findOne_('Settings', function (r) { return String(r.key) === key; });
  if (row) updateRow_('Settings', row._row, { value: String(value), note: note || row.note || '' });
  else appendRow_('Settings', { key: key, value: String(value), note: note || '' });
  try { cache_().remove('settingsMap'); } catch (e) { }
}
function deleteSetting_(key) {
  var row = findOne_('Settings', function (r) { return String(r.key) === key; });
  if (row) updateRow_('Settings', row._row, { value: '' });
  try { cache_().remove('settingsMap'); } catch (e) { }
}
function nextId_(counterKey) { // doPost زیر LockService صدا می‌زند؛ پس اتمیک است
  var cur = getSettingNum_(counterKey) || 0;
  setSetting_(counterKey, cur + 1, 'شمارنده داخلی بات — دست نزنید');
  return cur + 1;
}

// ---------- Users ----------
function getUserByTg_(tg) {
  var key = 'user_' + tg, c = cache_().get(key);
  if (c) { try { return JSON.parse(c); } catch (e) { } }
  var u = findOne_('Users', function (r) { return num_(r.telegram_id) === num_(tg); });
  if (u) { try { cache_().put(key, JSON.stringify(u), CFG.CACHE_SECONDS); } catch (e) { } }
  return u;
}
function getUserById_(id) { return findOne_('Users', function (r) { return num_(r.user_id) === num_(id); }); }
function getUserByPhone_(phone) { // مقایسه با فرم نرمال تا اختلاف +98/صفر/ارقام فارسی مهم نباشد
  var target = normalizePhone_(phone);
  if (!target) return null;
  return findOne_('Users', function (r) { return normalizePhone_(r.phone) === target; });
}
function ensureUser_(tg, fullName) {
  var u = getUserByTg_(tg);
  if (u) return u;
  var id = nextId_('user_counter');
  appendRow_('Users', {
    user_id: id, telegram_id: tg, phone: '', full_name: fullName || '', city_id: '',
    role: 'employee', status: 'pending', reg_group_message_id: '', created_at: nowStr_(), updated_at: nowStr_()
  });
  try { cache_().remove('user_' + tg); } catch (e) { }
  return getUserByTg_(tg);
}
function updateUser_(userId, tg, partial) {
  var u = getUserById_(userId);
  if (!u) return;
  partial.updated_at = nowStr_();
  updateRow_('Users', u._row, partial);
  try { cache_().remove('user_' + tg); } catch (e) { }
}
function isActiveEmployee_(u) { return !!(u && u.status === 'approved' && num_(u.city_id) > 0); }

// ---------- Whitelist (لیست مجاز) ----------
function resolveWhitelistRow_(row, cities) {
  var cid = num_(row.city_id);
  if (cid > 0) return cid;
  var target = normCmp_(row.city_name);
  for (var i = 0; i < cities.length; i++) if (normCmp_(cities[i].name) === target) return num_(cities[i].city_id);
  return 0;
}
function matchWhitelistRows_(rows, phone, cities) {
  for (var i = 0; i < rows.length; i++) {
    if (normalizePhone_(rows[i].phone) === phone) {
      var cid = resolveWhitelistRow_(rows[i], cities);
      return cid > 0 ? { phone: phone, full_name: normalizeName_(rows[i].full_name), city_id: cid } : null;
    }
  }
  return null;
}
function findWhitelist_(phone) {
  var cities = getCities_();
  var hit = matchWhitelistRows_(cachedTable_('whitelist', 'Whitelist'), phone, cities);
  if (hit) return hit;
  // در کش پیدا نشد: یک بار مستقیم از خود برگه بخوان؛ شاید همین الآن اضافه شده باشد.
  return matchWhitelistRows_(readTable_('Whitelist'), phone, cities);
}

// ---------- Instructors (مدرسان) ----------
function listInstructors_(cityId) {
  return cachedTable_('instructors', 'Instructors').filter(function (r) {
    return num_(r.city_id) === num_(cityId) && String(r.is_active) !== '0' && r.is_active !== false;
  });
}
function getOrCreateInstructor_(cityId, name, createdByTg) {
  var clean = normalizeName_(name), norm = normCmp_(clean);
  var found = listInstructors_(cityId).filter(function (r) { return String(r.name_norm) === norm; })[0];
  if (found) return found;
  var id = nextId_('instructor_counter');
  appendRow_('Instructors', {
    instructor_id: id, city_id: cityId, name: clean, name_norm: norm, is_active: 1,
    created_by: createdByTg || '', created_at: nowStr_()
  });
  bustCache_();
  return findOne_('Instructors', function (r) { return num_(r.instructor_id) === id; });
}
function getInstructorInCity_(instructorId, cityId) {
  return listInstructors_(cityId).filter(function (r) { return num_(r.instructor_id) === num_(instructorId); })[0] || null;
}

// ---------- Reports ----------
function getReport_(rid) { return findOne_('Reports', function (r) { return num_(r.report_id) === num_(rid); }); }
function getReportPhotos_(rid) {
  return readTable_('ReportPhotos').filter(function (r) { return num_(r.report_id) === num_(rid); })
    .sort(function (a, b) { return num_(a.sort) - num_(b.sort); });
}
function setReportPhotos_(rid, photos) {
  var sh = sheet_('ReportPhotos'), rows = readTable_('ReportPhotos');
  for (var i = rows.length - 1; i >= 0; i--) if (num_(rows[i].report_id) === num_(rid)) sh.deleteRow(rows[i]._row);
  photos.forEach(function (p, idx) {
    appendRow_('ReportPhotos', { report_id: rid, sort: idx, file_id: p.file_id, file_unique_id: p.file_unique_id || '' });
  });
}
function createReport_(rep, photos) {
  var rid = nextId_('report_counter');
  rep.report_id = rid; rep.created_at = nowStr_(); rep.updated_at = nowStr_();
  appendRow_('Reports', rep);
  setReportPhotos_(rid, photos);
  return rid;
}
function updateReport_(rid, partial) {
  var r = getReport_(rid);
  if (!r) return false;
  partial.updated_at = nowStr_();
  updateRow_('Reports', r._row, partial);
  return true;
}
// قفل اتمیک تصمیم: فقط اولین ادمین (doPost زیر LockService است پس همزمانی نداریم)
function tryLockDecision_(rid, adminTg, newStatus) {
  var r = getReport_(rid);
  if (!r || r.status !== 'pending' || String(r.decided_by || '') !== '') return false;
  updateRow_('Reports', r._row, { status: newStatus, decided_by: adminTg, decided_at: nowStr_(), updated_at: nowStr_() });
  return true;
}
function unlockDecision_(rid) {
  updateReport_(rid, { status: 'pending', decided_by: '', decided_at: '' });
}
function myReports_(telegramId, cityId, limit) {
  return readTable_('Reports').filter(function (r) {
    return num_(r.submitter_telegram_id) === num_(telegramId) && num_(r.city_id) === num_(cityId);
  }).sort(function (a, b) { return num_(b.report_id) - num_(a.report_id); }).slice(0, limit);
}
function countReports_(status) {
  return readTable_('Reports').filter(function (r) { return r.status === status; }).length;
}
function subjectsSimilar_(a, b) {
  var na = normCmp_(a), nb = normCmp_(b);
  if (!na || !nb) return false;
  if (na === nb || na.indexOf(nb) !== -1 || nb.indexOf(na) !== -1) return true;
  function grams(s) { var g = {}, i; for (i = 0; i < s.length - 1; i++) g[s.slice(i, i + 2)] = 1; return Object.keys(g); }
  var ga = grams(na), gb = {}, hit = 0;
  grams(nb).forEach(function (g) { gb[g] = 1; });
  ga.forEach(function (g) { if (gb[g]) hit++; });
  return (2 * hit) / (ga.length + Object.keys(gb).length || 1) >= 0.8;
}
// فقط هشدار تکراری: همان شهرستان+تاریخ+ساعت+موضوع مشابه، یا عکس با file_unique_id تکراری
function duplicateHints_(cityId, eventJalali, eventTime, subject, photoUniques, excludeRid) {
  var hints = [], reports = readTable_('Reports');
  for (var i = 0; i < reports.length; i++) {
    var r = reports[i];
    if (excludeRid && num_(r.report_id) === num_(excludeRid)) continue;
    if (num_(r.city_id) === num_(cityId) && String(cellToStr_(r.event_jalali)) === eventJalali &&
      String(cellToStr_(r.event_time)) === eventTime && (r.status === 'pending' || r.status === 'approved') &&
      subjectsSimilar_(subject, r.subject)) {
      hints.push('گزارش ' + r.report_id + ' با همین شهرستان، تاریخ، ساعت و موضوع مشابه ثبت شده است');
      break;
    }
  }
  var clean = (photoUniques || []).filter(Boolean);
  if (clean.length) {
    var ph = readTable_('ReportPhotos');
    for (var j = 0; j < ph.length; j++) {
      if (excludeRid && num_(ph[j].report_id) === num_(excludeRid)) continue;
      if (clean.indexOf(String(ph[j].file_unique_id)) !== -1) {
        hints.push('یکی از عکس‌ها قبلاً در گزارش ' + ph[j].report_id + ' استفاده شده است');
        break;
      }
    }
  }
  return hints;
}

// ---------- Sessions (وضعیت فرم؛ در کش + برگه Sessions برای ماندگاری) ----------
function sessKey_(chatId, userId) { return chatId + ':' + userId; }
function getSession_(chatId, userId) {
  var key = sessKey_(chatId, userId), c = cache_().get('sess_' + key);
  if (c) { try { return JSON.parse(c); } catch (e) { } }
  var row = findOne_('Sessions', function (r) { return String(cellToStr_(r.key)) === key; });
  var s = row ? { state: String(row.state || ''), data: safeJson_(cellToStr_(row.data_json)), _row: row._row }
    : { state: '', data: {}, _row: 0 };
  try { cache_().put('sess_' + key, JSON.stringify(s), CFG.CACHE_SECONDS); } catch (e) { }
  return s;
}
function saveSession_(chatId, userId, session) {
  var key = sessKey_(chatId, userId);
  var payload = {
    key: key, chat_id: String(chatId), user_id: String(userId), state: session.state || '',
    data_json: JSON.stringify(session.data || {}), updated_at: nowStr_()
  };
  if (session._row) updateRow_('Sessions', session._row, payload);
  else session._row = appendRow_('Sessions', payload);
  try { cache_().put('sess_' + key, JSON.stringify(session), CFG.CACHE_SECONDS); } catch (e) { }
}
function clearSession_(chatId, userId) {
  var s = getSession_(chatId, userId);
  s.state = ''; s.data = {};
  saveSession_(chatId, userId, s);
}
function safeJson_(s) { try { return JSON.parse(s || '{}'); } catch (e) { return {}; } }

// ---------- Logs ‪(فقط warn/error در شیت)‬ + آمار کش + حذف تکراری ----------
function log_(level, event, detail, updateId) {
  // info فقط با Settings.debug=1 در Logs نوشته می‌شود؛ بقیه آمار در کش‌اند تا سهمیه و برگه شلوغ نشود.
  if (level === 'info') {
    var dbg = null;
    try { dbg = getSetting_('debug'); } catch (e) { }
    if (dbg !== '1') return;
  }
  try {
    appendRow_('Logs', {
      ts: nowStr_(), level: level, event: event,
      detail: String(detail || '').slice(0, 500), update_id: updateId || ''
    });
  } catch (e) { }
}
function seenUpdate_(updateId) {
  if (!updateId) return false;
  var c = cache_(), list = safeJsonArr_(c.get('seen_ids'));
  if (list.indexOf(updateId) !== -1) return true;
  list.push(updateId);
  if (list.length > 300) list = list.slice(list.length - 300);
  try { c.put('seen_ids', JSON.stringify(list), 21600); } catch (e) { }
  return false;
}
function safeJsonArr_(s) { try { var v = JSON.parse(s || '[]'); return Array.isArray(v) ? v : []; } catch (e) { return []; } }
// آمار اجرا برای /status — در کش، بر اساس روز تهران؛ جمع امروز+دیروز ≈ ۲۴ ساعت اخیر
function recordUpdateStats_(execMs, isLockTimeout) {
  try {
    var day = Utilities.formatDate(new Date(), 'Asia/Tehran', 'yyyy-MM-dd');
    var key = 'st_' + day, st = safeJson_(cache_().get(key));
    st.n = num_(st.n); st.sumMs = num_(st.sumMs); st.maxMs = num_(st.maxMs);
    st.lockTimeouts = num_(st.lockTimeouts); st.updates = num_(st.updates);
    if (isLockTimeout) st.lockTimeouts += 1;
    else { st.updates += 1; if (execMs > 0) { st.n += 1; st.sumMs += execMs; if (execMs > st.maxMs) st.maxMs = execMs; } }
    cache_().put(key, JSON.stringify(st), 21600);
  } catch (e) { }
}
function getExecStats24h_() {
  var now = new Date();
  var days = [Utilities.formatDate(now, 'Asia/Tehran', 'yyyy-MM-dd'),
  Utilities.formatDate(new Date(now.getTime() - 86400000), 'Asia/Tehran', 'yyyy-MM-dd')];
  var all = { n: 0, sumMs: 0, maxMs: 0, lockTimeouts: 0, updatesToday: 0 };
  days.forEach(function (d, idx) {
    var st = {};
    try { st = safeJson_(cache_().get('st_' + d)); } catch (e) { }
    all.n += num_(st.n); all.sumMs += num_(st.sumMs);
    if (num_(st.maxMs) > all.maxMs) all.maxMs = num_(st.maxMs);
    all.lockTimeouts += num_(st.lockTimeouts);
    if (idx === 0) all.updatesToday = num_(st.updates);
  });
  all.avgSec = all.n ? Math.round((all.sumMs / all.n) / 100) / 10 : 0;
  all.maxSec = Math.round(all.maxMs / 100) / 10;
  return all;
}
// ضداسپم ارسال گزارش (در CacheService)
function submitAllowed_(tg) {
  var key = 'rl_' + tg, now = Date.now(), list = safeJsonArr_(cache_().get(key))
    .filter(function (t) { return now - t < 60000; });
  if (list.length >= CFG.MAX_REPORTS_PER_MINUTE) { try { cache_().put(key, JSON.stringify(list), 120); } catch (e) { } return false; }
  list.push(now);
  try { cache_().put(key, JSON.stringify(list), 120); } catch (e) { }
  return true;
}
