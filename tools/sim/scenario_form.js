// سناریو: قدم ۱ (متن تازه)، قدم ۴ (پیشنهاد موضوع یا نوشتن)، مدرس جدید با قانون نام
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '5', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(from, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: from, first_name: 'x' }, chat: { id: from, type: 'private' }, date: 1 }, extra) }); }
function cb(from, data, m) { post({ callback_query: { id: 'c' + uid, from: { id: from }, data, message: { message_id: m || 9, chat: { id: from, type: 'private' } } } }); }
function since(n) { return env.tgCalls.slice(n); }
function texts(calls) { return calls.map(c => c.p.text || '').join(' | '); }
function kbData(calls) { return calls.filter(c => c.p.reply_markup && c.p.reply_markup.inline_keyboard).map(c => c.p.reply_markup.inline_keyboard.flat().map(b => b.callback_data + '=' + b.text).join(',')).join(' ; '); }
const st = () => C.getSession_(700, 700);

let n = env.tgCalls.length; msg(700, { text: '📝 ارسال گزارش' });
ok(texts(since(n)).includes('۱ تا ۵ عکس از برنامه را بفرستید (تکی یا آلبوم). حتماً بنر نماد نسرا به همراه موضوع و تاریخ در تصاویر باشد.\n\nوقتی تمام شد'), 'متن قدم ۱: ' + texts(since(n)));
msg(700, { photo: [{ file_id: 'a', file_unique_id: 'ua' }] });
cb(700, 'pd'); cb(700, 'ct:0'); n = env.tgCalls.length; cb(700, 'op0');
let k = kbData(since(n));
ok(/قدم ۴/.test(texts(since(n))) && k.includes('sj:0=سواد رسانه عمومی') && k.includes('sj:5=آموزش نرم افزارهای موبایلی') && k.includes('bk='), 'قدم ۴ با ۶ پیشنهاد + بازگشت: ' + k);
n = env.tgCalls.length; cb(700, 'sj:2');
ok(st().data.subject === 'جنگ شناختی' && st().state === 'form:teacher', 'انتخاب پیشنهاد ← قدم ۵');
ok(since(n).some(c => c.method === 'editMessageText' && /قدم ۵/.test(c.p.text)), 'همان پیام ویرایش شد');
n = env.tgCalls.length; cb(700, 'sj:1'); const a = since(n).find(c => c.method === 'answerCallbackQuery');
ok(a && /فعال نیست/.test(a.p.text) && st().data.subject === 'جنگ شناختی', 'دکمه کهنه موضوع اثری ندارد');
// بازگشت به قدم ۴ و نوشتن موضوع دلخواه
cb(700, 'bk'); ok(st().state === 'form:subject', 'بازگشت به قدم ۴');
msg(700, { text: 'کارگاه سواد رسانه ویژه والدین' });
ok(st().data.subject === 'کارگاه سواد رسانه ویژه والدین' && st().state === 'form:teacher', 'موضوع دلخواه نوشتنی هم پذیرفته می‌شود');
n = env.tgCalls.length; cb(700, 'tn');
ok(/حامد کمال/.test(texts(since(n))) && /دکتر حامد کمال/.test(texts(since(n))) && /لقب/.test(texts(since(n))), 'راهنمای مدرس جدید با مثال');
['دکتر کمال', 'آقای کمال', 'دکتر حامد کمال', 'کمال', 'سرهنگ رضا احمدی'].forEach(function (bad) {
  n = env.tgCalls.length; msg(700, { text: bad });
  ok(st().state === 'form:teacherNew' && /⚠️/.test(texts(since(n))) && /درست: حامد کمال/.test(texts(since(n))), 'رد شد: ' + bad + ' → ' + texts(since(n)).split('\n')[0]);
});
n = env.tgCalls.length; msg(700, { text: 'حامد کمال' });
ok(st().data.instructor_name === 'حامد کمال' && st().state === 'form:date', 'حامد کمال پذیرفته شد ← قدم تاریخ');
ok(C.readTable_('Instructors').some(r => r.name === 'حامد کمال') && !C.readTable_('Instructors').some(r => /دکتر/.test(r.name)), 'فقط نام درست در برگه Instructors');
// ادمین: افزودن مدرس با لقب رد می‌شود
const r1 = C.addInstructorAdmin_(5, 'دکتر رضا احمدی', 500); ok(!r1.ok && /دکتر/.test(r1.msg), 'پنل: افزودن با لقب رد شد');
const r2 = C.addInstructorAdmin_(5, 'رضا احمدی', 500); ok(r2.ok, 'پنل: افزودن درست');
const iid = C.readTable_('Instructors').find(r => r.name === 'رضا احمدی').instructor_id;
ok(!C.renameInstructor_(iid, 'مهندس رضا احمدی').ok, 'پنل: تغییر نام با لقب رد شد');
ok(C.renameInstructor_(iid, 'رضا احمدی‌نژاد').ok, 'پنل: تغییر نام درست');
const errs = env.sheets.Logs.data.filter(r => r[1] === 'error'); ok(!errs.length, 'بدون خطا ' + JSON.stringify(errs));
console.log('scenario_form: ' + pass + '/' + (pass + fail));
