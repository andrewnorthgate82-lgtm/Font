// Jalali helpers mirrored from worker.js; ASCII storage, Persian UI digits.
export const TEHRAN_OFFSET = 210;
const BREAKS = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];
const div = (a, b) => Math.trunc(a / b);
const mod = (a, b) => a - Math.trunc(a / b) * b;
export const faDigits = value => String(value ?? '').replace(/[0-9]/g, c => '۰۱۲۳۴۵۶۷۸۹'[Number(c)]);
export const asciiDigits = value => String(value ?? '').replace(/[۰-۹]/g, c => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(c))).replace(/[٠-٩]/g, c => String('٠١٢٣٤٥٦٧٨٩'.indexOf(c)));
export const pad = (n, w = 2) => String(n).padStart(w, '0');
export const monthNames = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'];
export const weekdayNames = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];
function jalCal(jy, withoutLeap = false) {
  const gy = jy + 621; let leapJ = -14; let jp = BREAKS[0]; let jm = 0; let jump = 0;
  if (jy < jp || jy >= BREAKS[BREAKS.length - 1]) throw new Error('سال جلالی خارج از محدوده است');
  for (let i = 1; i < BREAKS.length; i++) { jm = BREAKS[i]; jump = jm - jp; if (jy < jm) break; leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4); jp = jm; }
  let n = jy - jp; leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4); if (mod(jump, 33) === 4 && jump - n === 4) leapJ++;
  const leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150; const march = 20 + leapJ - leapG;
  if (withoutLeap) return { gy, march, leap: 0 };
  if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33;
  let leap = mod(mod(n + 1, 33) - 1, 4); if (leap === -1) leap = 4;
  return { gy, march, leap };
}
function g2d(gy, gm, gd) { let d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408; return d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752; }
// Exact inverse conversion; all integer divisions truncate toward zero.
function d2gExact(jdn) { let j = 4 * jdn + 139361631; j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908; const i = div(mod(j, 1461), 4) * 5 + 308; const gd = div(mod(i, 153), 5) + 1; const gm = mod(div(i, 153), 12) + 1; const gy = div(j, 1461) - 100100 + div(8 - gm, 6); return { gy, gm, gd }; }
function j2d(jy, jm, jd) { const r = jalCal(jy, true); return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1; }
function d2j(jdn) { const gy = d2gExact(jdn).gy; let jy = gy - 621; const r = jalCal(jy); const first = g2d(gy, 3, r.march); let k = jdn - first; let jm, jd; if (k >= 0) { if (k <= 185) return { jy, jm: 1 + div(k, 31), jd: mod(k, 31) + 1 }; k -= 186; } else { jy--; k += 179; if (r.leap === 1) k++; } jm = 7 + div(k, 30); jd = mod(k, 30) + 1; return { jy, jm, jd }; }
export function monthLength(jy, jm) { if (jm <= 6) return 31; if (jm <= 11) return 30; return jalCal(jy).leap === 0 ? 30 : 29; }
export function jalaliToGregorian(jy, jm, jd) { jy = Number(jy); jm = Number(jm); jd = Number(jd); if (!Number.isInteger(jy) || !Number.isInteger(jm) || !Number.isInteger(jd) || jm < 1 || jm > 12 || jd < 1 || jd > monthLength(jy, jm)) throw new Error('تاریخ جلالی معتبر نیست'); return d2gExact(j2d(jy, jm, jd)); }
export function gregorianToJalali(gy, gm, gd) { const date = new Date(Date.UTC(Number(gy), Number(gm) - 1, Number(gd))); if (date.getUTCFullYear() !== Number(gy) || date.getUTCMonth() !== Number(gm) - 1 || date.getUTCDate() !== Number(gd)) throw new Error('تاریخ میلادی معتبر نیست'); return d2j(g2d(Number(gy), Number(gm), Number(gd))); }
export function jalaliString(j) { return `${pad(j.jy, 4)}/${pad(j.jm)}/${pad(j.jd)}`; }
export function tehranParts(value = new Date()) { const d = value instanceof Date ? value : new Date(value); const t = new Date(d.getTime() + TEHRAN_OFFSET * 60000); return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate(), hour: t.getUTCHours(), minute: t.getUTCMinutes(), second: t.getUTCSeconds(), weekday: t.getUTCDay() }; }
export function utcToJalali(value = new Date()) { const p = tehranParts(value); const j = gregorianToJalali(p.year, p.month, p.day); return { ...j, ...p, date: jalaliString(j), time: `${pad(p.hour)}:${pad(p.minute)}` }; }
export function jalaliDateTimeToUtc(date, time = '00:00') { const m = asciiDigits(date).replace(/[-.]/g, '/').match(/^(\d{4})\/(\d{1,2})\/(\d{1,2})$/); const t = asciiDigits(time).match(/^(\d{1,2}):(\d{2})$/); if (!m || !t || Number(t[1]) > 23 || Number(t[2]) > 59) throw new Error('تاریخ یا ساعت معتبر نیست'); const g = jalaliToGregorian(Number(m[1]), Number(m[2]), Number(m[3])); return new Date(Date.UTC(g.gy, g.gm - 1, g.gd, Number(t[1]), Number(t[2])) - TEHRAN_OFFSET * 60000); }
export function formatJalali(value, withTime = true) { if (!value) return '—'; try { const j = utcToJalali(value); return faDigits(`${j.date}${withTime ? ` ${j.time}` : ''}`); } catch { return '—'; } }
export function todayJalali() { return utcToJalali(new Date()); }
export function monthGrid(jy, jm) { const first = jalaliToGregorian(jy, jm, 1); const firstUtc = new Date(Date.UTC(first.gy, first.gm - 1, first.gd)); // Persian calendar grids start on Saturday, not Sunday.
  const firstWeekday = (new Date(firstUtc.getTime() + TEHRAN_OFFSET * 60000).getUTCDay() + 1) % 7; const length = monthLength(jy, jm); const cells = []; const prevYear = jm === 1 ? jy - 1 : jy; const prevMonth = jm === 1 ? 12 : jm - 1; const nextYear = jm === 12 ? jy + 1 : jy; const nextMonth = jm === 12 ? 1 : jm + 1; const prevLength = monthLength(prevYear, prevMonth); for (let i = firstWeekday - 1; i >= 0; i--) cells.push({ jy: prevYear, jm: prevMonth, jd: prevLength - i, other: true }); for (let d = 1; d <= length; d++) cells.push({ jy, jm, jd: d, other: false }); let next = 1; while (cells.length % 7) { cells.push({ jy: nextYear, jm: nextMonth, jd: next++, other: true }); } return cells; }
export function shiftMonth(jy, jm, delta) { const index = jy * 12 + (jm - 1) + delta; return { jy: Math.floor(index / 12), jm: mod(index, 12) + 1 }; }
export function relativeTime(value) { if (!value) return '—'; const diff = Date.now() - new Date(value).getTime(); const min = Math.round(Math.abs(diff) / 60000); if (min < 1) return 'همین حالا'; if (min < 60) return faDigits(`${min} دقیقه ${diff >= 0 ? 'پیش' : 'بعد'}`); const hours = Math.round(min / 60); if (hours < 24) return faDigits(`${hours} ساعت ${diff >= 0 ? 'پیش' : 'بعد'}`); return formatJalali(value); }
