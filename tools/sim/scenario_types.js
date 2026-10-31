// سناریو: فرم کامل هر نوع ← ارسال ← تایید در گروه ← کپشن کانال ؛ ویرایش نوع از پیش‌نمایش ؛ اصلاحیه گزارش قدیمی ؛ ویرایش پنل
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '29', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: 700, first_name: 'x' }, chat: { id: 700, type: 'private' }, date: 1 }, text === null ? {} : { text }, extra || {}) }); }
function cb(data, from, chat) { post({ callback_query: { id: 'c' + uid, from: { id: from || 700 }, data, message: { message_id: 9, chat: { id: chat || from || 700, type: chat ? 'supergroup' : 'private' } } } }); }
const st = () => C.getSession_(700, 700);
function lastText() { for (let i = env.tgCalls.length - 1; i >= 0; i--) { const p = env.tgCalls[i].p; if (p.text && p.chat_id == 700 || (env.tgCalls[i].method === 'editMessageText' && p.text)) return p.text; } return ''; }
function kb() { for (let i = env.tgCalls.length - 1; i >= 0; i--) { const p = env.tgCalls[i].p; if (p.reply_markup && p.reply_markup.inline_keyboard && (p.chat_id == 700)) return p.reply_markup.inline_keyboard.flat().map(b => b.callback_data + '=' + b.text); } return []; }
function errs() { return env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':')); }
function start() { delete env.cache.rl_700; C.clearSession_(700, 700); msg('📝 ارسال گزارش'); msg(null, { photo: [{ file_id: 'f' + mid, file_unique_id: 'u' + mid }] }); cb('pd'); }
function approveLast() {
  const rid = Math.max(...C.readTable_('Reports').map(r => +r.report_id));
  const n = env.tgCalls.length; cb('ok:' + rid, 500, -100500);
  const pub = env.tgCalls.slice(n).find(c => (c.method === 'sendPhoto' || c.method === 'sendMediaGroup') && c.p.chat_id == -100600);
  return { rid, caption: pub ? (pub.p.caption || pub.p.media[0].caption) : '', rep: C.getReport_(rid) };
}

// ---------- حضوری ----------
start(); ok(/قدم ۲ — نوع کلاس/.test(lastText()), 'قدم ۲ بدون تعداد کل: ' + lastText().slice(0, 30));
cb('ct:0'); ok(/^قدم ۳ از ۱۱ — نام عملیات/.test(lastText()), 'حضوری قدم ۳ از ۱۱');
msg('بصیرت'); cb('sj:0'); cb('tn'); msg('حامد کمال'); cb('dq:-1'); cb('th:18'); cb('tm:30'); cb('du:60'); msg('۴۰'); cb('au:0');
ok(/^قدم ۱۱ از ۱۱ — بستر \/ مکان/.test(lastText()), 'قدم ۱۱ مکان');
msg('مسجد جامع\nسالن اجتماعات'); ok(st().state === 'form:preview', 'حضوری ← پیش‌نمایش');
cb('ps'); let a = approveLast();
ok(a.caption.startsWith('📅 تاریخ درج در کانال: ') && a.caption.includes('⭕️ نوع کلاس/ برنامه: #حضوری\n💢 نام عملیات: بصیرت') && a.caption.includes('🏫 بستر / مکان برگزاری:\nمسجد جامع\nسالن اجتماعات') && a.caption.includes('📍 ناحیه برگزار کننده: #نجف_آباد'), 'کپشن کانال حضوری:\n' + a.caption);
ok(a.rep.form_v === '2' && a.rep.class_sub === '' && a.rep.link === '', 'ردیف حضوری form_v=2');

