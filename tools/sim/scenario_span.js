// سناریو: پنل ← 📊 آمار ماهانه ← 🗓 بازه دلخواه ← گزینه آماده / تقویم (ماه و روز شروع ← ماه و روز پایان) / تایپ دو تاریخ
// ← آمار بازه ← 📥 فایل اکسل این بازه (همه شهرستان‌ها / یک شهرستان خواهر-برادر)
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '29', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
let uid = 1, mid = 1, pass = 0, fail = 0, cbs = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text) { post({ message: { message_id: mid++, from: { id: 500, first_name: 'x' }, chat: { id: 500, type: 'private' }, date: 1, text } }); }
function cb(data) { cbs++; post({ callback_query: { id: 'c' + uid, from: { id: 500, first_name: 'علی' }, data, message: { message_id: 9, chat: { id: 500, type: 'private' } } } }); }
const since = n => env.tgCalls.slice(n);
const answer = calls => { const a = calls.filter(c => c.method === 'answerCallbackQuery').pop(); return a ? (a.p.text || '') : ''; };
const shown = calls => calls.filter(c => c.method === 'editMessageText' || c.method === 'sendMessage').pop();
const btns = s => s.p.reply_markup.inline_keyboard.flat();
const errs = () => env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':'));
const K = d => C.dateKey_(d.jy, d.jm, d.jd), off = n => C.jalaliOffset_(n);

// گزارش‌ها: امروز، ۵ روز پیش، ۲۰ روز پیش، ۴۰ روز پیش (تاییدشده) + یک ردشده و یک در انتظار
let rid = 0;
function add(days, extra) {
  const d = off(-days);
  C.appendRow_('Reports', Object.assign({ report_id: String(++rid), status: 'approved', city_id: '29', submitter_telegram_id: '700', submitter_position: 'مسئول نسرا خواهر',
    event_year: String(d.jy), event_month: String(d.jm), event_day: String(d.jd), event_jalali: C.formatJalali_(d.jy, d.jm, d.jd),
    class_type: '#حضوری', subject: 'موضوع ' + rid, participants: '10', score: '4' }, extra || {}));
}
add(0); add(5, { city_id: '1', class_type: '#مجازی', score: '5' }); add(20); add(40); add(3, { status: 'rejected', score: '2' }); add(2, { status: 'pending', score: '' });
C.bustCache_();
const today = K(off(0)), t = C.todayJalali_();

// ---------- ورود از آمار ماهانه ----------
const cur = C.currentPeriod_();
let n = env.tgCalls.length; cb('A:s:' + cur.jy + ':' + cur.jm);
let s = shown(since(n));
ok(btns(s).some(b => b.callback_data === 'A:sr' && b.text === '🗓 بازه دلخواه'), 'دکمه بازه دلخواه در آمار ماهانه');
n = env.tgCalls.length; cb('A:xm:0');
ok(btns(shown(since(n))).some(b => b.callback_data === 'A:sr'), 'دکمه بازه دلخواه در انتخاب ماه فایل اکسل');

// ---------- منو + گزینه آماده ----------
n = env.tgCalls.length; cb('A:sr');
s = shown(since(n));
ok(/آمار بازه دلخواه/.test(s.p.text) && /1405\/07\/01 تا 1405\/07\/30/.test(s.p.text), 'متن منو با نمونه تایپ');
ok(C.getSession_(500, 500).state === 'adm:span', 'حالت تایپ تاریخ فعال شد');
const b7 = btns(s).find(b => /۷ روز اخیر/.test(b.text)), b30 = btns(s).find(b => /۳۰ روز اخیر/.test(b.text));
ok(b7.callback_data === 'A:sR:' + K(off(-6)) + ':' + today && b30.callback_data === 'A:sR:' + K(off(-29)) + ':' + today, 'گزینه‌های ۷ و ۳۰ روز: ' + b7.callback_data);
ok(btns(s).some(b => b.callback_data === 'A:sR:' + C.dateKey_(t.jy, 1, 1) + ':' + today), 'از اول سال تا امروز');
n = env.tgCalls.length; cb(b7.callback_data);
s = shown(since(n));
ok(/📊 گزارش بازه دلخواه \(7 روز\) — تاییدشده‌ها/.test(s.p.text) && s.p.text.includes('🗓 برنامه‌های از ' + C.keyText_(K(off(-6))) + ' تا ' + C.keyText_(today)), 'سرتیتر آمار ۷ روز:\n' + s.p.text.split('\n').slice(0, 3).join('\n'));
ok(/جمع: 2 گزارش — 20 نفر/.test(s.p.text), 'فقط تاییدشده‌های ۷ روز (امروز + ۵ روز پیش)');
ok(/نمره بازه = /.test(s.p.text) && /نجف آباد: ⭐️ 0\.6 از 5/.test(s.p.text), 'نمره بازه (۴ و ۲ ردشده ← میانگین ۳، یک بخش ← ۰٫۶): ' + (s.p.text.match(/نجف آباد[^\n]*/) || [''])[0]);
ok(C.getSession_(500, 500).state === '', 'کلیک دکمه ← حالت تایپ لغو شد');
const xBtn = btns(s).find(b => b.text === '📥 فایل اکسل این بازه');
ok(xBtn && xBtn.callback_data === 'A:xs:' + K(off(-6)) + ':' + today, 'دکمه اکسل همین بازه');
n = env.tgCalls.length; cb(b30.callback_data);
ok(/جمع: 3 گزارش/.test(shown(since(n)).p.text), '۳۰ روز ← ۳ گزارش تاییدشده');

