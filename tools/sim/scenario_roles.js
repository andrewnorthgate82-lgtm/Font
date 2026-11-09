// سناریو: نقش در ثبت‌نام (دستی/لیست مجاز/نیمه‌کاره/همکار قدیمی) ← منوی نقش‌محور (مسئول فضای مجازی بدون «ارسال گزارش»)
// ← «📋 گزارش‌های من»: ماه ← (فضای مجازی: برادران/خواهران) ← عملکرد ← یادآوری بدون فضای مجازی ← تغییر نقش توسط ادمین
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0, cbs = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(from, text, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: from, first_name: 'x' }, chat: { id: from, type: 'private' }, date: 1 }, text === null ? {} : { text }, extra || {}) }); }
function contact(from, phone) { msg(from, null, { contact: { user_id: from, phone_number: phone } }); }
function cb(from, data, chat, msgId, text) { cbs++; post({ callback_query: { id: 'c' + uid, from: { id: from, first_name: 'علی' }, data, message: { message_id: msgId || 9, text, chat: { id: chat || from, type: chat ? 'supergroup' : 'private' } } } }); }
const since = n => env.tgCalls.slice(n);
const to = (calls, id) => calls.filter(c => c.p.chat_id == id && (c.method === 'sendMessage' || c.method === 'editMessageText'));
const last = (calls, id) => { const a = to(calls, id); return a.length ? a[a.length - 1] : { p: {} }; };
const btns = c => c && c.p.reply_markup && c.p.reply_markup.inline_keyboard ? c.p.reply_markup.inline_keyboard.flat().map(b => b.callback_data) : [];
const menuOf = calls => { const k = calls.filter(c => c.p.reply_markup && c.p.reply_markup.keyboard).pop(); return k ? k.p.reply_markup.keyboard.flat().map(b => b.text) : null; };
function answer(calls) { const a = calls.filter(c => c.method === 'answerCallbackQuery').pop(); return a ? (a.p.text || '') : ''; }
function errs() { return env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':')); }
const user = tg => { C.bustCache_(); return C.getUserByTg_(tg); };
const state = tg => C.getSession_(tg, tg).state;

// ---------- ۱) ثبت‌نام دستی: شماره ← شهرستان ← نام ← نقش ← گروه ادمین‌ها ----------
let n = env.tgCalls.length;
msg(800, '/start'); contact(800, '+989130000800');
ok(state(800) === 'reg:city', 'بعد از شماره: انتخاب شهرستان');
cb(800, 'rc:29'); msg(800, 'زهرا کریمی');
ok(state(800) === 'role:ask', 'بعد از نام: قدم نقش');
let q = last(since(n), 800);
ok(/آخرین قدم: نقش خود را/.test(q.p.text) && btns(q).join(',') === 'po:0,po:1,po:2,po:3,po:4', 'پنج دکمه نقش: ' + btns(q));
ok(!since(n).some(c => c.p.chat_id == -100500), 'قبل از انتخاب نقش، درخواستی به گروه نرفته');
n = env.tgCalls.length; msg(800, 'سلام');
ok(/با یکی از دکمه‌های زیر/.test(last(since(n), 800).p.text) && btns(last(since(n), 800)).length === 5, 'متن به‌جای دکمه نقش ← راهنما + دکمه‌ها');
n = env.tgCalls.length; cb(800, 'po:2', 0, 77);
ok(user(800).position === 'مسئول نسرا خواهر' && !state(800), 'نقش ذخیره شد و نشست پاک شد');
const grp = since(n).filter(c => c.p.chat_id == -100500).pop();
ok(grp && /نقش: مسئول نسرا خواهر/.test(grp.p.text) && /شماره: 09130000800/.test(grp.p.text), 'درخواست گروه با نقش:\n' + (grp && grp.p.text));
ok(since(n).some(c => c.method === 'editMessageText' && c.p.message_id === 77 && /🎖 نقش شما: مسئول نسرا خواهر ✅/.test(c.p.text)), 'پیام نقش ویرایش شد');
ok(since(n).some(c => c.p.chat_id == 800 && /درخواست ثبت‌نام شما ارسال شد/.test(c.p.text)), 'پیام «درخواست ارسال شد»');
n = env.tgCalls.length; cb(800, 'po:0', 0, 77);
ok(/دیگر فعال نیست/.test(answer(since(n))) && user(800).position === 'مسئول نسرا خواهر', 'دکمه نقش تکراری بی‌اثر');
n = env.tgCalls.length; cb(500, 'ga:' + user(800).user_id, -100500, 50, grp.p.text);
let ap = since(n).filter(c => c.p.chat_id == 800).pop();
ok(ap && /تایید شد/.test(ap.p.text) && /نقش: مسئول نسرا خواهر/.test(ap.p.text) && menuOf([ap]).includes('📝 ارسال گزارش'), 'تایید: منوی کامل');

