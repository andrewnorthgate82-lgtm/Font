/*
 * Nasra Esfahan Media Studio — Google Apps Script backup mirror
 * Deploy as a Web app. Store the shared GAS_KEY in Script Properties (not code).
 * Mirror tabs append/upsert and NEVER delete rows. Only disposable snapshot
 * slots are overwritten; the last N slots are retained.
 */
var NASRA_TABLES = ['studio_settings', 'playbook_versions', 'brand_kit', 'sources', 'source_items', 'platform_specs', 'agents', 'topic_candidates', 'content_items', 'content_versions', 'gate_decisions', 'agent_runs', 'ai_daily_usage', 'jobs', 'publish_jobs', 'inbox_messages', 'metrics', 'experiments', 'backup_snapshots', 'audit_logs'];

function nasraJson(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
function nasraKeyProvided(body) {
  var expected = PropertiesService.getScriptProperties().getProperty('GAS_KEY');
  return expected && body && body.key && String(body.key) === String(expected);
}
function nasraCell(value, isText) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  var text = String(value);
  // An apostrophe is a Sheets text marker and is not displayed in the cell.
  if (isText && (/^\d{4}\/\d{1,2}\/\d{1,2}/.test(text) || /^\d{1,2}:\d{2}(?::\d{2})?$/.test(text))) return "'" + text;
  return text;
}
function nasraTextColumn(name, body, table) {
  if (body.text_columns && body.text_columns[table] && body.text_columns[table].indexOf(name) !== -1) return true;
  return /(^id$|_id$|_at$|_date$|_time$|date|time|jalali|scheduled|created|updated|expires|timestamp)/i.test(name);
}
function nasraRowKey(row, headers) {
  var preferred = ['id', 'key', 'provider'];
  for (var i = 0; i < preferred.length; i++) if (row[preferred[i]] !== undefined && row[preferred[i]] !== null && row[preferred[i]] !== '') return String(row[preferred[i]]);
  if (row.date_utc !== undefined && row.scope !== undefined) return String(row.date_utc) + '|' + String(row.scope);
  if (row.version !== undefined && row.title !== undefined) return String(row.version) + '|' + String(row.title);
  // All current tables have a stable key. This fallback avoids deleting or
  // collapsing rows if a future table does not: each row gets a content hash-like key.
  return JSON.stringify(row);
}
function nasraHeaders(rows) {
  var headers = [], seen = {};
  rows.forEach(function(row) { Object.keys(row || {}).forEach(function(key) { if (!seen[key]) { seen[key] = true; headers.push(key); } }); });
  return headers;
}
function nasraPrepareSheet(ss, name, headers) {
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  var existing = [];
  if (sheet.getLastColumn() > 0) existing = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0].map(String).filter(function(x) { return x !== ''; });
  var merged = existing.slice();
  headers.forEach(function(header) { if (merged.indexOf(header) === -1) merged.push(header); });
  if (!merged.length) merged = ['id'];
  // Header changes do not remove any old columns or data.
  if (sheet.getLastColumn() < merged.length || sheet.getLastRow() === 0) sheet.getRange(1, 1, 1, merged.length).setValues([merged]);
  else {
    var oldHeaderCount = sheet.getLastColumn();
    if (merged.length > oldHeaderCount) sheet.getRange(1, oldHeaderCount + 1, 1, merged.length - oldHeaderCount).setValues([merged.slice(oldHeaderCount)]);
  }
  return { sheet: sheet, headers: merged };
}
function nasraMirror(ss, table, rows, body) {
  if (!rows || !rows.length) return;
  var prepared = nasraPrepareSheet(ss, 'mirror_' + table, nasraHeaders(rows));
  var sheet = prepared.sheet, headers = prepared.headers;
  var keyColumn = headers.indexOf('id') !== -1 ? 'id' : headers.indexOf('key') !== -1 ? 'key' : headers.indexOf('provider') !== -1 ? 'provider' : '';
  var existingValues = sheet.getLastRow() > 1 ? sheet.getRange(2, 1, sheet.getLastRow() - 1, headers.length).getValues() : [];
  var index = {};
  existingValues.forEach(function(oldRow, i) {
    var oldObject = {}; headers.forEach(function(h, j) { oldObject[h] = oldRow[j]; });
    index[nasraRowKey(oldObject, headers)] = i + 2;
  });
  rows.forEach(function(row) {
    var key = nasraRowKey(row, headers);
    var target = index[key];
    var values = headers.map(function(header) { return nasraCell(row[header], nasraTextColumn(header, body, table)); });
    if (!target) target = sheet.getLastRow() + 1;
    // TEXT format is applied BEFORE setValues, including dates, times and IDs.
    for (var col = 0; col < headers.length; col++) if (nasraTextColumn(headers[col], body, table)) sheet.getRange(target, col + 1).setNumberFormat('@');
    sheet.getRange(target, 1, 1, values.length).setValues([values]);
    index[key] = target;
  });
}
function nasraSnapshot(ss, table, rows, slot, body) {
  var name = 'snap_' + ('0' + slot).slice(-2) + '_' + table;
  var prepared = nasraPrepareSheet(ss, name, nasraHeaders(rows));
  var sheet = prepared.sheet, headers = prepared.headers;
  // Only a rolling snapshot slot is cleared. mirror_* tabs are never cleared.
  if (sheet.getLastRow() > 1) sheet.getRange(2, 1, sheet.getLastRow() - 1, Math.max(sheet.getLastColumn(), headers.length)).clearContent();
  if (!rows.length) return;
  var values = rows.map(function(row) { return headers.map(function(header) { return nasraCell(row[header], nasraTextColumn(header, body, table)); }); });
  for (var col = 0; col < headers.length; col++) if (nasraTextColumn(headers[col], body, table)) sheet.getRange(2, col + 1, values.length, 1).setNumberFormat('@');
  sheet.getRange(2, 1, values.length, headers.length).setValues(values);
}
function nasraBackup(body) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var tables = body.tables || {};
  var keep = Math.max(1, Math.min(60, Number(body.keep_snapshots) || 14));
  NASRA_TABLES.forEach(function(table) { if (tables[table]) nasraMirror(ss, table, tables[table], body); });
  var props = PropertiesService.getScriptProperties();
  var sequence = Number(props.getProperty('NASRA_SNAPSHOT_SEQUENCE') || '0') + 1;
  props.setProperty('NASRA_SNAPSHOT_SEQUENCE', String(sequence));
  var slot = ((sequence - 1) % keep) + 1;
  NASRA_TABLES.forEach(function(table) { if (tables[table]) nasraSnapshot(ss, table, tables[table], slot, body); });
  var log = ss.getSheetByName('backup_log') || ss.insertSheet('backup_log');
  if (log.getLastRow() === 0) log.appendRow(['snapshot_id', 'jalali_date', 'tehran_time', 'slot', 'received_at', 'tables']);
  log.getRange(log.getLastRow() + 1, 1, 1, 6).setNumberFormats([['@', '@', '@', '@', '@', '@']]);
  log.appendRow([String(body.snapshot_id || ''), "'" + String(body.jalali_date || ''), "'" + String(body.tehran_time || ''), slot, new Date().toISOString(), Object.keys(tables).join(',')]);
  return { ok: true, snapshot_id: body.snapshot_id, slot: slot, keep_snapshots: keep, mirrored_tables: Object.keys(tables), mirror_policy: 'append_or_upsert_never_delete', text_columns: '@' };
}
function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents || '{}');
    if (!nasraKeyProvided(body)) return nasraJson({ ok: false, error: 'unauthorized' });
    return nasraJson(nasraBackup(body));
  } catch (error) {
    return nasraJson({ ok: false, error: String(error && error.message ? error.message : error) });
  }
}
function doGet() {
  return nasraJson({ ok: true, service: 'Nasra Esfahan backup endpoint', method: 'POST with GAS_KEY required', mirror_policy: 'never_delete' });
}
