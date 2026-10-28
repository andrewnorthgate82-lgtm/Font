// ReportForm.gs — فرم ۱۱ قدمی ثبت گزارش + پیش‌نمایش + ویرایش تک‌فیلدی + اصلاحیه پس از رد
// در هر قدم «↩️ بازگشت» و «❌ انصراف» هست. قدم‌های دکمه‌ای پیام قبلی را ویرایش می‌کنند تا مصرف سهمیه کم شود.

var FORM_BACK = {
  class: 'photos', operation: 'class', subject: 'operation', teacher: 'subject',
  teacherNew: 'teacher', date: 'teacher', year: 'date', month: 'year', day: 'month',
  hour: 'date', minute: 'hour', duration: 'hour', participants: 'duration',
  audience: 'participants', place: 'audience', preview: 'place'
};
var FORM_SUBSTEPS = { year: 1, month: 1, day: 1, minute: 1 };
var EDIT_FIELDS = [
  ['photos', '📷 عکس‌ها'], ['class', 'نوع کلاس/برنامه'], ['operation', 'نام عملیات'],
  ['subject', 'موضوع'], ['teacher', 'مدرس/سخنران'], ['date', 'تاریخ برگزاری'],
  ['hour', 'ساعت برگزاری'], ['duration', 'مدت زمان'], ['participants', 'تعداد شرکت‌کنندگان'],
  ['audience', 'نوع مخاطبین'], ['place', 'مکان برگزاری']
];

function startReport_(chatId, tg, user) {
  if (!positionOf_(user && user.position)) { askPosition_(chatId, tg); return; } // فاز ۴: نقش فقط یک بار پرسیده می‌شود
  var s = getSession_(chatId, tg);
  s.state = 'form:photos';
  s.data = { photos: [], replacePhotos: false, editing: false, rid: 0, tp: 0 };
  saveSession_(chatId, tg, s);
  showStep_(chatId, tg, s, 'photos', 0);
}

// فاز ۴: همکاری که هنوز نقش ندارد، قبل از اولین گزارش نقش خودش را انتخاب می‌کند (بعداً فقط ادمین عوضش می‌کند)
function askPosition_(chatId, tg) {
  var s = getSession_(chatId, tg);
  s.state = 'form:position'; s.data = {};
  saveSession_(chatId, tg, s);
  var rows = POSITIONS.map(function (p, i) { return [btn_(p, 'po:' + i)]; });
  rows.push([btn_('❌ انصراف', 'cx')]);
  tgSend_(chatId, '🎖 پیش از ثبت گزارش، نقش خودتان را در شهرستان انتخاب کنید:\n(فقط همین یک بار پرسیده می‌شود؛ برای تغییرِ بعدی به ادمین بگویید.)', ikb_(rows));
}
function photosKb_(n) {
  return ikb_([[btn_('✅ پایان ارسال عکس (' + n + ')', 'pd')], [btn_('❌ انصراف', 'cx')]]);
}
function stepOf_(session) { return String(session.state || '').replace('form:', ''); }