// ---------- ۲) ثبت‌نام «مسئول فضای مجازی» ← منو بدون «ارسال گزارش» ----------
contact(801, '+989130000801'); cb(801, 'rc:29'); msg(801, 'مریم رضایی'); cb(801, 'po:0');
n = env.tgCalls.length; cb(500, 'ga:' + user(801).user_id, -100500, 51, 'x');
ap = since(n).filter(c => c.p.chat_id == 801).pop();
let menu = menuOf([ap]);
ok(menu && !menu.includes('📝 ارسال گزارش') && menu.includes('📋 گزارش‌های من') && /ارسال گزارش برای این نقش نیست/.test(ap.p.text), 'فضای مجازی: منو ' + menu);
for (const t of ['📝 ارسال گزارش', '📝 ثبت گزارش جدید']) {
  n = env.tgCalls.length; msg(801, t);
  ok(/ارسال گزارش برای این نقش نیست/.test(last(since(n), 801).p.text) && !state(801), 'دکمه قدیمی «' + t + '» مسدود');
}
n = env.tgCalls.length; msg(801, '/start');
ok(!menuOf(since(n)).includes('📝 ارسال گزارش') && /نقش: مسئول فضای مجازی/.test(last(since(n), 801).p.text), '/start: منوی بدون ارسال');

// ---------- ۳) نیمه‌کاره در قدم نقش ← /start از همان قدم ----------
contact(802, '+989130000802'); cb(802, 'rc:29'); msg(802, 'حسن نوری');
C.clearSession_(802, 802);
n = env.tgCalls.length; msg(802, '/start');
ok(state(802) === 'role:ask' && /نیمه‌کاره/.test(last(since(n), 802).p.text) && C.isHalfRegistered_(user(802)), 'نیمه‌کاره ← قدم نقش');
cb(802, 'po:3');
ok(user(802).position === 'فرمانده گردان برادر' && !C.isHalfRegistered_(user(802)), 'بعد از ارسال درخواست دیگر نیمه‌کاره نیست');
n = env.tgCalls.length; msg(802, '/start');
ok(/هنوز در انتظار تایید/.test(last(since(n), 802).p.text), 'در انتظار تایید');

// ---------- ۴) لیست مجاز: تایید فوری ← نقش ← منو ----------
C.appendRow_('Whitelist', { phone: '09130000803', full_name: 'رضا امینی', city_name: 'نجف آباد', city_id: '' }); C.bustCache_();
n = env.tgCalls.length; contact(803, '+989130000803');
ok(user(803).status === 'approved' && state(803) === 'role:ask' && /ثبت‌نام شما کامل شد/.test(since(n).filter(c => c.p.chat_id == 803)[0].p.text), 'لیست مجاز ← نقش');
n = env.tgCalls.length; cb(803, 'po:4');
ok(user(803).position === 'مسئول نسرا برادر' && menuOf(since(n)).includes('📝 ارسال گزارش') && !since(n).some(c => c.p.chat_id == -100500), 'لیست مجاز: منو، بدون درخواست گروه');

