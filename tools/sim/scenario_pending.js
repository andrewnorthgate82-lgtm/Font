// سناریو: یادآوری روزانه گزارش‌های در انتظار در گروه ← 🔗 لینک پیام بررسی / 📨 ارسال دوباره ← تایید از پیام تازه
// + همان دکمه‌ها در کارت گزارشِ پنل
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '29', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0, cbs = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: 700, first_name: 'x' }, chat: { id: 700, type: 'private' }, date: 1 }, text === null ? {} : { text }, extra || {}) }); }
function cb(data, from, chat, msgId, markup) { cbs++; post({ callback_query: { id: 'c' + uid, from: { id: from, first_name: 'علی' }, data, message: { message_id: msgId || 9, reply_markup: markup, chat: { id: chat || from, type: chat && chat < 0 ? 'supergroup' : 'private' } } } }); }
const since = n => env.tgCalls.slice(n);
const answer = calls => { const a = calls.filter(c => c.method === 'answerCallbackQuery').pop(); return a ? (a.p.text || '') : ''; };
const errs = () => env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':'));
function submit(subject) { // گزارش «تولیدات» (کوتاه‌ترین فرم)
  delete env.cache.rl_700; C.clearSession_(700, 700); msg('📝 ارسال گزارش');
  msg(null, { photo: [{ file_id: 'f' + mid, file_unique_id: 'u' + mid }] }); cb('pd', 700); cb('ct:4', 700); cb('sb:2', 700); cb('tn', 700); msg('حامد کمال'); msg(subject);
  cb('ps', 700);
  return Math.max(...C.readTable_('Reports').map(r => +r.report_id));
}
const r1 = submit('موضوع یک'), r2 = submit('موضوع دو'), r3 = submit('موضوع سه');
C.updateReport_(r2, { review_chat_id: '-100999' });                           // گروه عوض شده
C.updateReport_(r3, { review_chat_id: '', review_info_id: '', subject: '' });  // هیچ‌وقت به گروه نرفته + موضوع خالی
const col = C.SHEETS.Reports.indexOf('updated_at');
[r1, r2, r3].forEach(id => { env.sheets.Reports.data[C.getReport_(id)._row - 1][col] = '2020-01-01 10:00:00'; }); // updateReport_ زمان را خودش به‌روز می‌کند
C.bustCache_();
const info1 = +C.getReport_(r1).review_info_id;

