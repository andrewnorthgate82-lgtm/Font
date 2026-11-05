// سناریو: امتیاز ۱ تا ۵ بعد از تایید/رد در گروه ← سطر امتیاز در پیام همکار (ویرایش همان پیام) ← اصلاح امتیاز ← ارسال مجدد ← نمره ماهانه
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Admins', { telegram_id: '501', full_name: 'ادمین۲', role: 'admin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '29', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: 700, first_name: 'x' }, chat: { id: 700, type: 'private' }, date: 1 }, text === null ? {} : { text }, extra || {}) }); }
function cb(data, from, chat, msgId, text) { post({ callback_query: { id: 'c' + uid, from: { id: from || 700, first_name: from === 501 ? 'مریم' : 'علی' }, data, message: { message_id: msgId || 9, text, chat: { id: chat || from || 700, type: chat ? 'supergroup' : 'private' } } } }); }
const since = n => env.tgCalls.slice(n);
function errs() { return env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':')); }
function answer(calls) { const a = calls.filter(c => c.method === 'answerCallbackQuery').pop(); return a ? a.p.text : ''; }
// آخرین متن/کیبورد پیام بررسی در گروه
function info(rid) {
  const id = +C.getReport_(rid).review_info_id;
  const e = env.tgCalls.filter(c => c.method === 'editMessageText' && c.p.chat_id == -100500 && c.p.message_id === id).pop();
  return { id, text: e ? e.p.text : '', kb: e && e.p.reply_markup ? e.p.reply_markup.inline_keyboard.flat().map(b => b.callback_data + '=' + b.text) : [] };
}
function submit(type) { // گزارش «تولیدات» (کوتاه‌ترین فرم)
  delete env.cache.rl_700; C.clearSession_(700, 700); msg('📝 ارسال گزارش');
  msg(null, { photo: [{ file_id: 'f' + mid, file_unique_id: 'u' + mid }] }); cb('pd'); cb('ct:4'); cb('sb:2'); cb('tn'); msg('حامد کمال'); msg('موضوع ' + type);
  cb('ps');
  return Math.max(...C.readTable_('Reports').map(r => +r.report_id));
}

// ---------- تایید ← دکمه‌های امتیاز روی همان پیام بررسی ----------
const r1 = submit('یک');
let n = env.tgCalls.length;
cb('ok:' + r1, 500, -100500, +C.getReport_(r1).review_info_id);
let I = info(r1);
ok(I.text.includes('✅ تایید و منتشر شد') && I.text.endsWith('⭐️ امتیاز گزارش (۱ ضعیف تا ۵ عالی): هنوز ثبت نشده'), 'پیام بررسی: نتیجه + سطر امتیاز:\n' + I.text);
ok(I.kb.join(' ') === `sc:${r1}:1=1 sc:${r1}:2=2 sc:${r1}:3=3 sc:${r1}:4=4 sc:${r1}:5=5`, 'دکمه‌های ۱ تا ۵: ' + I.kb.join(' '));
const notice = since(n).filter(c => c.method === 'sendMessage' && c.p.chat_id == 700).pop();
ok(notice && /تایید و در کانال منتشر شد/.test(notice.p.text) && !/امتیاز/.test(notice.p.text), 'پیام همکار هنوز بدون امتیاز');
ok(+C.getReport_(r1).notice_msg_id > 0, 'شماره پیام همکار ذخیره شد');
const groupMsgsBefore = env.tgCalls.filter(c => c.method === 'sendMessage' && c.p.chat_id == -100500).length;
const userMsgsBefore = env.tgCalls.filter(c => c.method === 'sendMessage' && c.p.chat_id == 700).length;

