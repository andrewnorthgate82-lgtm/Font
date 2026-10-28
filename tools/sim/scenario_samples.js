// سناریو: ادمین نمونه ثبت می‌کند (متن + آلبوم فوروارد شده از کانال)، همکار از منو دریافت می‌کند
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '5', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.appendRow_('Users', { user_id: '2', telegram_id: '800', phone: '0914', full_name: 'غریبه', city_id: '0', role: 'employee', status: 'pending' });
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(from, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: from, first_name: 'x' }, chat: { id: from, type: 'private' }, date: 1 }, extra) }); }
function cb(from, data) { post({ callback_query: { id: 'c' + uid, from: { id: from }, data, message: { message_id: 9, chat: { id: from, type: 'private' } } } }); }
function since(n) { return env.tgCalls.slice(n); }
function texts(calls) { return calls.map(c => c.p.text || c.p.caption || '').join(' | '); }
function errs() { return env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':')); }

// ۱) منو: برچسب تازه + قدیمی هر دو فرم را شروع می‌کنند
let n = env.tgCalls.length; msg(700, { text: '/start' });
const kb = since(n).map(c => c.p.reply_markup).filter(Boolean).map(r => JSON.stringify(r)).join('');
ok(kb.includes('📝 ارسال گزارش') && kb.includes('📚 نمونه گزارش‌ها') && !kb.includes('ثبت گزارش جدید'), 'منوی /start دکمه‌های تازه را دارد');
n = env.tgCalls.length; msg(700, { text: '📝 ارسال گزارش' }); ok(/ارسال گزارش/.test(texts(since(n))), 'دکمه تازه فرم را شروع می‌کند: ' + texts(since(n)).slice(0, 80));
C.clearSession_(700, 700);
n = env.tgCalls.length; msg(700, { text: '📝 ثبت گزارش جدید' }); ok(/ارسال گزارش/.test(texts(since(n))), 'کیبورد قدیمی هم کار می‌کند');
C.clearSession_(700, 700);

// ۲) همکار نمونه‌ها را باز می‌کند؛ هنوز چیزی ثبت نشده
n = env.tgCalls.length; msg(700, { text: '📚 نمونه گزارش‌ها' });
let mk = since(n).find(c => c.p.reply_markup && c.p.reply_markup.inline_keyboard);
ok(mk && JSON.stringify(mk.p.reply_markup).includes('sm:4'), 'پنج دکمه نوع برنامه');
n = env.tgCalls.length; cb(700, 'sm:0');
let ans = since(n).find(c => c.method === 'answerCallbackQuery');
ok(ans && /هنوز/.test(ans.p.text) && ans.p.show_alert, 'نمونه ثبت‌نشده ← هشدار');

// ۳) غریبه دسترسی ندارد
n = env.tgCalls.length; cb(800, 'sm:0'); ans = since(n).find(c => c.method === 'answerCallbackQuery');
ok(ans && /دسترسی/.test(ans.p.text), 'کاربر تاییدنشده دسترسی ندارد');
n = env.tgCalls.length; msg(800, { text: '📚 نمونه گزارش‌ها' }); ok(!since(n).some(c => c.p.reply_markup && JSON.stringify(c.p.reply_markup).includes('sm:')), 'منوی نمونه برای غریبه باز نمی‌شود');