function showStep_(chatId, tg, s, step, editId) {
  var u = getUserByTg_(tg), text = '', rows = null, i;
  s.state = 'form:' + step;
  if (step === 'photos') {
    var n = (s.data.photos || []).length;
    text = n === 0 ? TXT.FORM_START : '📷 تا حالا ' + n + ' عکس دریافت شد (حداکثر ' + CFG.MAX_PHOTOS + '). عکس بعدی را بفرستید یا «پایان ارسال عکس» را بزنید.';
    var sent = tgSend_(chatId, text, photosKb_(n));
    if (sent) s.data.photosMsg = sent.message_id;
    saveSession_(chatId, tg, s);
    return;
  }
  if (step === 'class') {
    text = TXT.ASK_CLASS; rows = [];
    CFG.CLASS_TYPES.forEach(function (ct, idx) { rows.push([btn_(ct, 'ct:' + idx)]); });
  } else if (step === 'operation') {
    text = TXT.ASK_OPERATION; rows = [[btn_('ندارد', 'op0')]];
  } else if (step === 'subject') {
    text = TXT.ASK_SUBJECT; rows = [];
  } else if (step === 'teacher') {
    text = TXT.ASK_TEACHER;
    var p = paginate_(listInstructors_(u.city_id), s.data.tp || 0, CFG.TEACHERS_PER_PAGE);
    rows = p.chunk.map(function (t) { return [btn_(t.name, 'te:' + t.instructor_id)]; });
    rows.push([btn_('➕ مدرس جدید', 'tn')]);
    var nav = [];
    if (p.page > 0) nav.push(btn_('⬅️ قبلی', 'tp:' + (p.page - 1)));
    if (p.page < p.total - 1) nav.push(btn_('بعدی ➡️', 'tp:' + (p.page + 1)));
    if (nav.length) rows.push(nav);
  } else if (step === 'teacherNew') {
    text = TXT.ASK_TEACHER_NEW; rows = [];
  } else if (step === 'date') {
    text = TXT.ASK_DATE;
    rows = [[btn_('امروز', 'dq:0'), btn_('دیروز', 'dq:-1'), btn_('۲ روز پیش', 'dq:-2')], [btn_('📅 تاریخ دیگر', 'dc')]];
  } else if (step === 'year') {
    text = TXT.ASK_YEAR; rows = []; var r3 = [], cur = todayJalali_().jy;
    for (i = cur; i > cur - 5; i--) { r3.push(btn_(String(i), 'dy:' + i)); if (r3.length === 3) { rows.push(r3); r3 = []; } }
    if (r3.length) rows.push(r3);
  } else if (step === 'month') {
    text = TXT.ASK_MONTH; rows = []; var rm = [];
    JALALI_MONTHS.forEach(function (m, idx) { rm.push(btn_(m, 'dm:' + (idx + 1))); if (rm.length === 3) { rows.push(rm); rm = []; } });
    if (rm.length) rows.push(rm);
  } else if (step === 'day') {
    text = TXT.ASK_DAY; rows = []; var rd = [], nd = daysInJalaliMonth_(s.data.tmpY, s.data.tmpM);
    for (i = 1; i <= nd; i++) { rd.push(btn_(String(i), 'dd:' + i)); if (rd.length === 7) { rows.push(rd); rd = []; } }
    if (rd.length) rows.push(rd);
  } else if (step === 'hour') {
    text = TXT.ASK_HOUR; rows = []; var rh = [];
    for (i = 0; i < 24; i++) { rh.push(btn_(pad2_(i), 'th:' + pad2_(i))); if (rh.length === 6) { rows.push(rh); rh = []; } }
    if (rh.length) rows.push(rh);
  } else if (step === 'minute') {
    text = TXT.ASK_MIN;
    rows = [CFG.MINUTES.map(function (m) { return btn_(m, 'tm:' + m); })];
  } else if (step === 'duration') {
    text = TXT.ASK_DURATION;
    rows = [CFG.DURATIONS_MIN.map(function (d) { return btn_(d + ' دقیقه', 'du:' + d); })];
  } else if (step === 'participants') {
    text = TXT.ASK_PARTICIPANTS; rows = [];
  } else if (step === 'audience') {
    text = TXT.ASK_AUDIENCE;
    rows = [CFG.AUDIENCES.map(function (a, idx) { return btn_(a, 'au:' + idx); })];
  } else if (step === 'place') {
    text = TXT.ASK_PLACE; rows = [];
  }
  rows = (rows || []).concat(backCancelRows_());
  var kb = ikb_(rows), res = null;
  if (editId) res = tgEdit_(chatId, editId, text, kb);
  if (!res) { res = tgSend_(chatId, text, kb); if (res) s.data.lastMsg = res.message_id; }
  saveSession_(chatId, tg, s);
}