// ---------- ۵) همکار قدیمیِ تاییدشده بدون نقش ----------
C.appendRow_('Users', { user_id: '90', telegram_id: '804', phone: '0913804', full_name: 'قدیمی', city_id: '29', role: 'employee', status: 'approved', position: '' }); C.bustCache_();
n = env.tgCalls.length; msg(804, '/start');
ok(state(804) === 'role:ask' && btns(last(since(n), 804)).length === 5, 'قدیمی: /start ← نقش');
n = env.tgCalls.length; msg(804, '📋 گزارش‌های من');
ok(state(804) === 'role:ask', 'قدیمی: گزارش‌های من ← نقش');
n = env.tgCalls.length; cb(804, 'po:1');
ok(user(804).position === 'فرمانده گردان خواهر' && btns(last(since(n), 804))[0].startsWith('mr:'), 'بعد از نقش ← انتخاب ماه');
C.updateUser_(90, 804, { position: '' }); C.bustCache_();
n = env.tgCalls.length; msg(804, '📝 ارسال گزارش'); cb(804, 'po:0');
ok(/ارسال گزارش برای این نقش نیست/.test(since(n).filter(c => c.p.chat_id == 804).map(c => c.p.text).join('|')) && !state(804), 'نقش فضای مجازی از مسیر ارسال ← فرم شروع نمی‌شود');
C.updateUser_(90, 804, { position: '' }); C.bustCache_();
n = env.tgCalls.length; msg(804, '📝 ارسال گزارش'); cb(804, 'po:3');
ok(state(804) === 'form:photos', 'نقش برادر از مسیر ارسال ← فرم شروع شد');
C.clearSession_(804, 804);

// ---------- ۶) داده‌های گزارش ----------
const t = C.currentPeriod_(), pv = C.prevJalaliMonth_(t.jy, t.jm);
let rid = 100;
function rep(o) {
  const per = o.per || t;
  C.appendRow_('Reports', Object.assign({ report_id: String(++rid), submitter_telegram_id: '999', city_id: '29', status: 'approved', event_year: per.jy, event_month: per.jm, event_day: 10, participants: '0', class_sub: '' }, o, { per: undefined }));
}
const B = 'فرمانده گردان برادر', B2 = 'مسئول نسرا برادر', S = 'مسئول نسرا خواهر', S2 = 'فرمانده گردان خواهر', V = 'مسئول فضای مجازی';
rep({ class_type: '#حضوری', participants: '30', submitter_position: B });
rep({ class_type: '#حضوری', participants: '25', submitter_position: B2 });
rep({ class_type: '#مجازی', participants: '40', submitter_position: B });
rep({ class_type: '#توانمندسازی', class_sub: '#حضوری', participants: '12', submitter_position: B2 });
rep({ class_type: '#توانمندسازی', class_sub: '#مجازی', participants: '8', submitter_position: B });
rep({ class_type: '#خلاقانه', class_sub: '#ریلز', participants: '900', submitter_position: B });
rep({ class_type: '#تولیدات', class_sub: '#پوستر', submitter_position: B });
rep({ class_type: '#تولیدات', class_sub: '#پوستر', submitter_position: B2 });
rep({ class_type: '#تولیدات', class_sub: '#صفحه_word', submitter_position: B });
rep({ class_type: '#تولیدات', class_sub: '#سایر', submitter_position: B });
rep({ class_type: '#تولیدات', class_sub: '', submitter_position: B });           // فرم قدیمی بدون نوع ← سایر
rep({ class_type: '#حضوری', participants: '99', submitter_position: B, status: 'pending' });
rep({ class_type: '#حضوری', participants: '99', submitter_position: B, status: 'rejected' });
rep({ class_type: '#حضوری', participants: '99', submitter_position: B, city_id: '5' }); // ناحیه دیگر
rep({ class_type: '#حضوری', participants: '99', submitter_position: V });                // فضای مجازی ← هیچ‌کدام
rep({ class_type: '#حضوری', participants: '7', submitter_position: B, per: pv });        // ماه قبل
rep({ class_type: '#حضوری', participants: '15', submitter_position: S });
rep({ class_type: '#مجازی', participants: '22', submitter_position: S2 });
rep({ class_type: '#تولیدات', class_sub: '#کلیپ', submitter_position: S2 });
rep({ class_type: '#تولیدات', class_sub: '#موشن_گرافیک', submitter_position: S });
C.bustCache_();