// ---------- مجازی ----------
start(); cb('ct:1'); ok(/^قدم ۳ از ۱۰ — موضوع/.test(lastText()), 'مجازی قدم ۳ از ۱۰ موضوع');
cb('sj:2'); cb('tn'); msg('زهرا احمدی'); cb('dq:0');
ok(/^قدم ۶ از ۱۰ — مدت/.test(lastText()), 'مجازی بدون ساعت ← مدت: ' + lastText().slice(0, 25));
cb('du:90'); msg('120'); cb('au:1');
ok(kb().includes('pf:0=اسکای روم'), 'دکمه‌های بستر برگزاری');
cb('pf:0'); ok(/لینک برگزاری/.test(lastText()) && kb().includes('lk0=ندارد'), 'قدم لینک');
msg('سلام'); ok(st().state === 'form:link' && /معتبر نیست/.test(lastText()), 'لینک نامعتبر رد شد');
msg('https://www.skyroom.online/ch/nasra/room1'); ok(st().state === 'form:preview', 'مجازی ← پیش‌نمایش');
cb('ps'); a = approveLast();
ok(a.caption.includes('⭕️ نوع کلاس: #مجازی') && a.caption.includes('💻 بستر برگزاری: اسکای روم') && a.caption.endsWith('🔗 لینک برگزاری: https://www.skyroom.online/ch/nasra/room1') && !a.caption.includes('ساعت') && !a.caption.includes('عملیات'), 'کپشن مجازی:\n' + a.caption);
ok(a.rep.event_time === '' && a.rep.operation_name === '', 'ستون‌های غیرمرتبط خالی');

// ---------- توانمندسازی مجازی ----------
start(); cb('ct:2'); ok(/^قدم ۳ از ۱۲ — کلاس توانمندسازی حضوری/.test(lastText()) && kb().includes('sb:1=مجازی'), 'توانمندسازی: حضوری یا مجازی');
cb('sb:1'); ok(/^قدم ۴ از ۱۲ — موضوع/.test(lastText()), 'توانمندسازی مجازی ← موضوع');
cb('sj:1'); cb('tn'); msg('حامد کمال'); cb('dq:0'); cb('th:20'); cb('tm:00'); cb('du:120'); msg('25'); cb('au:1'); msg('ایتا'); cb('lk0');
ok(st().state === 'form:preview', 'توانمندسازی مجازی ← پیش‌نمایش');
// تغییر حضوری/مجازی از پیش‌نمایش: فقط بخش‌های تازه پرسیده شود
cb('pe'); ok(kb().some(x => x.startsWith('ef:sub=')) && kb().some(x => x.startsWith('ef:link=')) && !kb().some(x => x.startsWith('ef:operation')), 'دکمه‌های ویرایش همین نوع');
cb('ef:sub'); cb('sb:0'); ok(st().state === 'form:operation', 'به حضوری ← اول نام عملیات (ناقص)');
cb('op0'); ok(st().state === 'form:place', 'بعد مکان (ناقص)؛ بقیه حفظ شد');
msg('پایگاه شهید فهمیده'); ok(st().state === 'form:preview', 'دوباره پیش‌نمایش');
cb('ps'); a = approveLast();
ok(a.caption.includes('⭕️ نوع کلاس/ برنامه: #توانمندسازی (حضوری)') && a.caption.includes('⏰ ساعت برگزاری: 20:00') && a.caption.includes('🏫 بستر / مکان برگزاری: پایگاه شهید فهمیده') && !a.caption.includes('لینک'), 'کپشن توانمندسازی حضوری:\n' + a.caption);
ok(a.rep.class_sub === 'حضوری' && a.rep.link === '', 'ردیف توانمندسازی');

// ---------- خلاقانه ----------
start(); cb('ct:3'); ok(/^قدم ۳ از ۸ — فعالیت خلاقانه/.test(lastText()) && kb().includes('sb:0=انتشار شبکه اجتماعی') && kb().includes('sb:2=بروشور') && kb().includes('sb:6=صدا و سیما'), 'هفت بخش خلاقانه: ' + kb().join(' '));
cb('sb:0'); cb('sj:3'); ok(/^قدم ۵ از ۸ — تاریخ انتشار/.test(lastText()), 'تاریخ انتشار');
cb('dq:-2'); ok(/تعداد بازدید/.test(lastText()), 'تعداد بازدید');
msg('۱۲٬۵۰۰'); ok(st().data.participants === 12500, 'عدد با جداکننده فارسی');
ok(kb().includes('pf:0=اینستاگرام'), 'شبکه‌های اجتماعی');
cb('pf:0'); msg('https://instagram.com/p/xyz'); ok(st().state === 'form:preview', 'خلاقانه ← پیش‌نمایش');
// تغییر بخش به «چاپ بنر»: بستر نوشتنی است ولی مقدار قبلی می‌ماند
cb('pe'); cb('ef:sub'); cb('sb:1'); ok(st().state === 'form:preview', 'زیرشاخه خلاقانه عوض شد ← مستقیم پیش‌نمایش');
cb('ps'); a = approveLast();
ok(a.caption.includes('⭕️ نوع فعالیت: #خلاقانه #چاپ_بنر') && a.caption.includes('👁 تعداد بازدید: 12500') && a.caption.includes('📢 بستر انتشار: اینستاگرام') && a.caption.includes('🔗 لینک پست: https://instagram.com/p/xyz') && !a.caption.includes('مدرس'), 'کپشن خلاقانه:\n' + a.caption);