function goNext_(chatId, tg, s, normalNext, editId) {
  if (s.data.editing) {
    s.data.editing = false; saveSession_(chatId, tg, s);
    showPreview_(chatId, tg, s);
  } else showStep_(chatId, tg, s, normalNext, editId);
}

function showPreview_(chatId, tg, s) {
  var u = getUserByTg_(tg), city = getCityById_(u.city_id);
  s.state = 'form:preview'; saveSession_(chatId, tg, s);
  var d = s.data;
  if (!d.event_year || !d.duration_min || !d.participants || !(d.photos || []).length) {
    tgSend_(chatId, '⚠️ بعضی اطلاعات فرم ناقص است. از «✏️ ویرایش» بخش ناقص را کامل کنید.', editFieldsKb_());
    return;
  }
  var caption = captionFromData_(d, city ? city.hashtag : '', todayJalaliStr_()); // پیش‌نمایش: تاریخ درج = امروز
  if (caption.length > CFG.CAPTION_LIMIT) {
    tgSend_(chatId, '⚠️ متن کپشن از حد مجاز تلگرام (' + CFG.CAPTION_LIMIT + ' حرف) بلندتر شده است (' +
      caption.length + ' حرف).\nلطفاً «موضوع» یا «مکان» را کوتاه‌تر کنید.', editFieldsKb_());
    return;
  }
  tgSendAlbum_(chatId, d.photos, caption);
  tgSend_(chatId, TXT.PREVIEW_TITLE, ikb_([
    [btn_('✅ ارسال برای بررسی', 'ps')],
    [btn_('✏️ ویرایش', 'pe'), btn_('❌ انصراف', 'cx')]
  ]));
}
function editFieldsKb_() {
  var rows = [], row = [];
  EDIT_FIELDS.forEach(function (f) {
    row.push(btn_(f[1], 'ef:' + f[0]));
    if (row.length === 2) { rows.push(row); row = []; }
  });
  if (row.length) rows.push(row);
  rows.push([btn_('↩️ بازگشت به پیش‌نمایش', 'pw')]);
  rows.push([btn_('❌ انصراف', 'cx')]);
  return ikb_(rows);
}

// ---------- عکس‌ها (تکی یا آلبوم؛ شمارنده با ویرایش همان پیام بالا می‌رود) ----------
function handleFormPhoto_(msg, s) {
  var chatId = msg.chat.id, tg = msg.from.id;
  s.data.photos = s.data.photos || [];
  if (s.data.replacePhotos) { // در ویرایش عکس‌ها: قبلی‌ها فقط با رسیدن اولین عکس جدید کنار می‌روند
    s.data.photos = [];
    s.data.replacePhotos = false;
  }
  if (s.data.photos.length >= CFG.MAX_PHOTOS) {
    if (!msg.media_group_id) tgSend_(chatId, '⚠️ حداکثر ' + CFG.MAX_PHOTOS + ' عکس مجاز است؛ عکس‌های اضافه پذیرفته نشدند.', photosKb_(s.data.photos.length));
    return;
  }
  var best = msg.photo[msg.photo.length - 1];
  s.data.photos.push({ file_id: best.file_id, file_unique_id: best.file_unique_id || '' });
  var n = s.data.photos.length;
  var text = '📷 تا حالا ' + n + ' عکس دریافت شد (حداکثر ' + CFG.MAX_PHOTOS + '). عکس بعدی را بفرستید یا «پایان ارسال عکس» را بزنید.';
  var done = false;
  if (s.data.photosMsg) done = !!tgEdit_(chatId, s.data.photosMsg, text, photosKb_(n));
  if (!done) { var sent = tgSend_(chatId, text, photosKb_(n)); if (sent) s.data.photosMsg = sent.message_id; }
  saveSession_(chatId, tg, s);
}