// ---------- ۷) برادر: ماه ← مستقیم عملکرد برادران ----------
C.appendRow_('Users', { user_id: '91', telegram_id: '805', phone: '0913805', full_name: 'برادر', city_id: '29', role: 'employee', status: 'approved', position: B2 }); C.bustCache_();
n = env.tgCalls.length; msg(805, '📋 گزارش‌های من');
q = last(since(n), 805);
const kbRows = q.p.reply_markup.inline_keyboard;
ok(/ماه گزارش را انتخاب کنید/.test(q.p.text) && kbRows.length === 3 && kbRows.every(r => r.length === 2) && kbRows[0][0].callback_data === 'mr:' + t.jy + ':' + t.jm && kbRows[0][1].callback_data === 'mr:' + pv.jy + ':' + pv.jm, 'شش ماه، دو تا در هر ردیف');
n = env.tgCalls.length; cb(805, 'mr:' + t.jy + ':' + t.jm, 0, 40);
q = last(since(n), 805);
const exp = ['🏫 سواد رسانه عمومی:\nتعداد کلاس برگزار شده: 2\nتعداد نفرات آموزش داده شده: 55',
  '💻 کلاس‌های مجازی:\nتعداد کلاس برگزار شده: 1\nتعداد نفرات آموزش داده شده: 40',
  '💪 توانمندسازی گردان:\nتعداد کلاس برگزار شده: 2\nتعداد نفرات آموزش داده شده: 20',
  '🎨 سواد رسانه خلاقانه:\nتعداد نفرات آموزش داده شده: 900',
  '🎞 بخش تولیدات:\nپوستر: 2\nاینفوگرافیک: 0\nکلیپ: 0\nاسلاید: 0\nصفحه word: 1\nموشن گرافیک: 0\nسایر: 2',
  '⏳ در انتظار بررسی (هنوز حساب نشده): 1', '— برادران', '📍 ناحیه: نجف آباد', 'فرمانده گردان برادر و مسئول نسرا برادر'];
ok(q.method === 'editMessageText' && q.p.message_id === 40 && exp.every(x => q.p.text.includes(x)), 'عملکرد برادران:\n' + q.p.text);
ok(btns(q).join(',') === 'mr:m', 'برادر فقط «ماه دیگر» دارد: ' + btns(q));
n = env.tgCalls.length; cb(805, 'mr:' + t.jy + ':' + t.jm + ':s', 0, 40);
ok(/— برادران/.test(last(since(n), 805).p.text), 'برادر نمی‌تواند خواهران را ببیند');
n = env.tgCalls.length; cb(805, 'mr:' + pv.jy + ':' + pv.jm, 0, 40);
q = last(since(n), 805);
ok(q.p.text.includes('تعداد کلاس برگزار شده: 1\nتعداد نفرات آموزش داده شده: 7') && !/در انتظار بررسی/.test(q.p.text) && !/سایر/.test(q.p.text), 'ماه قبل:\n' + q.p.text);
n = env.tgCalls.length; cb(805, 'mr:m', 0, 40);
ok(last(since(n), 805).p.message_id === 40 && /ماه گزارش را انتخاب کنید/.test(last(since(n), 805).p.text), '«ماه دیگر» همان پیام را ویرایش می‌کند');
n = env.tgCalls.length; cb(805, 'mr:' + (t.jy + 1) + ':' + t.jm, 0, 40);
ok(/دیگر فعال نیست/.test(answer(since(n))), 'ماه آینده پذیرفته نمی‌شود');

