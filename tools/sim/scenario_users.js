// سناریو: پنل ← 👤 کاربران ← «📱 جستجو با شماره» یا «🏙 انتخاب ناحیه» (۳۲ ناحیه در یک صفحه) ← ۵ نقش ← فهرست ← کارت کاربر
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.setSetting_('setup_done', '1', '');
const U = (id, tg, name, city, pos, status) => C.appendRow_('Users', { user_id: String(id), telegram_id: String(tg), phone: '0913000' + String(tg).padStart(4, '0'), full_name: name, city_id: String(city), role: 'employee', status: status || 'approved', position: pos || '' });
U(1, 701, 'علی برادری', 29, 'فرمانده گردان برادر');
U(2, 702, 'حسن نسرایی', 29, 'مسئول نسرا برادر');
U(3, 703, 'زهرا خواهری', 29, 'فرمانده گردان خواهر');
U(4, 704, 'مریم مجازی', 29, 'مسئول فضای مجازی');
U(5, 705, 'رضا قدیمی', 29, '');
U(6, 706, 'بهرام دوم', 29, 'فرمانده گردان برادر', 'disabled');
U(7, 707, 'ردشده', 29, 'فرمانده گردان برادر', 'rejected');
U(8, 708, 'کاشانی', 5, 'فرمانده گردان برادر');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0, cbs = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text) { post({ message: { message_id: mid++, from: { id: 500, first_name: 'x' }, chat: { id: 500, type: 'private' }, date: 1, text } }); }
function cb(data) { cbs++; post({ callback_query: { id: 'c' + uid, from: { id: 500, first_name: 'ادمین' }, data, message: { message_id: 70, chat: { id: 500, type: 'private' } } } }); }
const since = n => env.tgCalls.slice(n);
const last = calls => calls.filter(c => c.p.chat_id == 500 && (c.method === 'sendMessage' || c.method === 'editMessageText')).pop() || { p: {} };
const kb = c => (c.p.reply_markup && c.p.reply_markup.inline_keyboard) || [];
const btns = c => kb(c).flat().map(b => b.callback_data + '=' + b.text);
const errs = () => env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':'));

// ۱) منوی کاربران: دو گزینه
let n = env.tgCalls.length; cb('A:u');
let q = last(since(n));
ok(/چطور پیدا کنیم/.test(q.p.text) && kb(q)[0][0].callback_data === 'A:uf' && kb(q)[1][0].callback_data === 'A:un', 'دو گزینه: ' + btns(q));
ok(!C.getSession_(500, 500).state, 'منو هنوز منتظر متن نیست');

// ۲) جستجو با شماره
n = env.tgCalls.length; cb('A:uf');
ok(C.getSession_(500, 500).state === 'adm:findUser' && /شماره موبایل را بفرستید/.test(last(since(n)).p.text), 'جستجو با شماره ← منتظر شماره');
n = env.tgCalls.length; msg('۰۹۱۳۰۰۰۰۷۰۲');
q = last(since(n));
ok(/1 کاربر پیدا شد/.test(q.p.text) && btns(q)[0].startsWith('A:uc:2=حسن نسرایی · نجف آباد'), 'یافتن با شماره فارسی: ' + btns(q));
ok(btns(q).some(b => b.startsWith('A:u=')), 'دکمه برگشت به کاربران');
n = env.tgCalls.length; msg('09999999999');
ok(/کاربری پیدا نشد/.test(last(since(n)).p.text) && btns(last(since(n))).some(b => b.startsWith('A:u=')), 'پیدا نشد + برگشت');