// ---------- قدم‌های متنی ----------
function handleFormText_(msg, s) {
  var chatId = msg.chat.id, tg = msg.from.id, step = stepOf_(s);
  var u = getUserByTg_(tg);
  if (!isActiveEmployee_(u)) { denyEmployee_(chatId, tg); return; }
  if (step === 'operation') {
    s.data.operation_name = msg.text.trim(); saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'subject', 0);
  } else if (step === 'subject') {
    if (!msg.text.trim()) { tgSend_(chatId, TXT.ASK_SUBJECT); return; }
    s.data.subject = msg.text.trim(); saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'teacher', 0);
  } else if (step === 'teacherNew') {
    var name = normalizeName_(msg.text);
    if (name.length < 3) { tgSend_(chatId, 'نام مدرس را کامل بنویسید:'); return; }
    var t = getOrCreateInstructor_(u.city_id, name, tg);
    s.data.instructor_id = t.instructor_id; s.data.instructor_name = t.name;
    saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'date', 0);
  } else if (step === 'participants') {
    var n = parseIntFa_(msg.text);
    if (n === null || n < CFG.MIN_PARTICIPANTS || n > CFG.MAX_PARTICIPANTS) { tgSend_(chatId, TXT.PARTICIPANTS_BAD); return; }
    s.data.participants = n; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'audience', 0);
  } else if (step === 'place') {
    if (!msg.text.trim()) { tgSend_(chatId, TXT.ASK_PLACE); return; }
    s.data.place = msg.text.trim(); s.data.editing = false; saveSession_(chatId, tg, s);
    showPreview_(chatId, tg, s);
  } else if (step === 'photos') {
    tgSend_(chatId, 'عکس‌ها را بفرستید یا روی «✅ پایان ارسال عکس» بزنید.');
  } else { // قدم‌های دکمه‌ای: نوع کلاس، مدرس، تاریخ، ساعت، مدت، مخاطب، پیش‌نمایش
    tgSend_(chatId, 'لطفاً در این قدم از دکمه‌ها استفاده کنید 🙏');
  }
}