// غیرادمین
n = env.tgCalls.length; cb('sc:' + r1 + ':4', 700, -100500, I.id, I.text);
ok(!C.getReport_(r1).score && /دسترسی/.test(answer(since(n))), 'غیرادمین نمی‌تواند امتیاز بدهد');
// ادمین ← ۲
n = env.tgCalls.length; cb('sc:' + r1 + ':2', 501, -100500, I.id, I.text);
ok(+C.getReport_(r1).score === 2 && +C.getReport_(r1).score_by === 501, 'امتیاز ۲ ثبت شد');
ok(/امتیاز 2 از 5 ثبت شد/.test(answer(since(n))), 'پاسخ دکمه');
I = info(r1);
ok(I.text.endsWith('⭐️ امتیاز گزارش: 2 از 5 — توسط مریم') && !I.text.includes('هنوز ثبت نشده') && I.kb.includes(`sc:${r1}:2=✅ 2`), 'پیام بررسی به‌روز شد:\n' + I.text);
let ue = since(n).find(c => c.method === 'editMessageText' && c.p.chat_id == 700);
ok(ue && ue.p.message_id === +C.getReport_(r1).notice_msg_id && ue.p.text === '✅ گزارش شما تایید و در کانال منتشر شد 🎉\nموضوع: موضوع یک\n⭐️ امتیاز گزارش: 2 از 5', 'همان پیام همکار ویرایش شد:\n' + (ue && ue.p.text));
// اصلاح ← ۴
n = env.tgCalls.length; cb('sc:' + r1 + ':4', 500, -100500, I.id, I.text);
I = info(r1);
ok(+C.getReport_(r1).score === 4 && I.text.endsWith('⭐️ امتیاز گزارش: 4 از 5 — توسط علی') && (I.text.match(/⭐️/g) || []).length === 1, 'اصلاح امتیاز (یک سطر):\n' + I.text);
// همان عدد دوباره
n = env.tgCalls.length; cb('sc:' + r1 + ':4', 500, -100500, I.id, I.text);
ok(/از قبل ثبت است/.test(answer(since(n))) && !since(n).some(c => c.method === 'editMessageText'), 'همان عدد ← بدون ویرایش');
ok(env.tgCalls.filter(c => c.method === 'sendMessage' && c.p.chat_id == -100500).length === groupMsgsBefore &&
  env.tgCalls.filter(c => c.method === 'sendMessage' && c.p.chat_id == 700).length === userMsgsBefore, 'هیچ پیام تازه‌ای (نه در گروه، نه برای همکار)');

// ---------- رد ← امتیاز ← دکمه اصلاح همکار می‌ماند ----------
const r2 = submit('دو');
const i2 = +C.getReport_(r2).review_info_id;
cb('rj:' + r2, 500, -100500, i2); cb('rs:' + r2 + ':2', 500, -100500, i2); cb('rd:' + r2, 500, -100500, i2);
I = info(r2);
ok(C.getReport_(r2).status === 'rejected' && I.text.includes('❌ رد شد') && I.kb.length === 5, 'رد ← دکمه‌های امتیاز');
n = env.tgCalls.length; cb('sc:' + r2 + ':1', 500, -100500, I.id, I.text);
ue = since(n).find(c => c.method === 'editMessageText' && c.p.chat_id == 700);
ok(ue && ue.p.text.startsWith('❌ گزارش شما رد شد.\nموضوع: موضوع دو\n⭐️ امتیاز گزارش: 1 از 5\n\nدلیل‌ها:\n• نداشتن نام مدرس') &&
  ue.p.reply_markup.inline_keyboard[0][0].callback_data === 're:' + r2, 'پیام رد همکار: امتیاز + دلیل + دکمه اصلاح:\n' + (ue && ue.p.text));

// ---------- ارسال مجدد ← امتیاز پاک ← دکمه کهنه غیرفعال ----------
delete env.cache.rl_700; C.clearSession_(700, 700); cb('re:' + r2); cb('ps');
ok(C.getReport_(r2).status === 'pending' && C.getReport_(r2).score === '' && C.getReport_(r2).notice_msg_id === '', 'ارسال مجدد ← امتیاز پاک شد');
n = env.tgCalls.length; cb('sc:' + r2 + ':5', 500, -100500, I.id, I.text);
ok(C.getReport_(r2).score === '' && /قابل امتیازدهی نیست/.test(answer(since(n))) && since(n).some(c => c.method === 'editMessageReplyMarkup'), 'دکمه امتیاز پیام قبلی غیرفعال و برداشته شد');
const i2b = +C.getReport_(r2).review_info_id;
cb('ok:' + r2, 500, -100500, i2b); I = info(r2); cb('sc:' + r2 + ':5', 500, -100500, I.id, I.text);
ok(+C.getReport_(r2).score === 5, 'گزارش اصلاح‌شده امتیاز تازه گرفت');

// ---------- کارت پنل + گزارش‌های من + آمار ماهانه ----------
ok(C.reportCardText_(C.getReport_(r1)).includes('⭐️ امتیاز: 4 از 5'), 'کارت پنل');
n = env.tgCalls.length; msg('📋 گزارش‌های من');
const my = since(n).filter(c => c.p.chat_id == 700 && c.p.text).pop().p.text;
ok(my.includes('موضوع یک') && my.includes('⭐️ 4 از 5') && /⭐️ نمره شهرستان شما در گزارش .* تا این لحظه: 0.9 از 5 \(1 بخش از 5 کار شده\)/.test(my), 'گزارش‌های من:\n' + my);
const t = C.currentPeriod_();
const stt = C.monthStatsText_(C.readTable_('Reports'), t.jy, t.jm);
ok(stt.includes('🏆 نجف آباد: ⭐️ 0.9 از 5 — 2 گزارش') && stt.includes('1 بخش از 5، میانگین امتیاز 4.5') && stt.includes('⭐️ نمره ماه = میانگین'), 'آمار ماهانه:\n' + stt);
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_score: ' + pass + '/' + (pass + fail));
