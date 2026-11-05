// سناریو: پیوست‌های قدم ۱ (عکس، ویدیو، فایل، صوت، گیف) ← پیش‌نمایش ← گروه بررسی ← تایید ← کانال ← ویرایش/حذف پنل ← اصلاحیه
const { makeEnv } = require('./stubs');
const env = makeEnv(), C = env.ctx;
C.setup(); C.installPhase4(); env.props.GAS_KEY = 'K';
C.appendRow_('Admins', { telegram_id: '500', full_name: 'ادمین', role: 'superadmin', is_active: '1', added_at: 'x' });
C.appendRow_('Users', { user_id: '1', telegram_id: '700', phone: '0913', full_name: 'همکار', city_id: '29', role: 'employee', status: 'approved', position: 'مسئول نسرا خواهر' });
C.appendRow_('Instructors', { instructor_id: '1', city_id: '29', name: 'حامد کمال', name_norm: C.normalizeName_('حامد کمال'), is_active: '1', created_by: '700', created_at: 'x' });
C.setSetting_('review_group_id', '-100500', ''); C.setSetting_('channel_id', '-100600', ''); C.setSetting_('setup_done', '1', '');
C.bustCache_();
let uid = 1, mid = 1, pass = 0, fail = 0;
function ok(c, m) { if (c) pass++; else { fail++; console.log('❌ ' + m); } }
function post(u) { u.update_id = uid++; C.doPost({ parameter: { key: 'K' }, postData: { contents: JSON.stringify(u) } }); }
function msg(text, extra) { post({ message: Object.assign({ message_id: mid++, from: { id: 700, first_name: 'x' }, chat: { id: 700, type: 'private' }, date: 1 }, text === null ? {} : { text }, extra || {}) }); }
function cb(data, from, chat) { post({ callback_query: { id: 'c' + uid, from: { id: from || 700 }, data, message: { message_id: 9, chat: { id: chat || from || 700, type: chat ? 'supergroup' : 'private' } } } }); }
const st = () => C.getSession_(700, 700);
const since = n => env.tgCalls.slice(n);
const textsTo = (calls, chat) => calls.filter(c => c.p.chat_id == chat && c.p.text).map(c => c.p.text).join('\n---\n');
function errs() { return env.sheets.Logs.data.filter(r => r[1] === 'error').map(r => r.slice(2, 4).join(':')); }
const sendCalls = (calls, chat) => calls.filter(c => /^send(Photo|Video|Document|Audio|Voice|Animation|MediaGroup)$/.test(c.method) && c.p.chat_id == chat);
const capOf = c => c ? (c.p.caption || (c.p.media && c.p.media[0].caption) || '') : '';