// ---------- دکمه‌های فرم ----------
function handleFormCb_(cb, s, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id, mid = cb.message.message_id;
  var u = getUserByTg_(tg);

  if (data === 'cx') {
    clearSession_(chatId, tg); tgAnswerCb_(cb.id, 'لغو شد');
    if (isActiveEmployee_(u) || isAdminTg_(tg)) sendMainMenu_(chatId, tg);
    else tgSend_(chatId, TXT.CANCELLED);
    return;
  }
  if (!isActiveEmployee_(u)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  var step = stepOf_(s), parts = data.split(':'), p = parts[0], v = parts[1];

  if (p === 'po') { // انتخاب نقش (فاز ۴)
    var pos = /^\d$/.test(String(v)) ? POSITIONS[num_(v)] : null;
    if (step !== 'position' || !pos) { tgAnswerCb_(cb.id, 'این دکمه دیگر فعال نیست؛ دوباره «' + MENU.NEW_REPORT + '» را بزنید.', true); return; }
    if (!positionOf_(u.position)) updateUser_(u.user_id, tg, { position: pos }); // اگر ادمین در این فاصله نقش گذاشته، همان می‌ماند
    tgAnswerCb_(cb.id, 'ثبت شد');
    var fresh = getUserByTg_(tg);
    tgEdit_(chatId, mid, '🎖 نقش شما: ' + (positionOf_(fresh && fresh.position) || pos) + ' ✅', null);
    startReport_(chatId, tg, fresh);
    return;
  }

  if (data === 'bk') {
    tgAnswerCb_(cb.id);
    if (s.data.editing && !FORM_SUBSTEPS[step]) {
      s.data.editing = false; saveSession_(chatId, tg, s);
      showPreview_(chatId, tg, s);
    } else showStep_(chatId, tg, s, FORM_BACK[step] || 'photos', mid);
    return;
  }
  if (data === 'pd') {
    if (!(s.data.photos || []).length) { tgAnswerCb_(cb.id, TXT.PHOTO_NEED_ONE, true); return; }
    tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'class', 0); return;
  }
  if (p === 'ct') { s.data.class_type = CFG.CLASS_TYPES[num_(v)]; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'operation', mid); return; }
  if (data === 'op0') { s.data.operation_name = ''; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'subject', mid); return; }
  if (p === 'tp') { s.data.tp = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'teacher', mid); return; }
  if (data === 'tn') { tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'teacherNew', mid); return; }
  if (p === 'te') {
    var t = getInstructorInCity_(num_(v), u.city_id); // فقط شهرستان خود کارمند
    if (!t) { tgAnswerCb_(cb.id, 'این مدرس در شهرستان شما یافت نشد.', true); return; }
    s.data.instructor_id = t.instructor_id; s.data.instructor_name = t.name;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'date', mid); return;
  }
  if (data === 'dc') { tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'year', mid); return; }
  if (p === 'dq') {
    var d = jalaliOffset_(num_(v));
    s.data.event_year = d.jy; s.data.event_month = d.jm; s.data.event_day = d.jd;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'hour', mid); return;
  }
  if (p === 'dy') { s.data.tmpY = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'month', mid); return; }
  if (p === 'dm') { s.data.tmpM = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'day', mid); return; }
  if (p === 'dd') {
    if (isFutureJalali_(s.data.tmpY, s.data.tmpM, num_(v))) {
      tgAnswerCb_(cb.id, TXT.DATE_FUTURE, true);
      showStep_(chatId, tg, s, 'date', mid); return;
    }
    s.data.event_year = s.data.tmpY; s.data.event_month = s.data.tmpM; s.data.event_day = num_(v);
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'hour', mid); return;
  }
  if (p === 'th') { s.data.hour = v; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'minute', mid); return; }
  if (p === 'tm') {
    s.data.event_time = (s.data.hour || '00') + ':' + v;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'duration', mid); return;
  }
  if (p === 'du') { s.data.duration_min = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'participants', mid); return; }
  if (p === 'au') { s.data.audience = CFG.AUDIENCES[num_(v)]; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'place', mid); return; }
  if (data === 'pe') { tgAnswerCb_(cb.id); tgSend_(chatId, TXT.EDIT_ASK, editFieldsKb_()); return; }
  if (data === 'pw') { tgAnswerCb_(cb.id); showPreview_(chatId, tg, s); return; }
  if (p === 'ef') {
    s.data.editing = true;
    if (v === 'photos') { s.data.replacePhotos = true; tgAnswerCb_(cb.id, 'عکس‌های جدید را بفرستید؛ با اولین عکس جدید جایگزین قبلی‌ها می‌شوند. اگر پشیمان شدید «بازگشت» بزنید تا قبلی‌ها بمانند.'); }
    else tgAnswerCb_(cb.id);
    saveSession_(chatId, tg, s);
    showStep_(chatId, tg, s, v, 0);
    return;
  }
  if (data === 'ps') { submitReport_(cb, s, u); return; }
  tgAnswerCb_(cb.id);
}

