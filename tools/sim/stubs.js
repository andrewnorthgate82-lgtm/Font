// نسخهٔ حافظه‌ای سرویس‌های Apps Script + تلگرام ساختگی
const fs = require('fs'), path = require('path'), vm = require('vm');
function makeEnv() {
  const logs = [], tgCalls = [], sheets = {}, props = { BOT_TOKEN: 'T', GAS_KEY: 'K' }, cache = {};
  let msgId = 1000;
  function Range(sh, r, c, nr, nc) {
    nr = nr || 1; nc = nc || 1;
    return {
      getValues() { const out = []; for (let i = 0; i < nr; i++) { const row = []; for (let j = 0; j < nc; j++) { const v = (sh.data[r - 1 + i] || [])[c - 1 + j]; row.push(v === undefined ? '' : v); } out.push(row); } return out; },
      getValue() { return this.getValues()[0][0]; },
      setValues(v) { for (let i = 0; i < v.length; i++) { while (sh.data.length < r + i) sh.data.push([]); for (let j = 0; j < v[i].length; j++) { let x = v[i][j]; if (typeof x === 'string' && x.charAt(0) === "'") x = x.slice(1); sh.data[r - 1 + i][c - 1 + j] = x; } } return this; },
      setValue(v) { return this.setValues([[v]]); },
      setNumberFormat() { return this; }, setNote() { return this; }, setDataValidation() { return this; }
    };
  }
  function Sheet(name) {
    const sh = { name, data: [] };
    Object.assign(sh, {
      getName() { return name; },
      getLastRow() { let n = sh.data.length; while (n > 0 && !(sh.data[n - 1] || []).some(v => v !== '' && v !== undefined)) n--; return n; },
      getMaxRows() { return Math.max(1000, sh.data.length); },
      getRange(r, c, nr, nc) { return Range(sh, r, c, nr, nc); },
      getDataRange() { const lr = sh.getLastRow(); const lc = Math.max(1, ...sh.data.map(r => r.length)); return Range(sh, 1, 1, lr, lc); },
      deleteRow(r) { sh.data.splice(r - 1, 1); }, setFrozenRows() { }, setRightToLeft() { }
    });
    return sh;
  }
  const book = {
    getId() { return 'SS1'; },
    getSheetByName(n) { return sheets[n] || null; },
    insertSheet(n) { sheets[n] = Sheet(n); return sheets[n]; },
    deleteSheet(s) { delete sheets[s.name]; }, getSheets() { return Object.values(sheets); }
  };
  function fmt(d, tz, pat) {
    const p = {}; new Intl.DateTimeFormat('en-GB', { timeZone: tz || 'Asia/Tehran', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
      .formatToParts(d).forEach(x => p[x.type] = x.value);
    return pat.replace('yyyy', p.year).replace('MM', p.month).replace('dd', p.day).replace('HH', p.hour).replace('mm', p.minute).replace('ss', p.second);
  }
  const tgResult = { failMethods: {} };
  function tg(method, payload) {
    tgCalls.push({ method, p: payload });
    if (tgResult.failMethods[method]) return { ok: false, description: 'Bad Request: sim fail' };
    if (method === 'sendMediaGroup') return { ok: true, result: payload.media.map(() => ({ message_id: ++msgId })) };
    if (method === 'getChat') return { ok: true, result: { id: -100777, title: 'کانال', type: 'channel' } };
    if (method === 'getMe') return { ok: true, result: { id: 1, username: 'nasraesfahanbot' } };
    if (/^send|^copy|^forward/.test(method)) return { ok: true, result: { message_id: ++msgId, chat: { id: payload.chat_id } } };
    return { ok: true, result: true };
  }
  const ctx = {
    console, JSON, Math, Date, Intl, Object, Array, String, Number, RegExp, Error, encodeURIComponent, decodeURIComponent, unescape, escape, isNaN, parseInt, parseFloat,
    Logger: { log: (x) => logs.push(String(x)) },
    SpreadsheetApp: { getActiveSpreadsheet: () => book, openById: () => book, flush() { }, newDataValidation: () => { const b = { requireValueInList: () => b, setAllowInvalid: () => b, build: () => ({}) }; return b; } },
    PropertiesService: { getScriptProperties: () => ({ getProperty: k => (k in props ? props[k] : null), setProperty: (k, v) => { props[k] = String(v); }, deleteProperty: k => { delete props[k]; }, getProperties: () => Object.assign({}, props) }) },
    CacheService: { getScriptCache: () => ({ get: k => (k in cache ? cache[k] : null), put: (k, v) => { cache[k] = String(v); }, remove: k => { delete cache[k]; }, removeAll: ks => ks.forEach(k => delete cache[k]), getAll: ks => { const o = {}; ks.forEach(k => { if (k in cache) o[k] = cache[k]; }); return o; }, putAll: o => Object.assign(cache, o) }) },
    LockService: { getScriptLock: () => ({ tryLock: () => true, waitLock() { }, releaseLock() { }, hasLock: () => true }) },
    Utilities: { formatDate: fmt, sleep() { }, getUuid: () => 'uuid-' + Math.random().toString(36).slice(2), newBlob: (c, t, n) => ({ c, t, n, getName: () => n, setName() { } }), zip: (b, n) => ({ parts: b, getName: () => n, setName() { } }), base64Encode: s => Buffer.from(String(s)).toString('base64') },
    UrlFetchApp: {
      fetch(url, opt) {
        const m = url.match(/\/bot[^/]*\/(\w+)/); const method = m ? m[1] : '';
        let payload = {}; try { payload = opt && opt.payload ? (typeof opt.payload === 'string' ? JSON.parse(opt.payload) : opt.payload) : {}; } catch (e) { }
        const body = tg(method, payload);
        return { getResponseCode: () => (body.ok ? 200 : 400), getContentText: () => JSON.stringify(body) };
      }
    },
    ContentService: { createTextOutput: t => ({ t, setMimeType() { return this; } }), MimeType: { JSON: 'json', TEXT: 'text' } },
    ScriptApp: { getProjectTriggers: () => [], deleteTrigger() { }, newTrigger: () => { const b = { timeBased: () => b, atHour: () => b, nearMinute: () => b, everyDays: () => b, inTimezone: () => b, after: () => b, create: () => ({}) }; return b; } },
    DriveApp: { getFolderById: () => ({}), createFolder: () => ({ getId: () => 'F' }), getFileById: () => ({ makeCopy: () => ({}) }) },
    Session: { getScriptTimeZone: () => 'Asia/Tehran' }
  };
  vm.createContext(ctx);
  const dir = path.join(__dirname, '..', '..', 'nasra-gas');
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.gs')).sort();
  const all = files.map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('\n;\n');
  vm.runInContext(all, ctx, { filename: 'all.gs' });
  return { ctx, logs, tgCalls, sheets, props, cache, tgResult };
}
module.exports = { makeEnv };