// ۳) انتخاب ناحیه ← همه ناحیه‌ها در یک صفحه
n = env.tgCalls.length; cb('A:un');
q = last(since(n));
const cityBtns = btns(q).filter(b => b.startsWith('A:uR:'));
ok(cityBtns.length === C.getCities_().length && cityBtns.length >= 32 && !btns(q).some(b => /قبلی|بعدی/.test(b)), 'همه ' + cityBtns.length + ' ناحیه در یک صفحه');
ok(cityBtns.includes('A:uR:29=نجف آباد (5)') && cityBtns.includes('A:uR:5=' + C.getCityById_(5).name + ' (1)'), 'تعداد کاربران فعال کنار نام: ' + cityBtns.filter(b => /\((?!0)/.test(b)));
ok(C.getSession_(500, 500).state === '', 'کلیک پنل حالت جستجو را لغو کرد');

// ۴) ناحیه ← ۵ نقش
n = env.tgCalls.length; cb('A:uR:29');
q = last(since(n));
const roleBtns = btns(q);
ok(roleBtns.slice(0, 5).join('|') === 'A:uP:29:0=مسئول فضای مجازی (1)|A:uP:29:3=فرمانده گردان برادر (2)|A:uP:29:4=مسئول نسرا برادر (1)|A:uP:29:1=فرمانده گردان خواهر (1)|A:uP:29:2=مسئول نسرا خواهر (0)', 'پنج نقش با تعداد: ' + roleBtns.join(' | '));
ok(roleBtns.includes('A:uP:29:x=❔ بدون نقش (1)') && /نجف آباد — 6 کاربر/.test(q.p.text), 'بدون نقش + جمع (ردشده شمرده نمی‌شود)');

// ۵) نقش ← فهرست کاربران
n = env.tgCalls.length; cb('A:uP:29:3');
q = last(since(n));
ok(/فرمانده گردان برادر/.test(q.p.text) && btns(q)[0] === 'A:uc:1=علی برادری · فعال ✅' && btns(q)[1] === 'A:uc:6=بهرام دوم · غیرفعال ⛔️' && !btns(q).some(b => /ردشده|کاشانی/.test(b)), 'فهرست نقش (فعال اول، بدون ردشده و ناحیه دیگر): ' + btns(q));
ok(btns(q).includes('A:uR:29=↩️ نقش دیگر') && btns(q).includes('A:un=🏙 ناحیه دیگر'), 'دکمه‌های برگشت');
n = env.tgCalls.length; cb('A:uP:29:2');
ok(/هیچ کاربری با این نقش/.test(last(since(n)).p.text), 'نقش خالی');
n = env.tgCalls.length; cb('A:uP:29:x');
ok(btns(last(since(n)))[0] === 'A:uc:5=رضا قدیمی · فعال ✅', 'بدون نقش');
n = env.tgCalls.length; cb('A:uP:29:9');
ok(/پیدا نشد/.test(last(since(n)).p.text), 'نقش نامعتبر');

// ۶) کارت کاربر ← تغییر نقش ← برگشت به همان ناحیه
n = env.tgCalls.length; cb('A:uc:1');
q = last(since(n));
ok(/علی برادری/.test(q.p.text) && btns(q).includes('A:uR:29=🏙 کاربران نجف آباد') && btns(q).includes('A:u=↩️ کاربران'), 'کارت: برگشت به ناحیه: ' + btns(q));
cb('A:uQ:1:4'); C.bustCache_();
n = env.tgCalls.length; cb('A:uR:29');
ok(btns(last(since(n))).includes('A:uP:29:3=فرمانده گردان برادر (1)') && btns(last(since(n))).includes('A:uP:29:4=مسئول نسرا برادر (2)'), 'تعدادها بعد از تغییر نقش به‌روز شد');

const datas = env.tgCalls.flatMap(c => kb(c).flat().map(b => b.callback_data)).filter(Boolean);
ok(datas.every(d => Buffer.byteLength(d) <= 64), 'همه دکمه‌ها ≤ ۶۴ بایت');
ok(env.tgCalls.filter(c => c.method === 'answerCallbackQuery').length >= cbs, 'همه callbackها پاسخ گرفتند');
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_users: ' + pass + '/' + (pass + fail));