function submitReport_(cb, s, u) {
  var chatId = cb.message.chat.id, tg = cb.from.id;
  if (!submitAllowed_(tg)) {
    tgAnswerCb_(cb.id, '⚠️ خیلی تند! در هر دقیقه حداکثر ' + CFG.MAX_REPORTS_PER_MINUTE + ' گزارش می‌توانید برای بررسی بفرستید. کمی صبر کنید.', true);
    return;
  }
  var d = s.data, city = getCityById_(u.city_id);
  var caption = captionFromData_(d, city ? city.hashtag : '', todayJalaliStr_());
  if (caption.length > CFG.CAPTION_LIMIT) { tgAnswerCb_(cb.id, 'کپشن بلندتر از حد مجاز است؛ موضوع یا مکان را کوتاه کنید.', true); return; }
  tgAnswerCb_(cb.id, 'در حال ارسال برای بررسی…');
  var rep = {
    submitter_telegram_id: tg, employee_name: u.full_name, city_id: u.city_id,
    instructor_id: d.instructor_id || '', instructor_name: d.instructor_name || '',
    status: 'pending', class_type: d.class_type, operation_name: d.operation_name || '',
    subject: d.subject,
    event_jalali: formatJalali_(d.event_year, d.event_month, d.event_day),
    event_gregorian: jalaliToGregStr_(d.event_year, d.event_month, d.event_day),
    event_year: d.event_year, event_month: d.event_month, event_day: d.event_day,
    event_time: d.event_time, duration_min: d.duration_min, participants: d.participants,
    audience: d.audience, place: d.place,
    publish_jalali: '', published_caption: '', channel_message_ids: '[]',
    review_chat_id: '', review_album_ids: '[]', review_info_id: '',
    decided_by: '', decided_at: '', resubmit_count: 0,
    submitter_position: positionOf_(u.position) // نقش فرستنده در همین لحظه (برای خواهر/برادر فایل اکسل)
  };
  var rid;
  if (d.rid) { // ارسال مجدد گزارش ردشده — همان ردیف، شمارنده +۱
    var old = getReport_(d.rid);
    if (!old || num_(old.submitter_telegram_id) !== num_(tg) || num_(old.city_id) !== num_(u.city_id) || old.status !== 'rejected') {
      tgSend_(chatId, '⚠️ این گزارش دیگر قابل ارسال مجدد نیست.');
      clearSession_(chatId, tg); return;
    }
    rep.resubmit_count = num_(old.resubmit_count) + 1;
    rep.created_at = old.created_at;
    updateReport_(d.rid, rep);
    setReportPhotos_(d.rid, d.photos);
    rid = d.rid;
  } else rid = createReport_(rep, d.photos);

  var sent = sendReportToReview_(getReport_(rid));
  clearSession_(chatId, tg);
  tgSend_(chatId, TXT.REPORT_SENT, mainMenuKb_());
  if (!sent) superadminTgs_().forEach(function (a) {
    tgSend_(a, '⚠️ گروه بررسی هنوز تنظیم نشده و یک گزارش در انتظار مانده است. بات را به گروه بررسی اضافه کنید و در گروه /here بفرستید.');
  });
}

// اصلاح و ارسال مجدد — فرم با داده‌های قبلی باز می‌شود (نه از صفر)
function handleResubmitCb_(cb, session, data) {
  var chatId = cb.message.chat.id, tg = cb.from.id;
  var u = getUserByTg_(tg);
  if (!isActiveEmployee_(u)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  var rid = num_(data.split(':')[1]);
  var r = getReport_(rid);
  if (!r || num_(r.submitter_telegram_id) !== num_(tg) || num_(r.city_id) !== num_(u.city_id)) {
    tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return;
  }
  if (r.status !== 'rejected') { tgAnswerCb_(cb.id, 'این گزارش در وضعیت ردشده نیست و قابل اصلاح نمی‌باشد.', true); return; }
  var s = getSession_(chatId, tg);
  s.data = {
    photos: getReportPhotos_(rid).map(function (p) { return { file_id: String(p.file_id), file_unique_id: String(p.file_unique_id || '') }; }),
    editing: false, rid: rid, tp: 0,
    class_type: r.class_type, operation_name: r.operation_name || '', subject: r.subject,
    instructor_id: r.instructor_id, instructor_name: r.instructor_name,
    event_year: num_(r.event_year), event_month: num_(r.event_month), event_day: num_(r.event_day),
    event_time: String(r.event_time), hour: String(r.event_time || '00:00').split(':')[0],
    duration_min: num_(r.duration_min), participants: num_(r.participants),
    audience: r.audience, place: r.place
  };
  s.state = 'form:preview';
  saveSession_(chatId, tg, s);
  tgAnswerCb_(cb.id);
  tgSend_(chatId, 'اطلاعات قبلی گزارش بازیابی شد؛ پیش‌نمایش را ببینید و با «✏️ ویرایش» اصلاح کنید:');
  showPreview_(chatId, tg, s);
}