// ---------- تولیدات ----------
start(); cb('ct:4'); ok(/^قدم ۳ از ۵ — نوع محتوا/.test(lastText()) && kb().includes('sb:0=صفحه word') && kb().includes('sb:6=سایر'), 'نوع محتوا');
cb('sb:3'); ok(/^قدم ۴ از ۵ — تولید کننده/.test(lastText()) && kb().some(x => /=حامد کمال$/.test(x)) && kb().includes('tn=➕ تولید کننده جدید'), 'فهرست مدرسان برای تولید کننده');
const te = kb().find(x => /=حامد کمال$/.test(x)).split('=')[0]; cb(te);
ok(/^قدم ۵ از ۵ — موضوع محتوا/.test(lastText()), 'موضوع محتوا');
msg('سواد رسانه برای والدین'); ok(st().state === 'form:preview', 'تولیدات ← پیش‌نمایش (بدون تاریخ)');
cb('ps'); a = approveLast();
ok(a.caption.includes('⭕️ نوع فعالیت: #تولیدات\n👨‍🏫 تولید کننده: حامد کمال\n📄 موضوع محتوا: سواد رسانه برای والدین\n🎞 نوع محتوا: #موشن_گرافیک') && a.caption.includes('🗓 ماه: #'), 'کپشن تولیدات:\n' + a.caption);
ok(+a.rep.event_year > 1400 && a.rep.participants === '' && a.rep.audience === '', 'تولیدات: تاریخ = روز ارسال');

// ---------- تغییر نوع از پیش‌نمایش: مجازی ← خلاقانه ----------
start(); cb('ct:1'); cb('sj:0'); const te2 = kb().find(x => /=حامد کمال$/.test(x)).split('=')[0]; cb(te2); cb('dq:0'); cb('du:30'); msg('10'); cb('au:0'); cb('pf:1'); cb('lk0');
ok(st().state === 'form:preview', 'مجازی آماده');
cb('pe'); cb('ef:class'); cb('ct:3'); ok(st().state === 'form:sub', 'نوع عوض شد ← زیرشاخه');
cb('sb:4'); ok(st().state === 'form:date', 'موضوع حفظ شد ← تاریخ انتشار (پاک شده بود)');
cb('dq:0'); msg('800'); ok(st().state === 'form:platform' && !kb().some(x => x.startsWith('pf:')), 'نمایشگاه: بستر فقط نوشتنی');
msg('نمایشگاه کتاب نجف آباد'); ok(st().state === 'form:link', 'لینک');
cb('lk0'); ok(st().state === 'form:preview', 'پیش‌نمایش');
const prevCap = env.tgCalls.filter(c => c.p.chat_id == 700 && (c.p.caption || (c.p.media && c.p.media[0].caption))).pop();
const pc = prevCap.p.caption || prevCap.p.media[0].caption;
ok(pc.includes('#خلاقانه #نمایشگاه') && !pc.includes('مدرس') && !pc.includes('مدت'), 'پیش‌نمایش فقط بخش‌های خلاقانه:\n' + pc);
cb('cx');

// ---------- دکمه کهنه ----------
let n = env.tgCalls.length; cb('sb:1'); const an = env.tgCalls.slice(n).find(c => c.method === 'answerCallbackQuery');
ok(an && /فعال نیست/.test(an.p.text), 'دکمه کهنه بعد از انصراف');

