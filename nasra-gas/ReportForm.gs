// ReportForm.gs — فرم ثبت گزارش (قدم‌ها برای هر نوع برنامه جداست؛ تعریفشان در Caption.gs) + پیش‌نمایش + ویرایش + اصلاحیه پس از رد
// در هر قدم «↩️ بازگشت» و «❌ انصراف» هست. قدم‌های دکمه‌ای پیام قبلی را ویرایش می‌کنند تا مصرف سهمیه کم شود.
// حالت ویرایش (از پیش‌نمایش یا اصلاحیه): بعد از هر قدم فقط بخش‌های ناقص پرسیده می‌شود و بعد پیش‌نمایش.

var FORM_SUBSTEPS = { year: 1, month: 1, day: 1, minute: 1, teacherNew: 1 };

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


// گزینه‌های «بستر» برای همین گزارش (کلاس مجازی ← پلتفرم‌ها، انتشار شبکه اجتماعی ← شبکه‌ها، بقیه خلاقانه ← فقط نوشتنی)
function platformOptions_(d) {
  if (isOnlineClass_(d.class_type, d.class_sub)) return ONLINE_PLATFORMS;
  if (d.class_type === 'خلاقانه' && d.class_sub === CLASS_SUBS['خلاقانه'][0]) return SOCIAL_PLATFORMS;
  return [];
}
function platformPrompt_(d) {
  if (isOnlineClass_(d.class_type, d.class_sub)) return TXT.ASK_PLATFORM_ONLINE;
  return platformOptions_(d).length ? TXT.ASK_PLATFORM_SOCIAL : TXT.ASK_PLATFORM_CREATIVE;
}
// بخش‌هایی که به «نوع» بستگی دارند؛ با عوض شدن نوع پاک می‌شوند تا دوباره (و درست) پرسیده شوند
function resetTypeFields_(d) {
  ['class_sub', 'operation_name', 'event_year', 'event_month', 'event_day', 'event_time', 'hour', 'duration_min',
    'participants', 'audience', 'place', 'link', 'tmpY', 'tmpM'].forEach(function (k) { delete d[k]; });
}
// تولیدات قدم تاریخ ندارد: تاریخ = روز ارسال (برای دوره گزارش، ماه کپشن و فایل اکسل)
function fillAutoFields_(d) {
  if (d.class_type && formFlow_(d).indexOf('date') === -1 && !(num_(d.event_year) > 0)) {
    var t = todayJalali_();
    d.event_year = t.jy; d.event_month = t.jm; d.event_day = t.jd;
  }
}
function rowsOf_(labels, prefix, perRow) {
  var rows = [], row = [];
  labels.forEach(function (l, i) { row.push(btn_(l, prefix + i)); if (row.length === perRow) { rows.push(row); row = []; } });
  if (row.length) rows.push(row);
  return rows;
}