// ۴) ادمین: پنل ← نمونه‌ها ← حضوری ← ثبت
n = env.tgCalls.length; msg(500, { text: '/admin' });
ok(JSON.stringify(since(n)).includes('A:e"'), 'دکمه نمونه گزارش‌ها در پنل');
cb(500, 'A:e'); cb(500, 'A:et:0'); n = env.tgCalls.length; cb(500, 'A:er:0');
ok(/ثبت نمونه «حضوری»/.test(texts(since(n))), 'راهنمای ثبت');
ok(C.getSession_(500, 500).state === 'adm:sample', 'حالت adm:sample');
// متن با لینک + آلبوم سه‌عکسی فوروارد شده از کانال (نامرتب) + یک گیف
n = env.tgCalls.length;
msg(500, { text: 'راهنمای نوشتن گزارش حضوری', entities: [{ type: 'bold', offset: 0, length: 6 }] });
const fwd = { forward_from_chat: { id: -1001, type: 'channel', title: 'نسرا' }, forward_origin: { type: 'channel', chat: { id: -1001, type: 'channel' } } };
mid += 2; msg(500, Object.assign({ media_group_id: 'AL', photo: [{ file_id: 'p3' }] }, fwd));
mid -= 3; msg(500, Object.assign({ media_group_id: 'AL', photo: [{ file_id: 'p1' }], caption: '#حضوری نمونه کپشن', caption_entities: [{ type: 'hashtag', offset: 0, length: 6 }, { type: 'custom_emoji', offset: 7, length: 1 }] }, fwd));
msg(500, Object.assign({ media_group_id: 'AL', photo: [{ file_id: 'p2' }] }, fwd));
mid += 2; msg(500, { animation: { file_id: 'g1' }, document: { file_id: 'g1' } });
const acks = since(n).filter(c => /دریافت شد/.test(c.p.text || ''));
ok(acks.length === 3, 'یک تایید برای متن، یکی برای کل آلبوم، یکی برای گیف (got ' + acks.length + ')');
ok(!since(n).some(c => /کانال/.test(c.p.text || '') && !/دریافت/.test(c.p.text || '')), 'فوروارد کانال به «افزودن کانال» نمی‌رود');
ok(C.getSession_(500, 500).data.items.length === 5, '۵ مورد در نشست');
// ذخیره
n = env.tgCalls.length; cb(500, 'A:es');
const sent = since(n).map(c => c.method);
ok(sent.includes('sendMediaGroup') && sent.includes('sendAnimation'), 'پیش‌نمایش پس از ذخیره: ' + sent.join(','));
const mg = since(n).find(c => c.method === 'sendMediaGroup');
ok(mg && mg.p.media.map(m => m.media).join() === 'p1,p2,p3', 'ترتیب آلبوم درست شد: ' + (mg && mg.p.media.map(m => m.media).join()));
ok(mg && mg.p.media[0].caption === '#حضوری نمونه کپشن' && mg.p.media[0].caption_entities.length === 1, 'کپشن و قالب (بدون ایموجی اختصاصی) روی عکس اول');
ok(C.getSession_(500, 500).state === '', 'حالت پاک شد');
ok(C.getSample_(0).items.length === 5, 'در برگه Samples ذخیره شد');
// ۵) همکار دریافت می‌کند
n = env.tgCalls.length; cb(700, 'sm:0');
const got = since(n);
ok(got.filter(c => c.p.chat_id === 700).map(c => c.method).join(',') === 'sendMessage,sendMessage,sendMediaGroup,sendAnimation', 'همکار: عنوان + متن + آلبوم + گیف: ' + got.map(c => c.method).join(','));
ok(got[0].method === 'answerCallbackQuery', 'callback پاسخ داده شد');
const tx = got.find(c => c.p.text === 'راهنمای نوشتن گزارش حضوری'); ok(tx && tx.p.entities[0].type === 'bold', 'قالب پررنگ متن حفظ شد');
// ۶) انصراف: نمونه قبلی دست نمی‌خورد
cb(500, 'A:er:0'); msg(500, { text: 'متن اشتباه' }); cb(500, 'A:et:0');
msg(500, { text: 'سلام' }); // دیگر در حالت ثبت نیست
ok(C.getSample_(0).items.length === 5, 'انصراف ← نمونه قبلی ماند');
// ۷) دستور وسط ثبت ضبط نمی‌شود
cb(500, 'A:er:1'); n = env.tgCalls.length; msg(500, { text: '/admin' });
ok(JSON.stringify(since(n)).includes('A:e"') && !/دریافت شد/.test(texts(since(n))), '/admin وسط ثبت پنل را باز می‌کند');
// ۸) سقف ۱۰ مورد
cb(500, 'A:er:2'); for (let i = 0; i < 11; i++) msg(500, { text: 'م' + i });
ok(C.getSession_(500, 500).data.items.length === 10, 'سقف ۱۰ مورد');
cb(500, 'A:es'); ok(C.getSample_(2).items.length === 10, 'ذخیره ۱۰ مورد توانمندسازی');
// ۹) حذف
cb(500, 'A:ed:2'); cb(500, 'A:eD:2'); ok(C.getSample_(2).items.length === 0, 'حذف شد');
n = env.tgCalls.length; cb(700, 'sm:2'); ok(/هنوز/.test(texts(since(n))), 'پس از حذف ← هنوز ثبت نشده');
// ۱۰) همکار نمی‌تواند دکمه‌های ادمین را بزند
n = env.tgCalls.length; cb(700, 'A:er:0'); ok(/دسترسی/.test(texts(since(n))), 'کارمند به A:er دسترسی ندارد');
// ۱۱) همه callbackها پاسخ گرفتند و خطایی ثبت نشد
const cbs = env.tgCalls.filter(c => c.method === 'answerCallbackQuery').length;
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
// ۱۲) یادآوری با برچسب تازه
ok(/ارسال گزارش/.test(C.MENU.NEW_REPORT), 'MENU');
console.log('scenario_samples: ' + pass + '/' + (pass + fail) + ' — callbacks answered: ' + cbs);
