// Review.gs — بررسی در گروه ادمین‌ها: تایید و انتشار / رد چنددلیلی / رد دستی + قفل اولین تصمیم + انتشار در کانال

function reviewKb_(rid) {
  return ikb_([
    [btn_('✅ تایید و انتشار', 'ok:' + rid)],
    [btn_('❌ رد (انتخاب دلیل)', 'rj:' + rid), btn_('✉️ رد با پیام دستی', 'mn:' + rid)]
  ]);
}
function rejectReasonsKb_(rid, selected, hasManual) {
  var rows = CFG.REJECT_REASONS.map(function (r, i) {
    return [btn_((selected.indexOf(i) !== -1 ? '✅ ' : '') + r, 'rs:' + rid + ':' + i)];
  });
  rows.push([btn_(hasManual ? '📝 توضیح دستی ثبت شده ✓ (برای تغییر بزنید)' : '➕ افزودن توضیح دستی', 'rm:' + rid)]);
  rows.push([btn_('ثبت رد', 'rd:' + rid), btn_('↩️ بازگشت', 'rb:' + rid)]);
  return ikb_(rows);
}

function sendReportToReview_(rep) {
  var groupId = getSettingNum_('review_group_id');
  if (!groupId) { log_('warn', 'review-group-missing', 'گزارش ' + rep.report_id, ''); return false; }
  var photos = getReportMedia_(rep.report_id); // عکس، ویدیو، فایل، صوت…
  // در گروه بررسی «تاریخ درج» امروز فرض می‌شود؛ هنگام تایید دوباره محاسبه می‌شود.
  var caption = captionFromReport_(rep, todayJalaliStr_());
  var album = tgSendAlbum_(groupId, photos, caption);
  if (!album) return false;
  var city = getCityById_(rep.city_id);
  var info = '🆕 گزارش جدید برای بررسی\nکارمند: ' + rep.employee_name +
    '\nشهرستان: ' + (city ? city.name : '') + '\nشماره گزارش: ' + rep.report_id;
  duplicateHints_(rep.city_id, String(rep.event_jalali), String(rep.event_time), rep.subject,
    photos.map(function (p) { return p.file_unique_id; }), rep.report_id).forEach(function (h) {
      info += '\n⚠️ احتمال گزارش تکراری: ' + h;
    });
  var infoMsg = tgSend_(groupId, info, reviewKb_(rep.report_id));
  if (!infoMsg) return false;
  updateReport_(rep.report_id, {
    review_chat_id: groupId,
    review_album_ids: JSON.stringify(album.map(function (m) { return m.message_id; })),
    review_info_id: infoMsg.message_id
  });
  return true;
}

function publishReport_(rep) {
  var channelId = getSettingNum_('channel_id');
  if (!channelId) throw new Error('کانال در Settings تنظیم نشده است');
  var photos = getReportMedia_(rep.report_id);
  var publishDate = todayJalaliStr_(); // «تاریخ درج» در لحظه تایید
  var caption = captionFromReport_(rep, publishDate);
  var album = tgSendAlbum_(channelId, photos, caption);
  if (!album) throw new Error('ارسال عکس/فایل‌ها به کانال ناموفق بود');
  // انتشار همزمان در کانال‌های اضافه (📡 کانال‌ها در پنل) — شکست آن‌ها جلوی تایید را نمی‌گیرد، فقط هشدار می‌دهد
  var extra = {}, failed = [];
  extraChannels_().forEach(function (c) {
    var a2 = tgSendAlbum_(num_(c.id), photos, caption);
    if (a2) extra[String(c.id)] = a2.map(function (m) { return m.message_id; });
    else failed.push(c.title || String(c.id));
  });
  updateReport_(rep.report_id, {
    publish_jalali: publishDate, published_caption: caption,
    channel_message_ids: JSON.stringify(album.map(function (m) { return m.message_id; })),
    extra_channel_msgs: JSON.stringify(extra)
  });
  if (failed.length) {
    var gid = getSettingNum_('review_group_id');
    if (gid) tgSend_(gid, '⚠️ گزارش ' + rep.report_id + ' در کانال اصلی منتشر شد ولی در ' + failed.map(function (t) { return '«' + t + '»'; }).join('، ') +
      ' نه.\nبات در آن کانال ادمین نیست یا اجازه ارسال پیام ندارد (پنل /admin ← 📡 کانال‌ها).');
  }
  return album;
}