function showStep_(chatId, tg, s, step, editId) {
  var u = getUserByTg_(tg), d = s.data, type = d.class_type, text = '', rows = null, i;
  s.state = 'form:' + step;
  if (step === 'photos') {
    var n = (d.photos || []).length;
    text = n === 0 ? TXT.FORM_START : '📷 تا حالا ' + n + ' عکس دریافت شد (حداکثر ' + CFG.MAX_PHOTOS + '). عکس بعدی را بفرستید یا «پایان ارسال عکس» را بزنید.';
    var sent = tgSend_(chatId, text, photosKb_(n));
    if (sent) d.photosMsg = sent.message_id;
    saveSession_(chatId, tg, s);
    return;
  }
  if (step === 'class') {
    text = TXT.ASK_CLASS; rows = [];
    CFG.CLASS_TYPES.forEach(function (ct, idx) { rows.push([btn_(ct, 'ct:' + idx)]); });
  } else if (step === 'sub') {
    var subs = (CLASS_SUBS[type] || []).map(subLabel_);
    text = type === 'توانمندسازی' ? TXT.ASK_SUB_EMPOWER : type === 'تولیدات' ? TXT.ASK_SUB_PRODUCT : TXT.ASK_SUB_CREATIVE;
    rows = rowsOf_(subs, 'sb:', 2);
  } else if (step === 'operation') {
    text = TXT.ASK_OPERATION; rows = [[btn_('ندارد', 'op0')]];
  } else if (step === 'subject') {
    text = type === 'تولیدات' ? TXT.ASK_SUBJECT_PRODUCT : TXT.ASK_SUBJECT;
    rows = SUBJECT_SUGGESTIONS.map(function (sj, idx) { return [btn_(sj, 'sj:' + idx)]; });
  } else if (step === 'teacher') {
    text = type === 'تولیدات' ? TXT.ASK_PRODUCER : TXT.ASK_TEACHER;
    var p = paginate_(listInstructors_(u.city_id), d.tp || 0, CFG.TEACHERS_PER_PAGE);
    rows = p.chunk.map(function (t) { return [btn_(t.name, 'te:' + t.instructor_id)]; });
    rows.push([btn_(type === 'تولیدات' ? '➕ تولید کننده جدید' : '➕ مدرس جدید', 'tn')]);
    var nav = [];
    if (p.page > 0) nav.push(btn_('⬅️ قبلی', 'tp:' + (p.page - 1)));
    if (p.page < p.total - 1) nav.push(btn_('بعدی ➡️', 'tp:' + (p.page + 1)));
    if (nav.length) rows.push(nav);
  } else if (step === 'teacherNew') {
    text = type === 'تولیدات' ? TXT.ASK_PRODUCER_NEW : TXT.ASK_TEACHER_NEW; rows = [];
  } else if (step === 'date') {
    text = type === 'خلاقانه' ? TXT.ASK_DATE_PUBLISH : TXT.ASK_DATE;
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
    text = TXT.ASK_DAY; rows = []; var rd = [], nd = daysInJalaliMonth_(d.tmpY, d.tmpM);
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
    rows = [CFG.DURATIONS_MIN.map(function (x) { return btn_(x + ' دقیقه', 'du:' + x); })];
  } else if (step === 'participants') {
    text = TXT.ASK_PARTICIPANTS; rows = [];
  } else if (step === 'views') {
    text = TXT.ASK_VIEWS; rows = [];
  } else if (step === 'audience') {
    text = TXT.ASK_AUDIENCE;
    rows = [CFG.AUDIENCES.map(function (a, idx) { return btn_(a, 'au:' + idx); })];
  } else if (step === 'place') {
    text = TXT.ASK_PLACE; rows = [];
  } else if (step === 'platform') {
    text = platformPrompt_(d); rows = rowsOf_(platformOptions_(d), 'pf:', 3);
  } else if (step === 'link') {
    text = type === 'خلاقانه' ? TXT.ASK_LINK_CREATIVE : TXT.ASK_LINK_ONLINE; rows = [[btn_('ندارد', 'lk0')]];
  }
  if (!FORM_SUBSTEPS[step]) text = stepNo_(d, step) + ' — ' + text;
  rows = (rows || []).concat(backCancelRows_());
  var kb = ikb_(rows), res = null;
  if (editId) res = tgEdit_(chatId, editId, text, kb);
  if (!res) { res = tgSend_(chatId, text, kb); if (res) d.lastMsg = res.message_id; }
  saveSession_(chatId, tg, s);
}

// بعد از کامل شدن یک قدم: حالت عادی ← قدم بعدیِ همین نوع ؛ حالت ویرایش ← اولین بخش ناقص یا پیش‌نمایش
function goNext_(chatId, tg, s, doneStep, editId) {
  var d = s.data, nx;
  if (d.editing) {
    nx = firstMissing_(d);
    if (nx) { saveSession_(chatId, tg, s); showStep_(chatId, tg, s, nx, editId); return; }
    d.editing = false; saveSession_(chatId, tg, s);
    showPreview_(chatId, tg, s);
    return;
  }
  nx = nextStep_(d, doneStep);
  if (nx === 'preview') nx = firstMissing_(d) || 'preview';
  if (nx === 'preview') showPreview_(chatId, tg, s);
  else showStep_(chatId, tg, s, nx, editId);
}