// ---------- ۸) خواهر: عملکرد هر دو نقش خواهر ----------
n = env.tgCalls.length; cb(800, 'mr:' + t.jy + ':' + t.jm, 0, 41);
q = last(since(n), 800);
ok(q.p.text.includes('🏫 سواد رسانه عمومی:\nتعداد کلاس برگزار شده: 1\nتعداد نفرات آموزش داده شده: 15') && q.p.text.includes('کلیپ: 1') && q.p.text.includes('موشن گرافیک: 1') && q.p.text.includes('— خواهران'), 'عملکرد خواهران:\n' + q.p.text);

// ---------- ۹) فضای مجازی: ماه ← برادران/خواهران ----------
n = env.tgCalls.length; cb(801, 'mr:' + t.jy + ':' + t.jm, 0, 42);
q = last(since(n), 801);
ok(/کدام بخش/.test(q.p.text) && btns(q).join(',') === `mr:${t.jy}:${t.jm}:b,mr:${t.jy}:${t.jm}:s,mr:m`, 'انتخاب برادران/خواهران: ' + btns(q));
n = env.tgCalls.length; cb(801, `mr:${t.jy}:${t.jm}:s`, 0, 42);
q = last(since(n), 801);
ok(/— خواهران/.test(q.p.text) && btns(q).join(',') === `mr:${t.jy}:${t.jm}:b,mr:m`, 'خواهران + دکمه «برادران»');
n = env.tgCalls.length; cb(801, `mr:${t.jy}:${t.jm}:b`, 0, 42);
ok(last(since(n), 801).p.text.includes('نفرات آموزش داده شده: 55'), 'تعویض به برادران');

// ---------- ۱۰) یادآوری: مسئول فضای مجازی گیرنده نیست ----------
const tg = C.reminderTargets_([], C.readTable_('Users'), C.getCities_().filter(c => +c.city_id === 29), t.jy, t.jm);
const rec = tg.cities[0].recipients;
ok(rec.includes(800) && rec.includes(803) && !rec.includes(801), 'گیرندگان یادآوری: ' + rec);

// ---------- ۱۱) تغییر نقش توسط ادمین ← اطلاع + منوی تازه ----------
n = env.tgCalls.length; cb(500, 'A:uQ:' + user(800).user_id + ':0', 0, 60);
let nt = since(n).filter(c => c.p.chat_id == 800).pop();
ok(nt && /نقش شما توسط ادمین به «مسئول فضای مجازی» تغییر کرد/.test(nt.p.text) && !menuOf([nt]).includes('📝 ارسال گزارش'), 'اطلاع تغییر نقش + منوی بدون ارسال');
n = env.tgCalls.length; cb(500, 'A:uQ:' + user(800).user_id + ':0', 0, 60);
ok(!since(n).some(c => c.p.chat_id == 800), 'نقش تکراری ← پیام اضافه نمی‌رود');
n = env.tgCalls.length; cb(500, 'A:uQ:' + user(800).user_id + ':2', 0, 60);
ok(menuOf(since(n).filter(c => c.p.chat_id == 800)).includes('📝 ارسال گزارش'), 'برگشت نقش ← منوی کامل');
// نقش وسط فرم عوض شود ← ارسال نهایی پذیرفته نمی‌شود
C.updateUser_(user(800).user_id, 800, { position: V }); C.bustCache_();
const s = C.getSession_(800, 800); s.state = 'form:preview'; s.data = { photos: [] }; C.saveSession_(800, 800, s);
const before = C.readTable_('Reports').length;
n = env.tgCalls.length; cb(800, 'ps');
ok(C.readTable_('Reports').length === before && /ارسال گزارش برای این نقش نیست/.test(last(since(n), 800).p.text), 'ارسال نهایی برای فضای مجازی مسدود');

const answered = env.tgCalls.filter(c => c.method === 'answerCallbackQuery').length;
ok(answered >= cbs, 'همه callbackها پاسخ گرفتند: ' + answered + '/' + cbs);
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_roles: ' + pass + '/' + (pass + fail));