function reviewHeader_(rep) {
  var city = getCityById_(rep.city_id);
  return '🆕 گزارش جدید برای بررسی\nکارمند: ' + rep.employee_name +
    '\nشهرستان: ' + (city ? city.name : '') + '\nشماره گزارش: ' + rep.report_id;
}
// بعد از تایید/رد: همان پیام بررسی ← نتیجه + سطر امتیاز + دکمه‌های ۱ تا ۵ (پیام تازه‌ای فرستاده نمی‌شود)
function editReviewInfoFinal_(rep, suffix) {
  if (rep.review_chat_id && rep.review_info_id) {
    tgEdit_(num_(rep.review_chat_id), num_(rep.review_info_id), reviewHeader_(rep) + suffix + '\n\n' + SCORE_ASK, scoreKb_(rep.report_id, 0));
  }
}

// ---------- امتیاز گزارش (۱ تا ۵) ----------
// امتیاز در همان پیام بررسی گروه و همان پیام «تایید/رد» همکار نوشته می‌شود؛ تا وقتی گزارش دوباره فرستاده نشده، قابل اصلاح است.
var SCORE_ASK = '⭐️ امتیاز گزارش (۱ ضعیف تا ۵ عالی): هنوز ثبت نشده';
function scoreKb_(rid, cur) {
  return ikb_([[1, 2, 3, 4, 5].map(function (n) { return btn_((n === num_(cur) ? '✅ ' : '') + n, 'sc:' + rid + ':' + n); })]);
}
function scoreLine_(rep) { var s = num_(rep && rep.score); return s ? '\n⭐️ امتیاز گزارش: ' + s + ' از 5' : ''; }
function approvedNoticeText_(rep) {
  return '✅ گزارش شما تایید و در کانال منتشر شد 🎉\nموضوع: ' + rep.subject + scoreLine_(rep);
}
function rejectedNoticeText_(rep, why) {
  return '❌ گزارش شما رد شد.\nموضوع: ' + rep.subject + scoreLine_(rep) + '\n\nدلیل‌ها:\n' + why +
    '\n\nمی‌توانید اصلاح کنید و دوباره بفرستید؛ اطلاعات قبلی حفظ می‌شود.';
}
function resubmitKb_(rid) { return ikb_([[btn_('✏️ اصلاح و ارسال مجدد', 're:' + rid)]]); }
function lastRejectWhy_(rid) {
  var h = readTable_('RejectHistory').filter(function (r) { return num_(r.report_id) === num_(rid); }).pop();
  if (!h) return reasonsText_([], '');
  var reasons = [];
  try { reasons = JSON.parse(String(h.reasons_json || '[]')); } catch (e) { }
  return reasonsText_(reasons, String(h.manual_text || ''));
}
// ثبت/اصلاح امتیاز ← سطر امتیاز در پیام همکار (ویرایش همان پیام). خروجی: '' یا متن خطا
function setReportScore_(rid, n, adminTg) {
  var rep = getReport_(rid);
  if (!rep || (rep.status !== 'approved' && rep.status !== 'rejected')) return 'این گزارش دیگر قابل امتیازدهی نیست.';
  if (!(n >= 1 && n <= 5)) return 'امتیاز باید بین 1 تا 5 باشد.';
  updateReport_(rid, { score: n, score_by: adminTg });
  rep = getReport_(rid);
  if (num_(rep.notice_msg_id)) {
    if (rep.status === 'approved') tgEdit_(num_(rep.submitter_telegram_id), num_(rep.notice_msg_id), approvedNoticeText_(rep));
    else tgEdit_(num_(rep.submitter_telegram_id), num_(rep.notice_msg_id), rejectedNoticeText_(rep, lastRejectWhy_(rid)), resubmitKb_(rid));
  }
  return '';
}
function reasonsText_(reasons, manual) {
  var parts = reasons.map(function (r) { return '• ' + r; });
  if (manual) parts.push('• توضیح ادمین: ' + manual);
  return parts.length ? parts.join('\n') : '• بدون دلیل مشخص';
}
function finalizeReject_(rid, adminTg, adminName, reasons, manual) {
  if (!tryLockDecision_(rid, adminTg, 'rejected')) return false;
  appendRow_('RejectHistory', {
    report_id: rid, admin_telegram_id: adminTg,
    reasons_json: JSON.stringify(reasons), manual_text: manual || '', created_at: nowStr_()
  });
  var rep = getReport_(rid), why = reasonsText_(reasons, manual);
  editReviewInfoFinal_(rep, '\n\n❌ رد شد — توسط ' + adminName + '\nدلیل‌ها: ' + why.replace(/\n/g, '؛ '));
  var notice = tgSend_(rep.submitter_telegram_id, rejectedNoticeText_(rep, why), resubmitKb_(rid));
  if (notice) updateReport_(rid, { notice_msg_id: notice.message_id });
  return true;
}