// ---------- تقویم: شروع ۲۰ روز پیش ← پایان امروز ----------
const st = off(-20);
n = env.tgCalls.length; cb('A:sa');
s = shown(since(n));
const ym = st.jy * 100 + st.jm;
ok(btns(s)[0].callback_data === 'A:sd:' + (t.jy * 100 + t.jm) && btns(s).some(b => b.callback_data === 'A:sd:' + ym) && btns(s).length === 24 + 2, 'ماه‌های شروع (۲۴ ماه، جدیدترین اول)');
n = env.tgCalls.length; cb('A:sd:' + ym);
s = shown(since(n));
const dayBtns = btns(s).filter(b => /^A:se:/.test(b.callback_data));
const maxDay = ym === t.jy * 100 + t.jm ? t.jd : C.daysInJalaliMonth_(st.jy, st.jm);
ok(dayBtns.length === maxDay && dayBtns[0].text === '1', 'روزهای ماه شروع (تا امروز اگر ماه جاری است): ' + dayBtns.length);
n = env.tgCalls.length; cb('A:se:' + K(st));
s = shown(since(n));
ok(s.p.text.includes('✅ شروع بازه: ' + C.keyText_(K(st))) && btns(s)[0].callback_data === 'A:sR:' + K(st) + ':' + today && btns(s)[1].callback_data === 'A:sR:' + K(st) + ':' + K(st), 'تا امروز / فقط همین روز');
const endMonths = btns(s).filter(b => /^A:sf:/.test(b.callback_data));
ok(endMonths[endMonths.length - 1].callback_data === 'A:sf:' + K(st) + ':' + ym && endMonths[0].callback_data === 'A:sf:' + K(st) + ':' + (t.jy * 100 + t.jm), 'ماه‌های پایان: از ماه جاری تا ماه شروع');
n = env.tgCalls.length; cb('A:sf:' + K(st) + ':' + (t.jy * 100 + t.jm));
s = shown(since(n));
const endDays = btns(s).filter(b => /^A:sR:/.test(b.callback_data));
ok(endDays[endDays.length - 1].text === String(t.jd) && endDays[0].text === String(ym === t.jy * 100 + t.jm ? st.jd : 1), 'روزهای پایان: تا امروز (و نه قبل از شروع)');
n = env.tgCalls.length; cb(endDays[endDays.length - 1].callback_data);
ok(/جمع: 3 گزارش/.test(shown(since(n)).p.text), 'بازه تقویم ← ۳ گزارش');

// ---------- تایپ دو تاریخ ----------
cb('A:sr');
const a40 = off(-45), a10 = off(-10);
n = env.tgCalls.length; msg(C.formatJalali_(a10.jy, a10.jm, a10.jd).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]) + ' تا ' + C.formatJalali_(a40.jy, a40.jm, a40.jd));
s = shown(since(n));
ok(s && s.method === 'sendMessage' && /جمع: 2 گزارش/.test(s.p.text) && s.p.text.includes('از ' + C.keyText_(K(a40)) + ' تا ' + C.keyText_(K(a10))), 'تایپ (فارسی، برعکس) ← ۲۰ و ۴۰ روز پیش');
ok(C.getSession_(500, 500).state === '', 'بعد از تایپ درست، حالت پاک شد');
cb('A:sr'); n = env.tgCalls.length; msg('دیروز تا امروز');
ok(/نفهمیدم/.test(shown(since(n)).p.text) && C.getSession_(500, 500).state === 'adm:span', 'تایپ نامعتبر ← راهنما و حالت می‌ماند');
C.clearSession_(500, 500);