// ---------- قدم ۱: انواع پیوست ----------
delete env.cache.rl_700; msg('📝 ارسال گزارش');
ok(st().state === 'form:photos', 'قدم ۱');
let n = env.tgCalls.length;
msg(null, { video: { file_id: 'V1', file_unique_id: 'uV1', mime_type: 'video/mp4' } });
ok(/📎 تا حالا دریافت شد: ۱ ویدیو/.test(textsTo(since(n), 700)), 'ویدیو پذیرفته شد: ' + textsTo(since(n), 700));
// آلبوم دو فایل (ورد + پاورپوینت)
msg(null, { media_group_id: 'D', document: { file_id: 'D1', file_unique_id: 'uD1', mime_type: 'application/msword', file_name: 'a.docx' } });
msg(null, { media_group_id: 'D', document: { file_id: 'D2', file_unique_id: 'uD2', mime_type: 'application/vnd.ms-powerpoint', file_name: 'b.pptx' } });
// تصویر به‌صورت فایل ← پذیرفته + توضیح
n = env.tgCalls.length;
msg(null, { document: { file_id: 'I1', file_unique_id: 'uI1', mime_type: 'image/jpeg', file_name: 'poster.jpg' } });
ok(/به‌صورت «فایل» رسید/.test(textsTo(since(n), 700)), 'توضیح تصویر-فایل');
msg(null, { photo: [{ file_id: 'p-small', file_unique_id: 'x' }, { file_id: 'P1', file_unique_id: 'uP1' }] });
msg(null, { audio: { file_id: 'A1', file_unique_id: 'uA1' } });
msg(null, { animation: { file_id: 'G1', file_unique_id: 'uG1' }, document: { file_id: 'G1', file_unique_id: 'uG1' } });
// تکراری
n = env.tgCalls.length; msg(null, { photo: [{ file_id: 'P1', file_unique_id: 'uP1' }] });
ok(/قبلاً دریافت شده/.test(textsTo(since(n), 700)), 'مورد تکراری اضافه نشد');
// استیکر
n = env.tgCalls.length; msg(null, { sticker: { file_id: 'S' } });
ok(/پذیرفته نمی‌شود/.test(textsTo(since(n), 700)), 'استیکر رد شد');
let d = st().data;
ok(d.photos.map(x => x.kind + ':' + x.file_id).join(',') === 'video:V1,document:D1,document:D2,document:I1,photo:P1,audio:A1,animation:G1', 'پیوست‌ها با نوع: ' + d.photos.map(x => x.kind + ':' + x.file_id).join(','));
const lastEdit = env.tgCalls.filter(c => c.method === 'editMessageText' && c.p.chat_id == 700).pop();
ok(lastEdit && lastEdit.p.text.includes('۱ عکس، ۱ ویدیو، ۳ فایل، ۱ صوت، ۱ گیف') && lastEdit.p.reply_markup.inline_keyboard.flat().some(b => b.callback_data === 'pz'), 'شمارنده + دکمه پاک کردن: ' + (lastEdit && lastEdit.p.text));
// سقف ۱۰ مورد
for (let i = 0; i < 5; i++) msg(null, { media_group_id: 'BIG', document: { file_id: 'X' + i, file_unique_id: 'uX' + i } });
n = env.tgCalls.length;
ok(st().data.photos.length === 10, 'سقف ۱۰ مورد: ' + st().data.photos.length);
// پاک کردن همه
cb('pz'); ok(st().data.photos.length === 0 && st().state === 'form:photos', 'پاک کردن همه');
cb('pd'); ok(/حداقل یک عکس، ویدیو یا فایل/.test(JSON.stringify(env.tgCalls.slice(-1))), 'بدون پیوست ادامه نمی‌دهد');
// دوباره: ۶ عکس ← ۵ عکس
for (let i = 0; i < 6; i++) msg(null, { media_group_id: 'PH', photo: [{ file_id: 'Q' + i, file_unique_id: 'uQ' + i }] });
ok(st().data.photos.length === 5, 'سقف ۵ عکس');
cb('pz');
msg(null, { photo: [{ file_id: 'P1', file_unique_id: 'uP1' }] });
msg(null, { media_group_id: 'D', document: { file_id: 'D1', file_unique_id: 'uD1', mime_type: 'application/pdf' } });
msg(null, { media_group_id: 'D', document: { file_id: 'D2', file_unique_id: 'uD2', mime_type: 'application/pdf' } });
msg(null, { video: { file_id: 'V1', file_unique_id: 'uV1' } });
msg(null, { voice: { file_id: 'O1', file_unique_id: 'uO1' } });
cb('pd');
ok(st().state === 'form:class', 'قدم ۲');
n = env.tgCalls.length; msg(null, { photo: [{ file_id: 'Z', file_unique_id: 'uZ' }] });
ok(/فقط در قدم ۱/.test(textsTo(since(n), 700)) && st().data.photos.length === 5, 'عکس در قدم‌های بعد ← راهنما');

// ---------- تولیدات ----------
cb('ct:4'); cb('sb:2');
const te = env.tgCalls.filter(c => c.p.chat_id == 700 && c.p.reply_markup && c.p.reply_markup.inline_keyboard).pop().p.reply_markup.inline_keyboard.flat().find(b => /حامد کمال/.test(b.text)).callback_data;
cb(te); n = env.tgCalls.length; msg('سواد رسانه');
ok(st().state === 'form:preview', 'پیش‌نمایش');
let pv = sendCalls(since(n), 700);
ok(pv.map(c => c.method + (c.p.media ? ':' + c.p.media.map(m => m.type).join('+') : '')).join(',') === 'sendMediaGroup:photo+video,sendMediaGroup:document+document,sendVoice', 'پیش‌نمایش: ' + pv.map(c => c.method + (c.p.media ? ':' + c.p.media.map(m => m.type).join('+') : '')).join(','));
ok(capOf(pv[0]).includes('#تولیدات') && !capOf(pv[1]) && !capOf(pv[2]), 'کپشن فقط روی پیام اول');
cb('ps');
const rid = Math.max(...C.readTable_('Reports').map(r => +r.report_id));
const rows = C.getReportPhotos_(rid);
ok(rows.map(r => r.kind).join(',') === 'photo,document,document,video,voice', 'ستون kind در ReportPhotos: ' + rows.map(r => r.kind).join(','));
const grp = sendCalls(env.tgCalls, -100500);
ok(grp.length === 3 && capOf(grp[0]).includes('#تولیدات'), 'گروه بررسی: ۳ پیام، کپشن روی اولی');
const rep0 = C.getReport_(rid); ok(JSON.parse(rep0.review_album_ids).length === 5, 'شناسه پیام‌های گروه: ' + rep0.review_album_ids);