function showPreview_(chatId, tg, s) {
  var u = getUserByTg_(tg), city = getCityById_(u.city_id), d = s.data;
  fillAutoFields_(d);
  var miss = firstMissing_(d);
  if (miss) {
    d.editing = true; saveSession_(chatId, tg, s);
    tgSend_(chatId, '⚠️ بخش «' + stepLabel_(d.class_type, miss) + '» هنوز کامل نشده است:');
    showStep_(chatId, tg, s, miss, 0);
    return;
  }
  s.state = 'form:preview'; saveSession_(chatId, tg, s);
  var caption = captionFromData_(d, city ? city.hashtag : '', todayJalaliStr_()); // پیش‌نمایش: تاریخ درج = امروز
  if (caption.length > CFG.CAPTION_LIMIT) {
    tgSend_(chatId, '⚠️ متن کپشن از حد مجاز تلگرام (' + CFG.CAPTION_LIMIT + ' حرف) بلندتر شده است (' +
      caption.length + ' حرف).\nلطفاً «موضوع» یا «بستر / مکان» را کوتاه‌تر کنید.', editFieldsKb_(d));
    return;
  }
  tgSendAlbum_(chatId, d.photos, caption);
  tgSend_(chatId, TXT.PREVIEW_TITLE, ikb_([
    [btn_('✅ ارسال برای بررسی', 'ps')],
    [btn_('✏️ ویرایش', 'pe'), btn_('❌ انصراف', 'cx')]
  ]));
}
function editFieldsKb_(d) {
  var rows = [], row = [];
  formFlow_(d).forEach(function (st) {
    row.push(btn_(stepLabel_(d.class_type, st), 'ef:' + st));
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
  var chatId = msg.chat.id, tg = msg.from.id, step = stepOf_(s), d = s.data;
  var u = getUserByTg_(tg), raw = String(msg.text || '').trim(), n;
  if (!isActiveEmployee_(u)) { denyEmployee_(chatId, tg); return; }
  if (step === 'operation') {
    d.operation_name = raw; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'operation', 0);
  } else if (step === 'subject') {
    if (!raw) { tgSend_(chatId, TXT.ASK_SUBJECT); return; }
    d.subject = raw; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'subject', 0);
  } else if (step === 'teacherNew') {
    var name = normalizeName_(raw), issue = instructorNameIssue_(name);
    if (issue) { tgSend_(chatId, '⚠️ ' + issue + '\n\n' + TXT.TEACHER_NAME_RULE + '\n\nدوباره بنویسید:'); return; }
    var t = getOrCreateInstructor_(u.city_id, name, tg);
    d.instructor_id = t.instructor_id; d.instructor_name = t.name;
    saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'teacher', 0);
  } else if (step === 'participants') {
    n = parseIntFa_(raw);
    if (n === null || n < CFG.MIN_PARTICIPANTS || n > CFG.MAX_PARTICIPANTS) { tgSend_(chatId, TXT.PARTICIPANTS_BAD); return; }
    d.participants = n; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'participants', 0);
  } else if (step === 'views') {
    n = parseIntFa_(raw);
    if (n === null || n < 1 || n > MAX_VIEWS) { tgSend_(chatId, TXT.VIEWS_BAD); return; }
    d.participants = n; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'views', 0);
  } else if (step === 'place' || step === 'platform') {
    if (!raw) { tgSend_(chatId, step === 'place' ? TXT.ASK_PLACE : platformPrompt_(d)); return; }
    d.place = raw; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, step, 0);
  } else if (step === 'link') {
    var lk = parseLinkInput_(raw);
    if (lk.error) { tgSend_(chatId, '⚠️ ' + lk.error, ikb_([[btn_('ندارد', 'lk0')]])); return; }
    d.link = lk.link; saveSession_(chatId, tg, s);
    goNext_(chatId, tg, s, 'link', 0);
  } else if (step === 'photos') {
    tgSend_(chatId, 'عکس‌ها را بفرستید یا روی «✅ پایان ارسال عکس» بزنید.');
  } else { // قدم‌های دکمه‌ای: نوع، زیرشاخه، مدرس، تاریخ، ساعت، مدت، مخاطب، پیش‌نمایش
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
  var step = stepOf_(s), parts = data.split(':'), p = parts[0], v = parts[1], d = s.data || {};
  function stale() { tgAnswerCb_(cb.id, 'این دکمه دیگر فعال نیست.', true); }

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
  if (String(s.state || '').indexOf('form:') !== 0) { stale(); return; } // دکمه فرمِ تمام‌شده یا لغوشده

  if (data === 'bk') {
    tgAnswerCb_(cb.id);
    if (d.editing && !FORM_SUBSTEPS[step] && !firstMissing_(d)) {
      d.editing = false; saveSession_(chatId, tg, s);
      showPreview_(chatId, tg, s);
    } else showStep_(chatId, tg, s, prevStep_(d, step), mid);
    return;
  }
  if (data === 'pd') {
    if (step !== 'photos') { stale(); return; }
    if (!(d.photos || []).length) { tgAnswerCb_(cb.id, TXT.PHOTO_NEED_ONE, true); return; }
    tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'photos', 0); return;
  }
  if (p === 'ct') {
    var nt = /^\d+$/.test(String(v)) ? CFG.CLASS_TYPES[num_(v)] : null;
    if (step !== 'class' || !nt) { stale(); return; }
    if (nt !== d.class_type) resetTypeFields_(d); // نوع عوض شد: بخش‌های وابسته به نوع دوباره پرسیده می‌شوند
    d.class_type = nt; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id);
    goNext_(chatId, tg, s, 'class', mid); return;
  }
  if (p === 'sb') {
    var subs = CLASS_SUBS[d.class_type] || [], sub = /^\d+$/.test(String(v)) ? subs[num_(v)] : null;
    if (step !== 'sub' || !sub) { stale(); return; }
    if (d.class_type === 'توانمندسازی' && sub !== d.class_sub) { delete d.operation_name; delete d.place; delete d.link; } // حضوری ↔ مجازی
    d.class_sub = sub; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id, subLabel_(sub));
    goNext_(chatId, tg, s, 'sub', mid); return;
  }
  if (data === 'op0') {
    if (step !== 'operation') { stale(); return; }
    d.operation_name = ''; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'operation', mid); return;
  }
  if (p === 'sj') { // پیشنهاد موضوع
    var subj = /^\d+$/.test(String(v)) ? SUBJECT_SUGGESTIONS[num_(v)] : null;
    if (step !== 'subject' || !subj) { stale(); return; }
    d.subject = subj; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id, subj);
    goNext_(chatId, tg, s, 'subject', mid); return;
  }
  if (p === 'tp') { if (step !== 'teacher') { stale(); return; } d.tp = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'teacher', mid); return; }
  if (data === 'tn') { if (step !== 'teacher') { stale(); return; } tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'teacherNew', mid); return; }
  if (p === 'te') {
    if (step !== 'teacher') { stale(); return; }
    var t = getInstructorInCity_(num_(v), u.city_id); // فقط شهرستان خود کارمند
    if (!t) { tgAnswerCb_(cb.id, 'این نام در فهرست شهرستان شما یافت نشد.', true); return; }
    d.instructor_id = t.instructor_id; d.instructor_name = t.name;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'teacher', mid); return;
  }
  if (data === 'dc') { if (step !== 'date') { stale(); return; } tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'year', mid); return; }
  if (p === 'dq') {
    if (step !== 'date') { stale(); return; }
    var dq = jalaliOffset_(Math.max(-2, Math.min(0, num_(v))));
    d.event_year = dq.jy; d.event_month = dq.jm; d.event_day = dq.jd;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'date', mid); return;
  }
  if (p === 'dy') { if (step !== 'year') { stale(); return; } d.tmpY = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'month', mid); return; }
  if (p === 'dm') { if (step !== 'month') { stale(); return; } d.tmpM = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'day', mid); return; }
  if (p === 'dd') {
    if (step !== 'day') { stale(); return; }
    if (isFutureJalali_(d.tmpY, d.tmpM, num_(v))) {
      tgAnswerCb_(cb.id, TXT.DATE_FUTURE, true);
      showStep_(chatId, tg, s, 'date', mid); return;
    }
    d.event_year = d.tmpY; d.event_month = d.tmpM; d.event_day = num_(v);
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'date', mid); return;
  }
  if (p === 'th') { if (step !== 'hour') { stale(); return; } d.hour = v; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); showStep_(chatId, tg, s, 'minute', mid); return; }
  if (p === 'tm') {
    if (step !== 'minute') { stale(); return; }
    d.event_time = (d.hour || '00') + ':' + v;
    saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'hour', mid); return;
  }
  if (p === 'du') { if (step !== 'duration') { stale(); return; } d.duration_min = num_(v); saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'duration', mid); return; }
  if (p === 'au') {
    var au = CFG.AUDIENCES[num_(v)];
    if (step !== 'audience' || !au) { stale(); return; }
    d.audience = au; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'audience', mid); return;
  }
  if (p === 'pf') {
    var pf = /^\d+$/.test(String(v)) ? platformOptions_(d)[num_(v)] : null;
    if (step !== 'platform' || !pf) { stale(); return; }
    d.place = pf; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id, pf); goNext_(chatId, tg, s, 'platform', mid); return;
  }
  if (data === 'lk0') {
    if (step !== 'link') { stale(); return; }
    d.link = ''; saveSession_(chatId, tg, s); tgAnswerCb_(cb.id); goNext_(chatId, tg, s, 'link', mid); return;
  }
  if (data === 'pe') { tgAnswerCb_(cb.id); tgSend_(chatId, TXT.EDIT_ASK, editFieldsKb_(d)); return; }
  if (data === 'pw') { tgAnswerCb_(cb.id); showPreview_(chatId, tg, s); return; }
  if (p === 'ef') {
    if (formFlow_(d).indexOf(v) === -1) { stale(); return; } // بخشی که در این نوع نیست
    d.editing = true;
    if (v === 'photos') { d.replacePhotos = true; tgAnswerCb_(cb.id, 'عکس‌های جدید را بفرستید؛ با اولین عکس جدید جایگزین قبلی‌ها می‌شوند. اگر پشیمان شدید «بازگشت» بزنید تا قبلی‌ها بمانند.'); }
    else tgAnswerCb_(cb.id);
    saveSession_(chatId, tg, s);
    showStep_(chatId, tg, s, v, 0);
    return;
  }
  if (data === 'ps') { if (step !== 'preview') { stale(); return; } submitReport_(cb, s, u); return; }
  tgAnswerCb_(cb.id);
}

