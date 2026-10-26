// Jalali.gs — تقویم شمسی خالص جاوااسکریپت (تبدیل شمسی↔میلادی با حساب کبیسه) — بدون کتابخانه خارجی

var JALALI_MONTHS = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور',
  'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'];
var JALALI_BREAKS = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
  1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];

// دقت: تقسیم و باقی‌مانده باید «به سمت صفر» خرد شوند (مثل ~~)، نه floor — وگرنه ماه‌های میلادی کمتر از ۸ خراب می‌شوند.
function jDiv_(a, b) { return Math.trunc(a / b); }
function jMod_(a, b) { return a - Math.trunc(a / b) * b; }

function jalCal_(jy) {
  var bl = JALALI_BREAKS.length, gy = jy + 621, leapJ = -14, jp = JALALI_BREAKS[0],
    jm, jump, leap, leapG, march, n, i;
  for (i = 1; i < bl; i += 1) {
    jm = JALALI_BREAKS[i]; jump = jm - jp;
    if (jy < jm) break;
    leapJ += jDiv_(jump, 33) * 8 + jDiv_(jMod_(jump, 33), 4);
    jp = jm;
  }
  n = jy - jp;
  leapJ += jDiv_(n, 33) * 8 + jDiv_(jMod_(n, 33) + 3, 4);
  if (jMod_(jump, 33) === 4 && jump - n === 4) leapJ += 1;
  leapG = jDiv_(gy, 4) - jDiv_((jDiv_(gy, 100) + 1) * 3, 4) - 150;
  march = 20 + leapJ - leapG;
  if (jump - n < 6) n = n - jump + jDiv_(jump + 4, 33) * 33;
  leap = jMod_(jMod_(n + 1, 33) - 1, 4);
  if (leap === -1) leap = 4;
  return { leap: leap, gy: gy, march: march };
}
function g2d_(gy, gm, gd) {
  var d = jDiv_((gy + jDiv_(gm - 8, 6) + 100100) * 1461, 4)
    + jDiv_(153 * jMod_(gm + 9, 12) + 2, 5) + gd - 34840408;
  d = d - jDiv_(jDiv_(gy + 100100 + jDiv_(gm - 8, 6), 100) * 3, 4) + 752;
  return d;
}
function d2g_(jdn) {
  var j = 4 * jdn + 139361631;
  j = j + jDiv_(jDiv_(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
  var i = jDiv_(jMod_(j, 1461), 4) * 5 + 308;
  return {
    gy: jDiv_(j, 1461) - 100100 + jDiv_(8 - (jMod_(jDiv_(i, 153), 12) + 1), 6),
    gm: jMod_(jDiv_(i, 153), 12) + 1,
    gd: jDiv_(jMod_(i, 153), 5) + 1
  };
}
function j2d_(jy, jm, jd) {
  var r = jalCal_(jy);
  return g2d_(r.gy, 3, r.march) + (jm - 1) * 31 - jDiv_(jm, 7) * (jm - 7) + jd - 1;
}
function d2j_(jdn) {
  var gy = d2g_(jdn).gy, jy = gy - 621, r = jalCal_(jy);
  var k = jdn - g2d_(gy, 3, r.march), jm, jd;
  if (k >= 0) {
    if (k <= 185) { return { jy: jy, jm: 1 + jDiv_(k, 31), jd: jMod_(k, 31) + 1 }; }
    k -= 186;
  } else {
    jy -= 1; k += 179;
    if (r.leap === 1) k += 1;
  }
  jm = 7 + jDiv_(k, 30); jd = jMod_(k, 30) + 1;
  return { jy: jy, jm: jm, jd: jd };
}
// میلادی ← شمسی
function jalaliToGregorian_(jy, jm, jd) { return d2g_(j2d_(jy, jm, jd)); }
// شمسی ← میلادی
function gregorianToJalali_(gy, gm, gd) { return d2j_(g2d_(gy, gm, gd)); }

function daysInJalaliMonth_(jy, jm) {
  if (jm <= 6) return 31;
  if (jm <= 11) return 30;
  return jalCal_(jy).leap === 0 ? 30 : 29; // leap===0 یعنی همان سال کبیسه است
}
function isValidJalali_(jy, jm, jd) {
  return jm >= 1 && jm <= 12 && jd >= 1 && jd <= daysInJalaliMonth_(jy, jm);
}
function pad2_(n) { return (n < 10 ? '0' : '') + n; }
function formatJalali_(jy, jm, jd) { return jy + '/' + pad2_(jm) + '/' + pad2_(jd); } // 1405/07/23
function jalaliToGregStr_(jy, jm, jd) {
  var g = jalaliToGregorian_(jy, jm, jd);
  return g.gy + '-' + pad2_(g.gm) + '-' + pad2_(g.gd);
}
function monthTag_(jy, jm) { return JALALI_MONTHS[jm - 1] + jy; } // مهر1405

// امروز تهران به‌صورت {jy,jm,jd}
function todayJalali_() {
  var s = Utilities.formatDate(new Date(), 'Asia/Tehran', 'yyyy-MM-dd').split('-');
  return gregorianToJalali_(parseInt(s[0], 10), parseInt(s[1], 10), parseInt(s[2], 10));
}
function todayJalaliStr_() {
  var t = todayJalali_();
  return formatJalali_(t.jy, t.jm, t.jd);
}
function jalaliOffset_(days) { // امروز + چند روز (منفی یعنی گذشته)
  var t = todayJalali_();
  return d2j_(j2d_(t.jy, t.jm, t.jd) + days);
}
function isFutureJalali_(jy, jm, jd) {
  var t = todayJalali_();
  return j2d_(jy, jm, jd) > j2d_(t.jy, t.jm, t.jd);
}