// ---------- یادآوری در گروه ----------
let n = env.tgCalls.length;
ok(C.pendingAlert_(C.readTable_('Reports')) === 3, 'سه گزارش عقب‌افتاده');
const alert = since(n).find(c => c.method === 'sendMessage' && c.p.chat_id == -100500);
const kb = alert.p.reply_markup.inline_keyboard;
ok(/• #\d+ — نجف آباد — موضوع یک\n/.test(alert.p.text) && alert.p.text.includes('موضوع دو ⚠️ پیامش در این گروه نیست') && alert.p.text.includes('— تولیدات ⚠️ پیامش در این گروه نیست'), 'متن یادآوری:\n' + alert.p.text);
ok(kb[0][0].url === 'https://t.me/c/500/' + info1 && kb[0][1].callback_data === 'rq:' + r1, 'ردیف ۱: لینک + ارسال دوباره ' + JSON.stringify(kb[0]));
ok(kb[1].length === 1 && kb[1][0].callback_data === 'rq:' + r2 && kb[2].length === 1 && kb[2][0].callback_data === 'rq:' + r3, 'بدون پیام در گروه ← فقط ارسال دوباره');

// ---------- 📨 ارسال دوباره از یادآوری ----------
n = env.tgCalls.length; cb('rq:' + r3, 701, -100500, 300, alert.p.reply_markup);
ok(/دسترسی/.test(answer(since(n))) || answer(since(n)) === C.TXT.NO_ACCESS, 'غیرادمین نمی‌تواند');
n = env.tgCalls.length; cb('rq:' + r3, 500, -100500, 300, alert.p.reply_markup);
let calls = since(n), rep3 = C.getReport_(r3);
ok(calls.some(c => c.method === 'sendPhoto' || c.method === 'sendMediaGroup') && +rep3.review_info_id > 0 && rep3.review_chat_id == -100500, 'گزارش ۳ دوباره به گروه رفت');
const newInfo = calls.find(c => c.method === 'sendMessage' && c.p.chat_id == -100500 && c.p.reply_markup && JSON.stringify(c.p.reply_markup).includes('ok:' + r3));
ok(!!newInfo, 'پیام تازه با دکمه‌های تایید/رد');
const ed = calls.find(c => c.method === 'editMessageReplyMarkup' && c.p.message_id === 300);
ok(ed && ed.p.reply_markup.inline_keyboard.length === 2 && !JSON.stringify(ed.p.reply_markup).includes('rq:' + r3), 'ردیف همان گزارش از یادآوری برداشته شد');
ok(/دوباره فرستاده شد/.test(answer(calls)), 'پاسخ: ' + answer(calls));
n = env.tgCalls.length; cb('rq:' + r3, 500, -100500, 300);
ok(/همین الان/.test(answer(since(n))) && !since(n).some(c => c.method === 'sendMessage'), 'کلیک دوباره ← تکراری فرستاده نمی‌شود');

// گزارش ۱ (پیامش هست): ارسال دوباره ← پیام قبلی دکمه‌هایش را از دست می‌دهد
n = env.tgCalls.length; cb('rq:' + r1, 500, -100500, 300);
calls = since(n);
const oldEdit = calls.find(c => c.method === 'editMessageText' && c.p.message_id === info1);
ok(oldEdit && /دوباره در پایین گروه فرستاده شد/.test(oldEdit.p.text) && oldEdit.p.reply_markup.inline_keyboard.length === 0, 'پیام قبلی: بدون دکمه + توضیح');
const info1b = +C.getReport_(r1).review_info_id;
ok(info1b !== info1, 'شماره پیام بررسی تازه ذخیره شد');
// تایید از پیام تازه
n = env.tgCalls.length; cb('ok:' + r1, 500, -100500, info1b);
ok(C.getReport_(r1).status === 'approved' && since(n).some(c => c.method === 'editMessageText' && c.p.message_id === info1b && /تایید و منتشر شد/.test(c.p.text)), 'تایید از پیام تازه');
n = env.tgCalls.length; cb('rq:' + r1, 500, -100500, 300);
ok(/دیگر در انتظار بررسی نیست/.test(answer(since(n))), 'گزارش تاییدشده دوباره فرستاده نمی‌شود');

// ---------- پنل: کارت گزارش در انتظار ----------
n = env.tgCalls.length; cb('A:p', 500);
let pl = since(n).filter(c => c.p.chat_id == 500).pop();
ok(pl.p.text.includes('موضوع دو') && pl.p.text.includes('⚠️ پیامش در گروه نیست') && pl.p.text.includes('📨 ارسال دوباره'), 'فهرست در انتظار پنل:\n' + pl.p.text);
n = env.tgCalls.length; cb('A:r:' + r2, 500);
let card = since(n).filter(c => c.p.chat_id == 500).pop();
let cbtn = card.p.reply_markup.inline_keyboard.flat();
ok(card.p.text.includes('پیام بررسی این گزارش در گروه فعلی نیست') && cbtn[0].callback_data === 'A:rq:' + r2 && !cbtn[0].url, 'کارت: ارسال دوباره ' + JSON.stringify(cbtn[0]));
n = env.tgCalls.length; cb('A:rq:' + r2, 500);
calls = since(n);
ok(C.getReport_(r2).review_chat_id == -100500 && /دوباره در گروه بررسی فرستاده شد/.test(answer(calls)), 'پنل: ارسال دوباره');
card = calls.filter(c => c.p.chat_id == 500 && c.method === 'editMessageText').pop();
cbtn = card.p.reply_markup.inline_keyboard.flat();
ok(cbtn[0].url === 'https://t.me/c/500/' + C.getReport_(r2).review_info_id && !card.p.text.includes('در گروه فعلی نیست'), 'کارت به‌روز شد: حالا لینک دارد');

const datas = env.tgCalls.flatMap(c => ((c.p.reply_markup && c.p.reply_markup.inline_keyboard) || []).flat().map(b => b.callback_data)).filter(Boolean);
ok(datas.every(d => Buffer.byteLength(d) <= 64), 'همه دکمه‌ها ≤ ۶۴ بایت');
ok(env.tgCalls.filter(c => c.method === 'answerCallbackQuery').length >= cbs, 'همه callbackها پاسخ گرفتند');
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_pending: ' + pass + '/' + (pass + fail));