// ---------- دکمه‌های بررسی (فقط ادمین‌ها) ----------
function handleReviewCb_(cb, s, data) {
  var tg = cb.from.id, chatId = cb.message.chat.id, mid = cb.message.message_id;
  if (!isAdminTg_(tg)) { tgAnswerCb_(cb.id, TXT.NO_ACCESS, true); return; }
  var parts = data.split(':'), p = parts[0], rid = num_(parts[1]);

  if (p === 'ok') { // ✅ تایید و انتشار
    if (!tryLockDecision_(rid, tg, 'approved')) { tgAnswerCb_(cb.id, TXT.DECIDED_ALREADY, true); return; }
    tgAnswerCb_(cb.id, 'در حال انتشار در کانال…');
    try {
      publishReport_(getReport_(rid));
    } catch (e) {
      unlockDecision_(rid);
      log_('error', 'publish', String(e.message || e), '');
      tgSend_(chatId, '⚠️ انتشار گزارش ' + rid + ' در کانال ناموفق بود. مطمئن شوید بات در کانال ادمین است و اجازه ارسال دارد؛ گزارش به حالت «در انتظار بررسی» برگشت تا دوباره تلاش کنید.');
      return;
    }
    editReviewInfoFinal_(getReport_(rid), '\n\n✅ تایید و منتشر شد — توسط ' + fullNameOf_(cb.from));
    var rep = getReport_(rid);
    var notice = tgSend_(rep.submitter_telegram_id, approvedNoticeText_(rep));
    if (notice) updateReport_(rid, { notice_msg_id: notice.message_id });
    return;
  }
  if (p === 'sc') { // ⭐️ امتیاز ۱ تا ۵ — هر ادمین گروه؛ با زدن عدد دیگر اصلاح می‌شود
    var n = num_(parts[2]), rp = getReport_(rid);
    if (!rp || num_(rp.review_info_id) !== num_(mid) || (rp.status !== 'approved' && rp.status !== 'rejected')) {
      tgAnswerCb_(cb.id, 'این گزارش دیگر قابل امتیازدهی نیست (دوباره فرستاده یا حذف شده است).', true);
      tgEditMarkup_(chatId, mid, { inline_keyboard: [] });
      return;
    }
    if (num_(rp.score) === n) { tgAnswerCb_(cb.id, 'امتیاز ' + n + ' از قبل ثبت است.'); return; }
    var err = setReportScore_(rid, n, tg);
    if (err) { tgAnswerCb_(cb.id, err, true); return; }
    tgAnswerCb_(cb.id, '⭐️ امتیاز ' + n + ' از 5 ثبت شد');
    var base = String(cb.message.text || reviewHeader_(rp)).replace(/\n\n⭐️ امتیاز گزارش[^\n]*$/, '');
    tgEdit_(chatId, mid, base + '\n\n⭐️ امتیاز گزارش: ' + n + ' از 5 — توسط ' + fullNameOf_(cb.from), scoreKb_(rid, n));
    return;
  }
  if (p === 'rj') { // منوی دلایل رد
    var r = getReport_(rid);
    if (!r || r.status !== 'pending') { tgAnswerCb_(cb.id, TXT.DECIDED_ALREADY, true); return; }
    s.state = 'review:rejecting';
    s.data = { rid: rid, sel: [], manual: '', manualOnly: false };
    saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id, TXT.REJECT_ASK);
    tgEditMarkup_(chatId, mid, rejectReasonsKb_(rid, [], false));
    return;
  }
  if (p === 'rs') { // انتخاب/لغو یک دلیل
    var idx = num_(parts[2]);
    s.data.sel = s.data.sel || [];
    var k = s.data.sel.indexOf(idx);
    if (k === -1) s.data.sel.push(idx); else s.data.sel.splice(k, 1);
    saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    tgEditMarkup_(chatId, mid, rejectReasonsKb_(rid, s.data.sel, !!s.data.manual));
    return;
  }
  if (p === 'rb') { // بازگشت از منوی رد
    clearSession_(chatId, tg);
    tgAnswerCb_(cb.id);
    tgEditMarkup_(chatId, mid, reviewKb_(rid));
    return;
  }
  if (p === 'rd') { // ثبت رد
    var reasons = (s.data.sel || []).map(function (i) { return CFG.REJECT_REASONS[i]; });
    var manual = String(s.data.manual || '').trim();
    if (!reasons.length && !manual) { tgAnswerCb_(cb.id, TXT.REJECT_NONE, true); return; }
    var done = finalizeReject_(rid, tg, fullNameOf_(cb.from), reasons, manual);
    clearSession_(chatId, tg);
    tgAnswerCb_(cb.id, done ? 'گزارش رد شد و به کارمند اطلاع داده شد.' : TXT.DECIDED_ALREADY, !done);
    return;
  }
  if (p === 'mn' || p === 'rm') { // رد با پیام دستی / افزودن توضیح دستی
    var r2 = getReport_(rid);
    if (!r2 || r2.status !== 'pending') { tgAnswerCb_(cb.id, TXT.DECIDED_ALREADY, true); return; }
    s.state = 'review:manual';
    s.data.rid = rid;
    s.data.manualOnly = (p === 'mn');
    if (p === 'mn') s.data.sel = [];
    saveSession_(chatId, tg, s);
    tgAnswerCb_(cb.id);
    tgSend_(chatId, 'گزارش ' + rid + ':\n' + TXT.REJECT_MANUAL);
    return;
  }
  tgAnswerCb_(cb.id);
}

// متن دستی دلیل رد (پیام بعدی ادمین در گروه)
function handleReviewManualText_(msg, s) {
  var tg = msg.from.id, chatId = msg.chat.id;
  if (!isAdminTg_(tg)) return;
  var rid = num_(s.data.rid), manual = msg.text.trim();
  if (!rid) { clearSession_(chatId, tg); return; }
  if (s.data.manualOnly) {
    var done = finalizeReject_(rid, tg, fullNameOf_(msg.from), [], manual);
    clearSession_(chatId, tg);
    tgSend_(chatId, done ? '✅ گزارش ' + rid + ' رد شد و دلیل برای کارمند ارسال شد.' : TXT.DECIDED_ALREADY);
    return;
  }
  s.data.manual = manual;
  s.state = 'review:rejecting';
  saveSession_(chatId, tg, s);
  tgSend_(chatId, '✅ توضیح دستی ثبت شد. حالا روی پیام بررسی گزارش، «ثبت رد» را بزنید.');
  var rep = getReport_(rid);
  if (rep && rep.review_info_id) {
    tgEditMarkup_(chatId, num_(rep.review_info_id), rejectReasonsKb_(rid, s.data.sel || [], true));
  }
}