function submitReport_(cb, s, u) {
  var chatId = cb.message.chat.id, tg = cb.from.id, d = s.data;
  if (!submitAllowed_(tg)) {
    tgAnswerCb_(cb.id, '⚠️ خیلی تند! در هر دقیقه حداکثر ' + CFG.MAX_REPORTS_PER_MINUTE + ' گزارش می‌توانید برای بررسی بفرستید. کمی صبر کنید.', true);
    return;
  }
  fillAutoFields_(d);
  if (firstMissing_(d)) { tgAnswerCb_(cb.id); showPreview_(chatId, tg, s); return; }
  var city = getCityById_(u.city_id);
  var caption = captionFromData_(d, city ? city.hashtag : '', todayJalaliStr_());
  if (caption.length > CFG.CAPTION_LIMIT) { tgAnswerCb_(cb.id, 'کپشن بلندتر از حد مجاز است؛ موضوع یا بستر / مکان را کوتاه کنید.', true); return; }
  tgAnswerCb_(cb.id, 'در حال ارسال برای بررسی…');
  var flow = formFlow_(d);
  function has(x) { return flow.indexOf(x) !== -1; }
  var rep = { // فقط بخش‌های همین نوع ذخیره می‌شوند (بقیه خالی)
    submitter_telegram_id: tg, employee_name: u.full_name, city_id: u.city_id,
    instructor_id: has('teacher') ? d.instructor_id || '' : '', instructor_name: has('teacher') ? d.instructor_name || '' : '',
    status: 'pending', class_type: d.class_type, class_sub: has('sub') ? d.class_sub : '',
    operation_name: has('operation') ? d.operation_name || '' : '',
    subject: d.subject,
    event_jalali: formatJalali_(d.event_year, d.event_month, d.event_day),
    event_gregorian: jalaliToGregStr_(d.event_year, d.event_month, d.event_day),
    event_year: d.event_year, event_month: d.event_month, event_day: d.event_day,
    event_time: has('hour') ? d.event_time : '', duration_min: has('duration') ? d.duration_min : '',
    participants: has('participants') || has('views') ? d.participants : '',
    audience: has('audience') ? d.audience : '', place: has('place') || has('platform') ? d.place : '',
    link: has('link') ? d.link || '' : '', form_v: FORM_VERSION,
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

// اصلاح و ارسال مجدد — فرم با داده‌های قبلی باز می‌شود (نه از صفر). گزارش قدیمی (پیش از کپشن‌های جداگانه) با فرم تازه
// باز می‌شود و بخش‌های تازه‌اش (مثلاً زیرشاخه یا لینک) پرسیده می‌شود.
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
  var s = getSession_(chatId, tg), v2 = isV2_(r);
  s.data = {
    photos: getReportPhotos_(rid).map(function (p) { return { file_id: String(p.file_id), file_unique_id: String(p.file_unique_id || '') }; }),
    editing: false, rid: rid, tp: 0,
    class_type: String(r.class_type || '').replace(/^#/, ''), operation_name: String(r.operation_name || ''), subject: String(r.subject || ''),
    instructor_id: r.instructor_id, instructor_name: String(r.instructor_name || ''),
    event_year: num_(r.event_year), event_month: num_(r.event_month), event_day: num_(r.event_day),
    event_time: String(cellToStr_(r.event_time) || ''), hour: String(cellToStr_(r.event_time) || '00:00').split(':')[0],
    duration_min: num_(r.duration_min), participants: num_(r.participants),
    audience: String(r.audience || ''), place: String(r.place || '')
  };
  if (r.class_sub) s.data.class_sub = String(r.class_sub);
  if (v2) s.data.link = String(r.link || ''); // گزارش قدیمی لینک نداشت ← پرسیده می‌شود
  s.state = 'form:preview';
  saveSession_(chatId, tg, s);
  tgAnswerCb_(cb.id);
  tgSend_(chatId, 'اطلاعات قبلی گزارش بازیابی شد؛ پیش‌نمایش را ببینید و با «✏️ ویرایش» اصلاح کنید:');
  showPreview_(chatId, tg, s);
}