// ---------- تایید ← کانال ----------
n = env.tgCalls.length; cb('ok:' + rid, 500, -100500);
const ch = sendCalls(since(n), -100600);
ok(ch.length === 3 && capOf(ch[0]).includes('🎞 نوع محتوا: #کلیپ'), 'کانال: ۳ پیام با کپشن روی اولی');
const rep1 = C.getReport_(rid), ids = JSON.parse(rep1.channel_message_ids);
ok(rep1.status === 'approved' && ids.length === 5, 'تایید + ۵ شناسه پیام کانال');
// ویرایش کپشن از پنل ← روی اولین پیام
cb('A:rx:' + rid + ':subject', 500); post({ message: { message_id: mid++, from: { id: 500 }, chat: { id: 500, type: 'private' }, date: 1, text: 'موضوع تازه' } });
const ec = env.tgCalls.filter(c => c.method === 'editMessageCaption').pop();
ok(ec && ec.p.message_id === ids[0] && ec.p.caption.includes('موضوع تازه'), 'ویرایش کپشن روی پیام اول');
// حذف از کانال ← همه پیام‌ها
n = env.tgCalls.length; cb('A:rD:' + rid, 500);
const dels = since(n).filter(c => c.method === 'deleteMessage' && c.p.chat_id == -100600).map(c => c.p.message_id);
ok(dels.length === 5 && C.getReport_(rid).status === 'deleted', 'حذف همه ۵ پیام: ' + dels.join(','));

// ---------- شکست وسط ارسال ← پیام‌های نیمه پاک می‌شوند ----------
const before = env.tgCalls.length;
env.tgResult.failMethods.sendVoice = true;
const out = C.tgSendAlbum_(-100600, [{ kind: 'photo', file_id: 'a' }, { kind: 'voice', file_id: 'b' }], 'cap');
delete env.tgResult.failMethods.sendVoice;
ok(out === null && since(before).filter(c => c.method === 'deleteMessage').length === 1, 'شکست وسط کار: null + پاک کردن پیام اول');

// ---------- گزارش قدیمی (ستون kind خالی) ----------
const oldRid = C.createReport_({ submitter_telegram_id: 700, employee_name: 'همکار', city_id: 29, status: 'pending', class_type: 'حضوری', subject: 'قدیمی',
  event_jalali: '1405/08/01', event_year: 1405, event_month: 8, event_day: 1, channel_message_ids: '[]', review_album_ids: '[]' }, [{ file_id: 'old1', file_unique_id: 'uo1' }, { file_id: 'old2' }]);
env.sheets.ReportPhotos.data.forEach(r => { if (+r[0] === oldRid) r[4] = ''; });
ok(C.getReportMedia_(oldRid).map(x => x.kind).join(',') === 'photo,photo', 'ردیف قدیمی ← عکس');
n = env.tgCalls.length; C.sendReportToReview_(C.getReport_(oldRid));
ok(sendCalls(since(n), -100500).map(c => c.method).join(',') === 'sendMediaGroup' && sendCalls(since(n), -100500)[0].p.media.every(m => m.type === 'photo'), 'گزارش قدیمی مثل قبل آلبوم عکس');

// ---------- بیرون از فرم: یک پیام راهنما برای هر آلبوم ----------
C.clearSession_(700, 700); n = env.tgCalls.length;
msg(null, { media_group_id: 'OUT', document: { file_id: 'o1' } }); msg(null, { media_group_id: 'OUT', document: { file_id: 'o2' } });
ok(since(n).filter(c => c.method === 'sendMessage' && /از دکمه/.test(c.p.text)).length === 1, 'بیرون از فرم: یک راهنما برای آلبوم');

ok(errs().filter(e => !/sim fail/.test(e)).length === 0, 'بدون خطا: ' + errs().join(' ; '));
console.log('scenario_media: ' + pass + '/' + (pass + fail));