// ---------- اکسل بازه ----------
const sp30 = K(off(-29)) + ':' + today;
n = env.tgCalls.length; cb('A:xs:' + sp30);
s = shown(since(n));
ok(/📥 فایل اکسل — گزارش بازه دلخواه \(30 روز\)/.test(s.p.text) && /همه شهرستان‌ها: 3 گزارش/.test(s.p.text), 'صفحه اکسل بازه:\n' + s.p.text);
ok(btns(s).some(b => b.callback_data === 'A:sR:' + sp30 && /آمار این بازه/.test(b.text)) && btns(s).some(b => b.callback_data === 'A:xa:' + sp30), 'بازگشت به آمار بازه + همه شهرستان‌ها');
n = env.tgCalls.length; cb('A:xa:' + sp30);
let doc = since(n).find(c => c.method === 'sendDocument');
const fname = 'گزارش بازه ' + C.keyText_(K(off(-29))).replace(/\//g, '-') + ' تا ' + C.keyText_(today).replace(/\//g, '-') + ' - همه شهرستان‌ها.xlsx';
ok(doc && doc.p.document.getName() === fname, 'نام فایل: ' + (doc && doc.p.document.getName()));
ok(doc && /جمع: 3 گزارش تاییدشده — حضوری 2، مجازی 1/.test(doc.p.caption) && doc.p.caption.includes('برنامه‌های از ' + C.keyText_(K(off(-29)))), 'کپشن فایل:\n' + (doc && doc.p.caption));
const cityBtns = [0, 1, 2, 3].flatMap(pg => { n = env.tgCalls.length; cb('A:xc:' + sp30 + ':' + pg); return btns(shown(since(n))); });
ok(cityBtns.some(b => b.callback_data === 'A:xg:' + sp30 + ':29' && /\(2\)/.test(b.text)) && cityBtns.some(b => b.callback_data === 'A:xc:' + sp30 + ':1'), 'انتخاب شهرستان (صفحه‌بندی) با شمار گزارش بازه');
n = env.tgCalls.length; cb('A:xg:' + sp30 + ':29');
s = shown(since(n));
ok(/نجف آباد — گزارش بازه دلخواه/.test(s.p.text) && /خواهران: 2 گزارش/.test(s.p.text) && btns(s).some(b => b.callback_data === 'A:xf:' + sp30 + ':29:s'), 'شهرستان: خواهر/برادر/تجمیعی');
n = env.tgCalls.length; cb('A:xf:' + sp30 + ':29:s');
doc = since(n).find(c => c.method === 'sendDocument');
ok(doc && /نجف آباد - خواهران\.xlsx$/.test(doc.p.document.getName()) && /جمع: 2 گزارش/.test(doc.p.caption), 'فایل خواهران شهرستان');

// ---------- ورودی خراب ----------
['A:sR:14050730:14050701', 'A:sR:14051301:14051302', 'A:sd:999999', 'A:sd:' + (t.jy * 100 + t.jm + 1), 'A:se:abc', 'A:sf:14050701:140506', 'A:xs:14050730:14050701'].forEach(d => {
  n = env.tgCalls.length; cb(d);
  ok(/نامعتبر/.test(answer(since(n))), 'رد شد: ' + d + ' ← ' + answer(since(n)));
});
// همه کال‌بک‌ها پاسخ گرفتند + ≤۶۴ بایت
const answered = env.tgCalls.filter(c => c.method === 'answerCallbackQuery').length;
ok(answered >= cbs, 'هر کلیک پاسخ گرفت: ' + answered + '/' + cbs);
const long = env.tgCalls.filter(c => c.p && c.p.reply_markup && c.p.reply_markup.inline_keyboard).flatMap(c => c.p.reply_markup.inline_keyboard.flat()).filter(b => b.callback_data && Buffer.byteLength(b.callback_data) > 64);
ok(!long.length, 'دکمه بلندتر از ۶۴ بایت: ' + long.map(b => b.callback_data).join(','));
ok(!errs().length, 'خطا در Logs: ' + errs().join(' | '));
console.log('scenario_span: ' + pass + '/' + (pass + fail));
if (fail) process.exitCode = 1;