// ---------- اصلاحیه گزارش قدیمی (پیش از کپشن‌های جداگانه) ----------
const oldRid = C.createReport_({ submitter_telegram_id: 700, employee_name: 'همکار', city_id: 29, instructor_id: 1, instructor_name: 'حامد کمال', status: 'rejected',
  class_type: 'مجازی', operation_name: '', subject: 'قدیمی', event_jalali: '1405/08/01', event_gregorian: '2026-10-23', event_year: 1405, event_month: 8, event_day: 1,
  event_time: '20:00', duration_min: 60, participants: 30, audience: 'عموم', place: 'اسکای روم', publish_jalali: '', published_caption: '', channel_message_ids: '[]',
  review_chat_id: '', review_album_ids: '[]', review_info_id: '', decided_by: '', decided_at: '', resubmit_count: 0 }, [{ file_id: 'old1', file_unique_id: 'uo1' }]);
delete env.cache.rl_700; C.clearSession_(700, 700); cb('re:' + oldRid);
ok(st().state === 'form:link', 'گزارش قدیمی ← فقط لینک (بخش تازه) پرسیده می‌شود: ' + st().state);
cb('lk0'); ok(st().state === 'form:preview', 'بعد پیش‌نمایش');
cb('ps'); const rr = C.getReport_(oldRid); ok(rr.status === 'pending' && rr.form_v === '2' && rr.event_time === '' && +rr.resubmit_count === 1, 'اصلاحیه با قالب تازه ذخیره شد');

// ---------- ویرایش پنل: گزارش خلاقانه ----------
const cr = C.readTable_('Reports').find(r => r.class_type === 'خلاقانه' && r.status === 'approved');
cb('A:re:' + cr.report_id, 500);
const ekb = env.tgCalls.filter(c => c.p.chat_id == 500 && c.p.reply_markup).pop().p.reply_markup.inline_keyboard.flat().map(b => b.callback_data);
ok(ekb.includes('A:rx:' + cr.report_id + ':views') && ekb.includes('A:rx:' + cr.report_id + ':link') && !ekb.includes('A:rx:' + cr.report_id + ':teacher'), 'پنل: بخش‌های خلاقانه ' + ekb.join(' '));
cb('A:rx:' + cr.report_id + ':views', 500); post({ message: { message_id: mid++, from: { id: 500 }, chat: { id: 500, type: 'private' }, date: 1, text: '20000' } });
ok(C.getReport_(cr.report_id).published_caption.includes('👁 تعداد بازدید: 20000'), 'پنل: بازدید ویرایش شد');
const ed = env.tgCalls.filter(c => c.method === 'editMessageCaption').pop(); ok(ed && ed.p.caption.includes('20000'), 'کپشن کانال ویرایش شد');
cb('A:rx:' + cr.report_id + ':sub', 500); cb('A:rv:' + cr.report_id + ':sub:5', 500);
ok(C.getReport_(cr.report_id).published_caption.includes('#خلاقانه #تلویزیون_شهری'), 'پنل: بخش خلاقانه عوض شد');
cb('A:rx:' + cr.report_id + ':class', 500); cb('A:rv:' + cr.report_id + ':class:4', 500);
const skb = env.tgCalls.filter(c => c.p.chat_id == 500 && c.p.reply_markup).pop().p.reply_markup.inline_keyboard.flat().map(b => b.callback_data);
ok(skb.includes('A:rw:' + cr.report_id + ':4:2'), 'پنل: تغییر نوع به تولیدات ← انتخاب نوع محتوا');
cb('A:rw:' + cr.report_id + ':4:2', 500);
ok(C.getReport_(cr.report_id).published_caption.includes('#تولیدات') && C.getReport_(cr.report_id).published_caption.includes('🎞 نوع محتوا: #کلیپ'), 'پنل: نوع + زیرشاخه');
cb('A:rx:' + cr.report_id + ':operation', 500); ok(/این بخش در کپشن این نوع گزارش نیست/.test(JSON.stringify(env.tgCalls.slice(-2))), 'پنل: بخش نامرتبط رد شد');

// ---------- کارت گزارش + آمار + اکسل ----------
const card = C.reportCardText_(C.getReport_(cr.report_id)); ok(card.includes('نوع: تولیدات — کلیپ') && card.includes('تولید کننده'), 'کارت پنل نوع‌محور:\n' + card);
const t = C.currentPeriod_(); const stt = C.monthStatsText_(C.readTable_('Reports'), t.jy, t.jm); ok(/نفر شرکت‌کننده/.test(stt), 'آمار');
ok(errs().length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_types: ' + pass + '/' + (pass + fail));
