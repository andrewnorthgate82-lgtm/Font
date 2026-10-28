// Tests.gs — تست‌های پذیرش فاز ۱ تا ۴. در ویرایشگر Apps Script تابع runTests را Run کنید؛
// در Execution log باید خروجی «26 passed» را ببینید (در صورت شکست، علت هر مورد نوشته می‌شود).
// تست‌های ۱۲ تا ۱۶ مال پنل مدیریت فاز ۲ (Admin.gs)، ۱۷ تا ۲۰ مال خودکارهای فاز ۳ و ۲۱ تا ۲۳ مال فاز ۴ (دوره ۲۱ تا ۲۰، نقش‌ها، اکسل) هستند؛
// هیچ‌کدام پیامی به تلگرام نمی‌فرستند و به Google Drive دست نمی‌زنند.
// نکته: پس از هر به‌روزرسانی کد، یک بار setup()‎ را هم Run کنید (بی‌خطر و تکراری است) تا فرمت متنی ستون‌ها اعمال شود.

function runTests() {
  var passed = 0, total = 0, failed = [];
  function check(name, fn) {
    total++;
    try { fn(); passed++; Logger.log('✅ ' + name); }
    catch (e) { failed.push(name + ': ' + (e && e.message)); Logger.log('❌ ' + name + ' → ' + (e && e.message)); }
  }
  function eq(a, b, what) { if (a !== b) throw new Error(what + ' — got: ' + a + ' want: ' + b); }
  function ok(v, what) { if (!v) throw new Error(what); }

  check('۱) قالب دقیق کپشن', function () {
    var got = buildCaption_({
      publish_jalali: '1405/07/25', city_hashtag: '#نجف_آباد', class_type: 'حضوری',
      operation_name: '', subject: 'آموزش سواد رسانه', instructor_name: 'محمد احمدی',
      event_year: 1405, event_month: 7, event_day: 23, event_time: '21:00',
      duration_min: 90, participants: 35, audience: 'عموم', place: 'مسجد جامع نجف آباد'
    });
    var want = [
      'تاریخ درج در کانال :1405/07/25', '⚜️ نام استان : #اصفهان', '✅  معاونت : #نسرا',
      '🗓  ماه : #مهر1405', '⚜️ ناحیه برگزار کننده : #نجف_آباد', 'نوع کلاس/ برنامه:  #حضوری',
      '💢 نام عملیات : ', '📄 موضوع : آموزش سواد رسانه', '👨‍🏫مدرس یا سخنران : محمد احمدی',
      'تاریخ برگزاری:1405/07/23', '⏰ساعت برگزاری : 21:00', '⏳مدت زمان برگزاری :90 دقیقه',
      '🚻 تعداد نفرات شرکت کننده :35 نفر', '👥 نوع مخاطبین : #عموم',
      '⭕️ بستر / مکان برگزاری : ', 'مسجد جامع نجف آباد'
    ].join('\n');
    eq(got, want, 'کپشن کاراکتربه‌کاراکتر');
    ok(got.length <= CFG.CAPTION_LIMIT, 'طول کپشن نمونه از ۱۰۲۴ کمتر است');
  });

  check('۲) نرمال‌سازی شماره تلفن', function () {
    eq(normalizePhone_('+989123456789'), '09123456789', '+98');
    eq(normalizePhone_('00989123456789'), '09123456789', '0098');
    eq(normalizePhone_('989123456789'), '09123456789', '98…');
    eq(normalizePhone_('9123456789'), '09123456789', 'بدون صفر');
    eq(normalizePhone_('۰۹۱۲۳۴۵۶۷۸۹'), '09123456789', 'ارقام فارسی');
    eq(normalizePhone_('12345'), null, 'کوتاه نامعتبر');
    eq(parseIntFa_('۳۵'), 35, 'عدد فارسی');
    ok(validHashtag_('#آران_و_بیدگل'), 'هشتگ معتبر');
    ok(!validHashtag_('#امام حسین'), 'هشتگ با فاصله نامعتبر');
  });

  check('۳) تقویم شمسی و کبیسه اسفند (تاریخ‌های مرجع)', function () {
    var g = jalaliToGregorian_(1403, 1, 1);
    eq(g.gy + '/' + g.gm + '/' + g.gd, '2024/3/20', 'اول فروردین ۱۴۰۳');
    var g2 = jalaliToGregorian_(1403, 12, 30);
    eq(g2.gy + '/' + g2.gm + '/' + g2.gd, '2025/3/20', '۳۰ اسفند ۱۴۰۳ (کبیسه) ↔ 2025-03-20');
    var g3 = jalaliToGregorian_(1405, 8, 2);
    eq(g3.gy + '/' + g3.gm + '/' + g3.gd, '2026/10/24', '۲ آبان ۱۴۰۵ ↔ 2026-10-24');
    var j = gregorianToJalali_(2026, 10, 24);
    eq(j.jy + '/' + j.jm + '/' + j.jd, '1405/8/2', 'برعکس: 2026-10-24 ← شمسی');
    var j2 = gregorianToJalali_(2025, 3, 20);
    eq(j2.jy + '/' + j2.jm + '/' + j2.jd, '1403/12/30', 'برعکس: 2025-03-20 ← ۳۰ اسفند');
    eq(formatJalali_(1405, 7, 23), '1405/07/23', 'قالب تاریخ');
    eq(monthTag_(1405, 7), 'مهر1405', 'هشتگ ماه');
    eq(daysInJalaliMonth_(1403, 12), 30, 'اسفند ۱۴۰۳ کبیسه');
    eq(daysInJalaliMonth_(1404, 12), 29, 'اسفند ۱۴۰۴ غیرکبیسه');
    ok(isFutureJalali_(1499, 1, 1), 'آینده تشخیص داده می‌شود');
  });

  check('۴) دقیقاً ۳۲ شهرستان با هشتگ‌های درست', function () {
    var cities = sheet_('Cities') ? getCities_() : [];
    if (!cities.length) { // قبل از setup فقط خود بذر را بررسی کن
      eq(CITIES_SEED.length, 32, 'تعداد بذر');
      return;
    }
    eq(cities.length, 32, 'تعداد شهرستان‌های برگه Cities');
    CITIES_SEED.forEach(function (c) {
      var found = getCityById_(c[0]);
      ok(found, 'شهرستان ' + c[1]);
      eq(found.name, c[1], 'نام ' + c[1]);
      eq(found.hashtag, c[2], 'هشتگ ' + c[1]);
      ok(validHashtag_(c[2]), 'اعتبار ' + c[2]);
    });
  });

  check('۵) خواندن لیست مجاز (Whitelist)', function () {
    var fakeRows = [
      { phone: '9123456789', full_name: 'علی رضایی', city_name: 'کاشان', city_id: '' },
      { phone: 'بد', full_name: 'x', city_name: 'کاشان', city_id: '' }
    ];
    var cities = CITIES_SEED.map(function (c) { return { city_id: c[0], name: c[1], hashtag: c[2] }; });
    eq(resolveWhitelistRow_(fakeRows[0], cities), 23, 'کاشان = ۲۳');
    eq(normalizePhone_(fakeRows[0].phone), '09123456789', 'شماره بدون صفر در شیت درست می‌شود');
    eq(normalizePhone_(fakeRows[1].phone), null, 'شماره بد رد می‌شود');
    // مسیر واقعی: اگر شماره نمونه در برگه Whitelist باشد، findWhitelist_ همان را برمی‌گرداند
    var real = readTable_('Whitelist');
    if (real.length) {
      var p = normalizePhone_(real[0].phone);
      if (p) ok(!!findWhitelist_(p), 'اولین شماره واقعی برگه Whitelist خوانده می‌شود');
    }
  });

  check('۶) نرمال‌سازی نام مدرس و جلوگیری از تکراری (منطق)', function () {
    eq(normCmp_('محمد احمدي'), normCmp_('محمد  احمدی'), 'ي و فاصله اضافه یکی می‌شود');
    eq(normalizeName_('علي كريمي'), 'علی کریمی', 'ي→ی و ك→ک');
  });

  check('۷) قفل اولین تصمیم + هشدار تکراری + ارسال مجدد با شمارنده', function () {
    var uid = 999000111, cityId = 23, rid = 0;
    try {
      rid = createReport_({
        submitter_telegram_id: uid, employee_name: 'کاربر تست', city_id: cityId,
        instructor_id: '', instructor_name: 'مدرس تست', status: 'pending',
        class_type: 'حضوری', operation_name: '', subject: 'موضوع تست پذیرش',
        event_jalali: '1405/07/23', event_gregorian: jalaliToGregStr_(1405, 7, 23),
        event_year: 1405, event_month: 7, event_day: 23, event_time: '21:00',
        duration_min: 60, participants: 20, audience: 'عموم', place: 'مسجد',
        publish_jalali: '', published_caption: '', channel_message_ids: '[]',
        review_chat_id: '', review_album_ids: '[]', review_info_id: '',
        decided_by: '', decided_at: '', resubmit_count: 0
      }, [{ file_id: 'TESTFILE', file_unique_id: 'TESTUNIQUE' }]);
      var hints = duplicateHints_(cityId, '1405/07/23', '21:00', 'موضوع تست پذیرش', ['TESTUNIQUE'], 0);
      ok(hints.length > 0, 'هشدار تکراری باید بیاید');
      ok(tryLockDecision_(rid, 111, 'rejected'), 'اولین ادمین قفل می‌کند');
      ok(!tryLockDecision_(rid, 222, 'approved'), 'دومین ادمین دیگر نمی‌تواند تصمیم بگیرد');
      var before = getReport_(rid);
      updateReport_(rid, { status: 'pending', decided_by: '', decided_at: '', resubmit_count: num_(before.resubmit_count) + 1 });
      var after = getReport_(rid);
      eq(after.status, 'pending', 'بعد از اصلاحیه دوباره pending');
      eq(num_(after.resubmit_count), 1, 'شمارنده ارسال مجدد = ۱');
    } finally {
      if (rid) { // پاک‌سازی ردیف‌های تست
        var sh = sheet_('Reports'), r = getReport_(rid);
        if (r) sh.deleteRow(r._row);
        setReportPhotos_(rid, []);
      }
    }
  });

  check('۸) یک عکس با sendPhoto و ۲+ عکس با sendMediaGroup', function () {
    eq(albumMethodFor_(1), 'sendPhoto', 'تک‌عکس');
    eq(albumMethodFor_(2), 'sendMediaGroup', 'دو عکس');
    eq(albumMethodFor_(5), 'sendMediaGroup', 'پنج عکس');
  });

  check('۹) ساعت، تاریخ شمسی، کلید نشست و شماره در Sheet «رشته» می‌مانند', function () {
    var keys = ['_test_time', '_test_date', '_test_sess', '_test_phone', '_test_formula'];
    var vals = ['21:00', '1405/07/23', '123:456', '09123456789', '=1+1'];
    var sh = sheet_('Settings');
    try {
      keys.forEach(function (k, i) { setSetting_(k, vals[i], 'ردیف موقت تست'); });
      SpreadsheetApp.flush();
      keys.forEach(function (k, i) {
        var row = findOne_('Settings', function (r) { return String(r.key) === k; });
        ok(row, 'ردیف ' + k);
        var raw = sh.getRange(row._row, 2).getValue(); // مقدار خام سلول
        eq(typeof raw, 'string', 'نوعِ ' + vals[i] + ' باید رشته باشد (اگر نشد setup را یک بار Run کنید)');
        eq(String(raw), vals[i], 'مقدارِ ' + k);
        // کلید نشست دقیقاً با همین فرم خوانده شود:
        if (k === '_test_sess') eq(sessKey_(123, 456), '123:456', 'قالب کلید نشست');
      });
    } finally {
      keys.forEach(function (k) {
        var row = findOne_('Settings', function (r) { return String(r.key) === k; });
        if (row) sh.deleteRow(row._row);
      });
      try { cache_().remove('settingsMap'); } catch (e) { }
    }
  });

  check('۱۰) حذف تکراری update_id (مخزن seen زیر قفلِ doPost)', function () {
    var id = 987650000 + Math.floor(Math.random() * 100000);
    ok(seenUpdate_(id) === false, 'دفعه اول نباید seen باشد');
    ok(seenUpdate_(id) === true, 'دفعه دوم (تلاش مجدد ورکر) باید تکراری شناخته شود');
  });

  check('۱۱) کاربر disabled عبور نمی‌کند، rejected خودکار نمی‌شود، pending بدون شهرستان ادامه پیدا می‌کند', function () {
    var tgDis = 999000333, tgRej = 999000334, tgHalf = 999000444;
    function fakeContactMsg(tg, phoneIntl) {
      return {
        chat: { id: tg, type: 'private' }, from: { id: tg, first_name: 'تست' },
        contact: { user_id: tg, phone_number: phoneIntl }
      };
    }
    function deleteTestUser(tg) {
      var u = getUserById_(getUserByTg_(tg) ? getUserByTg_(tg).user_id : 0);
      if (u) sheet_('Users').deleteRow(u._row);
      try { cache_().remove('user_' + tg); } catch (e) { }
      var s = getSession_(tg, tg);
      if (s._row) sheet_('Sessions').deleteRow(s._row);
      try { cache_().remove('sess_' + sessKey_(tg, tg)); } catch (e) { }
    }
    var wlRows = [], tempAdminRow = 0;
    try {
      // این تست به وجود سوپرادمین وابسته نباشد (اگر هنوز ‎/claim‎ نشده، موقتاً بساز و بعد پاک کن):
      if (!hasSuperadmin_()) {
        tempAdminRow = appendRow_('Admins', { telegram_id: 999000999, full_name: 'سوپرادمین موقت تست', role: 'superadmin', is_active: 1, added_at: nowStr_() });
        bustCache_();
      }
      // کاربر disabled با شمارهٔ حاضر در Whitelist: هیچ تغییری نباید بگیرد
      var du = ensureUser_(tgDis, 'تست غیرفعال');
      updateUser_(du.user_id, tgDis, { status: 'disabled' });
      wlRows.push(appendRow_('Whitelist', { phone: '09120003333', full_name: 'تست غیرفعال', city_name: 'کاشان', city_id: '' }));
      wlRows.push(appendRow_('Whitelist', { phone: '09120003334', full_name: 'تست ردشده', city_name: 'کاشان', city_id: '' }));
      bustCache_();
      handleContact_(fakeContactMsg(tgDis, '+989120003333'));
      var du2 = getUserByTg_(tgDis);
      eq(du2.status, 'disabled', 'disabled با کانتکت هم disabled می‌ماند');
      ok(!(num_(du2.city_id) > 0), 'disabled شهرستان نمی‌گیرد');
      // کاربر rejected: با این‌که در Whitelist هست، خودکار approved نمی‌شود
      var ru = ensureUser_(tgRej, 'تست ردشده');
      updateUser_(ru.user_id, tgRej, { status: 'rejected' });
      handleContact_(fakeContactMsg(tgRej, '+989120003334'));
      var ru2 = getUserByTg_(tgRej);
      ok(ru2.status !== 'approved', 'rejected تایید خودکار نمی‌شود');
      eq(getSession_(tgRej, tgRej).state, 'reg:city', 'rejected به مسیر دستی برمی‌گردد');
      // کاربر نیمه‌کاره (pending + شماره + بدون شهرستان): /start ادامهٔ ثبت‌نام را می‌آورد
      var hu = ensureUser_(tgHalf, 'تست نیمه‌کاره');
      updateUser_(hu.user_id, tgHalf, { phone: '09120004444', status: 'pending', city_id: '' });
      handleStart_({ chat: { id: tgHalf, type: 'private' }, from: { id: tgHalf, first_name: 'تست' }, text: '/start' });
      eq(getSession_(tgHalf, tgHalf).state, 'reg:city', 'نیمه‌کاره از قدم انتخاب شهرستان ادامه می‌یابد');
    } finally {
      if (tempAdminRow) sheet_('Admins').deleteRow(tempAdminRow);
      deleteTestUser(tgDis); deleteTestUser(tgRej); deleteTestUser(tgHalf);
      wlRows.sort(function (a, b) { return b - a; }).forEach(function (r) { sheet_('Whitelist').deleteRow(r); });
      bustCache_();
    }
  });

  check('۱۲) پنل: خواندن تاریخ و ساعتِ تایپ‌شده توسط ادمین', function () {
    var j = parseJalaliInput_('۱۴۰۵/۷/۲۳');
    ok(j && j.jy === 1405 && j.jm === 7 && j.jd === 23, 'تاریخ با ارقام فارسی و بدون صفر');
    ok(parseJalaliInput_('1405-07-23') !== null, 'خط تیره به‌جای اسلش');
    eq(parseJalaliInput_('1405/12/30'), null, '۳۰ اسفند ۱۴۰۵ (غیرکبیسه) نامعتبر');
    ok(parseJalaliInput_('1403/12/30') !== null, '۳۰ اسفند ۱۴۰۳ (کبیسه) معتبر');
    eq(parseJalaliInput_('1405/13/01'), null, 'ماه ۱۳ نامعتبر');
    eq(parseJalaliInput_('امروز'), null, 'متن نامعتبر');
    eq(parseTimeInput_('21:00'), '21:00', 'ساعت عادی');
    eq(parseTimeInput_('۹:۰۵'), '09:05', 'ارقام فارسی + صفر اول');
    eq(parseTimeInput_('24:00'), null, 'ساعت ۲۴ نامعتبر');
    eq(parseTimeInput_('21:60'), null, 'دقیقه ۶۰ نامعتبر');
  });

  check('۱۳) پنل: آمار ماهانه فقط تاییدشده‌های دوره همان ماه (۲۱ تا ۲۰) را می‌شمارد', function () {
    var cities = [{ city_id: 1, name: 'الف' }, { city_id: 2, name: 'ب' }, { city_id: 3, name: 'ج' }];
    var reps = [
      { status: 'approved', city_id: 2, event_year: 1405, event_month: 8, event_day: 5, participants: 30 },
      { status: 'approved', city_id: 2, event_year: 1405, event_month: 7, event_day: 25, participants: 10 },
      { status: 'approved', city_id: 1, event_year: 1405, event_month: 8, event_day: 20, participants: 5 },
      { status: 'deleted', city_id: 1, event_year: 1405, event_month: 8, event_day: 10, participants: 99 },
      { status: 'pending', city_id: 3, event_year: 1405, event_month: 8, event_day: 10, participants: 99 },
      { status: 'approved', city_id: 3, event_year: 1405, event_month: 7, event_day: 20, participants: 99 },
      { status: 'approved', city_id: 3, event_year: 1405, event_month: 8, event_day: 21, participants: 99 }
    ];
    var st = monthStats_(reps, cities, 1405, 8);
    eq(st.total, 3, 'جمع گزارش‌های دوره آبان (۲۵ مهر حساب است، ۲۰ مهر و ۲۱ آبان نه)');
    eq(st.people, 45, 'جمع نفرات');
    eq(st.rows[0].name, 'ب', 'رتبه اول');
    eq(st.rows[0].count, 2, 'تعداد رتبه اول');
    eq(st.zero.join(','), 'ج', 'شهرستان بدون گزارش');
    var pv = prevJalaliMonth_(1405, 1);
    eq(pv.jy + '/' + pv.jm, '1404/12', 'ماه قبل از فروردین');
  });

  check('۱۴) پنل: همه callback_dataها حداکثر ۶۴ بایت', function () {
    function bytes(x) { return unescape(encodeURIComponent(x)).length; }
    var datas = [];
    function collect(kb) { kb.inline_keyboard.forEach(function (row) { row.forEach(function (b) { datas.push(b.callback_data); }); }); }
    var bigRid = 999999999;
    collect(reportEditKb_(bigRid));
    ['class', 'audience', 'duration'].forEach(function (f) { collect(fieldOptionsKb_(bigRid, f)); });
    collect(reportCardKb_({ report_id: bigRid, status: 'approved' }));
    collect(adminCitiesKb_('A:us:' + bigRid + ':', 'A:uy:' + bigRid + ':', 3, [], null));
    collect(adminCitiesKb_('A:tc:', 'A:t:', 0, [], function () { return ':99'; }, {}));
    datas.push('A:aX:9999999999999', 'A:s:1405:12', 'A:tc:32:99', 'A:ti:' + bigRid);
    ok(datas.length > 20, 'نمونه کافی');
    datas.forEach(function (d) {
      ok(d.indexOf('A:') === 0, 'پیشوند A: ← ' + d);
      ok(bytes(d) <= 64, 'طول ' + d);
    });
  });

  check('۱۵) پنل: ویرایش کپشن منتشرشده تاریخ درج را نگه می‌دارد و ورودی بد را رد می‌کند', function () {
    var rep = {
      report_id: 1, status: 'approved', city_id: 29, class_type: 'حضوری', operation_name: '',
      subject: 'آموزش سواد رسانه', instructor_name: 'محمد احمدی', event_year: 1405, event_month: 7,
      event_day: 23, event_time: '21:00', duration_min: 90, participants: 35, audience: 'عموم',
      place: 'مسجد جامع', publish_jalali: '1405/07/25'
    };
    var p1 = reportPatchFor_(rep, 'date', '1405/06/10', 0);
    ok(p1.patch && p1.patch.event_month === 6 && p1.patch.event_jalali === '1405/06/10', 'پچ تاریخ');
    var merged = {};
    Object.keys(rep).forEach(function (k) { merged[k] = rep[k]; });
    Object.keys(p1.patch).forEach(function (k) { merged[k] = p1.patch[k]; });
    var cap = captionFromReport_(merged, rep.publish_jalali);
    ok(cap.indexOf('تاریخ درج در کانال :1405/07/25') === 0, 'تاریخ درج اصلی حفظ می‌شود');
    ok(cap.indexOf('🗓  ماه : #شهریور1405') !== -1, 'هشتگ ماه با تاریخ جدید عوض می‌شود');
    ok(cap.indexOf('تاریخ برگزاری:1405/06/10') !== -1, 'تاریخ برگزاری جدید');
    eq(reportPatchFor_(rep, 'time', '۲۰:۳۰', 0).patch.event_time, '20:30', 'پچ ساعت');
    eq(reportPatchFor_(rep, 'participants', '۱۲۰', 0).patch.participants, 120, 'پچ تعداد');
    eq(reportPatchFor_(rep, 'operation', '-', 0).patch.operation_name, '', 'خالی‌کردن نام عملیات');
    eq(reportPatchFor_(rep, 'duration', 1, 0).patch.duration_min, CFG.DURATIONS_MIN[1], 'پچ مدت دکمه‌ای');
    ok(reportPatchFor_(rep, 'participants', '0', 0).error, 'تعداد صفر رد می‌شود');
    ok(reportPatchFor_(rep, 'date', '1499/01/01', 0).error, 'تاریخ آینده رد می‌شود');
    ok(reportPatchFor_(rep, 'subject', '   ', 0).error, 'موضوع خالی رد می‌شود');
    ok(reportPatchFor_({ status: 'pending' }, 'subject', 'x', 0).error, 'گزارش غیرتاییدشده ویرایش نمی‌شود');
  });

  check('۱۶) پنل: افزودن و برداشتن ادمین (بدون پیام تلگرام) + محافظت از سوپرادمین', function () {
    var t = 999000777;
    try {
      ok(!isAdminTg_(t), 'قبل از افزودن ادمین نیست');
      var r = addAdmin_(t, 'ادمین تست', false);
      ok(r.ok, 'افزودن موفق');
      ok(isAdminTg_(t), 'بعد از افزودن ادمین است');
      ok(!isSuperTg_(t), 'نقش admin است نه superadmin');
      ok(!addAdmin_(t, 'ادمین تست', false).ok, 'افزودن تکراری رد می‌شود');
      ok(!removeAdmin_(t, t).ok, 'کسی دسترسی خودش را برنمی‌دارد');
      ok(removeAdmin_(t, 1).ok, 'برداشتن دسترسی موفق');
      ok(!isAdminTg_(t), 'بعد از برداشتن ادمین نیست');
      ok(addAdmin_(t, 'ادمین تست', false).ok, 'فعال‌سازی دوباره همان ردیف');
      eq(readTable_('Admins').filter(function (a) { return num_(a.telegram_id) === t; }).length, 1, 'ردیف تکراری ساخته نمی‌شود');
      appendRow_('Admins', { telegram_id: 999000778, full_name: 'سوپر تست', role: 'superadmin', is_active: 1, added_at: nowStr_() });
      bustCache_();
      ok(!removeAdmin_(999000778, t).ok, 'سوپرادمین از داخل بات برداشته نمی‌شود');
    } finally {
      var rows = readTable_('Admins').filter(function (a) { return num_(a.telegram_id) === t || num_(a.telegram_id) === 999000778; })
        .map(function (a) { return a._row; }).sort(function (a, b) { return b - a; });
      rows.forEach(function (rr) { sheet_('Admins').deleteRow(rr); });
      bustCache_();
    }
  });

  check('۱۷) فایل اکسل: پنج برگه مثل فایل نمونه، هر گزارش در برگه نوع خودش، فقط دوره ۲۱ تا ۲۰', function () {
    var cities = [{ city_id: 23, name: 'کاشان' }, { city_id: 29, name: 'نجف آباد' }];
    var base = { status: 'approved', instructor_name: 'ن', duration_min: 90, participants: 35, place: 'مسجد', channel_message_ids: '[]' };
    function R(o) { var r = {}; Object.keys(base).forEach(function (k) { r[k] = base[k]; }); Object.keys(o).forEach(function (k) { r[k] = o[k]; }); return r; }
    var reps = [
      R({ report_id: 7, city_id: 29, class_type: 'حضوری', subject: 'موضوع <مهم> & "ویژه"', event_jalali: '1405/08/03', event_year: 1405, event_month: 8, event_day: 3,
        duration_min: '60', participants: '20', place: 'خط اول\nخط دوم', channel_message_ids: '[51,52]' }),
      R({ report_id: 5, city_id: 23, class_type: 'حضوری', subject: '=1+1', event_jalali: '1405/07/21', event_year: 1405, event_month: 7, event_day: 21, channel_message_ids: '[40]' }),
      R({ report_id: 9, city_id: 23, class_type: 'تولیدات', subject: 'کلیپ', instructor_name: 'تولیدکننده', event_jalali: '1405/08/10', event_year: 1405, event_month: 8, event_day: 10, channel_message_ids: '[60]' }),
      R({ report_id: 10, city_id: 23, class_type: 'مجازی', subject: 'لایو', place: 'ایتا', event_jalali: '1405/08/20', event_year: 1405, event_month: 8, event_day: 20 }),
      R({ report_id: 6, status: 'deleted', city_id: 23, class_type: 'حضوری', event_jalali: '1405/08/05', event_year: 1405, event_month: 8, event_day: 5 }),
      R({ report_id: 8, city_id: 23, class_type: 'حضوری', event_jalali: '1405/07/20', event_year: 1405, event_month: 7, event_day: 20 }),
      R({ report_id: 11, city_id: 23, class_type: 'حضوری', event_jalali: '1405/08/21', event_year: 1405, event_month: 8, event_day: 21 })
    ];
    var pick = excelPick_(reps, {}, 1405, 8, 0, 'a');
    eq(pick.map(function (r) { return r.report_id; }).sort().join(','), '10,5,7,9', 'فقط تاییدشده‌های ۲۱ مهر تا ۲۰ آبان');
    var sheets = excelSheets_(pick, cities, -1001234567890, '');
    eq(sheets.map(function (s) { return s.name; }).join('|'), 'سوادرسانه حضوری|سوادرسانه مجازی (لایو)|توانمندسازی گردان|سواد رسانه خلاقانه|تولیدات', 'نام و ترتیب برگه‌ها');
    eq(sheets.map(function (s) { return s.rows.length; }).join(','), '2,1,0,0,1', 'هر گزارش در برگه نوع خودش');
    eq(sheets[0].cols.map(function (c) { return c[0]; }).join('|'), 'ردیف|تاریخ|نام ناحیه|موضوع|نام سخنران / مدرس|مدت (دقیقه)|تعداد نفرات|مکان برگزاری|لینک مستندات', 'ستون‌های برگه حضوری');
    eq(sheets[0].rows[0].join('|'), '1|1405/07/21|کاشان|=1+1|ن|90|35|مسجد|https://t.me/c/1234567890/40', 'ردیف اول به ترتیب تاریخ');
    eq(typeof sheets[0].rows[1][5] + typeof sheets[0].rows[1][6], 'numbernumber', 'مدت و تعداد «عدد» هستند (قابل جمع در اکسل)');
    eq(sheets[0].rows[1][7], 'خط اول - خط دوم', 'مکان چندخطی در یک سلول');
    eq(sheets[1].rows[0].join('|'), '1|1405/08/20|کاشان|لایو|ن|90|35|ایتا|', 'مجازی: بستر = متن مکان، بدون پست = بدون لینک');
    eq(sheets[4].rows[0].join('|'), '1|1405/08/10|کاشان|کلیپ|تولیدکننده|||https://t.me/c/1234567890/60', 'تولیدات: ستون‌های پرسیده‌نشده خالی');
    eq(channelPostLink_(-1001234567890, '@nasra_isf', 51), 'https://t.me/nasra_isf/51', 'لینک کانال عمومی');
    var parts = xlsxBook_(sheets), byName = {};
    parts.forEach(function (p) { byName[p.name] = p.content; });
    ['[Content_Types].xml', '_rels/.rels', 'xl/workbook.xml', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml',
      'xl/worksheets/sheet1.xml', 'xl/worksheets/sheet5.xml', 'xl/worksheets/_rels/sheet5.xml.rels'].forEach(function (n) { ok(byName[n], 'بخش ' + n); });
    eq((byName['xl/workbook.xml'].match(/<sheet /g) || []).length, 5, 'پنج برگه در فایل');
    var sh = byName['xl/worksheets/sheet1.xml'];
    ok(sh.indexOf('rightToLeft="1"') !== -1, 'برگه راست‌به‌چپ');
    ok(sh.indexOf('موضوع &lt;مهم&gt; &amp; &quot;ویژه&quot;') !== -1, 'نویسه‌های ویژه XML فرار داده می‌شوند');
    ok(parts.every(function (p) { return p.content.indexOf('<f>') === -1; }), 'هیچ فرمولی در فایل نیست (=1+1 متن می‌ماند)');
    ok(sh.indexOf('<c r="F2" s="2"><v>90</v></c>') !== -1, 'عدد در سلول عددی');
    eq((sh.match(/<hyperlink /g) || []).length, 2, 'دو لینک قابل کلیک در برگه حضوری');
    ok(byName['xl/worksheets/_rels/sheet5.xml.rels'].indexOf('TargetMode="External"') !== -1, 'لینک بیرونی در برگه تولیدات');
    ok(byName['xl/styles.xml'].indexOf('B Titr') !== -1, 'قلم فایل نمونه');
  });

  check('۱۸) یادآوری فقط برای شهرستان‌های بدون گزارش در دوره (۲۱ تا ۲۰) و فقط کارمندان فعال', function () {
    var cities = [{ city_id: 1, name: 'الف' }, { city_id: 2, name: 'ب' }, { city_id: 3, name: 'ج' }, { city_id: 4, name: 'د' }];
    var users = [
      { telegram_id: 11, city_id: 1, status: 'approved' }, { telegram_id: 12, city_id: 1, status: 'disabled' },
      { telegram_id: 21, city_id: 2, status: 'approved' }, { telegram_id: 31, city_id: 3, status: 'approved' },
      { telegram_id: 32, city_id: 3, status: 'pending' }
    ];
    var reps = [
      { status: 'approved', city_id: 2, event_year: 1405, event_month: 8, event_day: 5 },
      { status: 'pending', city_id: 4, event_year: 1405, event_month: 7, event_day: 28 },
      { status: 'rejected', city_id: 1, event_year: 1405, event_month: 8, event_day: 5 },
      { status: 'approved', city_id: 3, event_year: 1405, event_month: 7, event_day: 15 }
    ];
    var t = reminderTargets_(reps, users, cities, 1405, 8);
    eq(t.cities.map(function (c) { return c.name; }).join(','), 'الف,ج', 'شهرستان‌های بدون گزارش دوره آبان (۲۸ مهر جزو آبان است؛ ردشده و ۱۵ مهر حساب نیست)');
    eq(t.cities[0].recipients.join(','), '11', 'غیرفعال یادآوری نمی‌گیرد');
    eq(t.cities[1].recipients.join(','), '31', 'در انتظار تایید ثبت‌نام یادآوری نمی‌گیرد');
    eq(t.totalRecipients, 2, 'جمع گیرندگان');
  });

  check('۱۹) فاز ۳: روزهای یادآوری و گزارش‌های معطل', function () {
    eq(parseDaysList_('۲۰، 27, x, 40, 20').join(','), '20,27', 'خواندن روزها با ارقام فارسی و حذف نامعتبر/تکراری');
    eq(parseDaysList_('').length, 0, 'خالی');
    var reps = [
      { report_id: 3, status: 'pending', updated_at: '2026-10-20 09:00:00' },
      { report_id: 1, status: 'pending', updated_at: '2026-10-19 08:00:00' },
      { report_id: 2, status: 'pending', updated_at: '2026-10-25 10:00:00' },
      { report_id: 4, status: 'approved', updated_at: '2026-10-01 10:00:00' }
    ];
    eq(overduePending_(reps, '2026-10-24 10:00:00').map(function (r) { return r.report_id; }).join(','), '1,3', 'فقط در انتظارهای قدیمی، به ترتیب شماره');
  });

  check('۲۰) فاز ۳: گیرندگان پیام همگانی + طول callback دکمه‌های جدید + جاشدن صف', function () {
    var users = [
      { telegram_id: 11, city_id: 1, status: 'approved' }, { telegram_id: 12, city_id: 2, status: 'approved' },
      { telegram_id: 13, city_id: 2, status: 'disabled' }, { telegram_id: 14, city_id: 0, status: 'approved' },
      { telegram_id: 11, city_id: 1, status: 'approved' }
    ];
    var admins = [{ telegram_id: 90, is_active: 1 }, { telegram_id: 91, is_active: '0' }];
    eq(broadcastRecipients_(users, admins, 'all').join(','), '11,12', 'همه فعال‌ها، بدون تکرار و بدون بی‌شهرستان');
    eq(broadcastRecipients_(users, admins, 'city', 2).join(','), '12', 'یک شهرستان');
    eq(broadcastRecipients_(users, admins, 'admins').join(','), '90', 'فقط ادمین‌های فعال');
    ['A:b', 'A:ba', 'A:bm', 'A:bc:3', 'A:bC:32', 'A:bs', 'A:k', 'A:kb', 'A:kr', 'A:kt'].forEach(function (d) {
      ok(unescape(encodeURIComponent(d)).length <= 64, 'طول ' + d);
    });
    var ids = [], i, text = '';
    for (i = 0; i < 200; i++) ids.push(7000000000 + i);
    for (i = 0; i < AUTO.BC_MAX_CHARS; i++) text += 'س';
    var job = JSON.stringify({ by: 7000000000, text: text, ids: ids, pos: 199, ok: 199, fail: 0, label: 'همکاران فریدون شهر', at: '2026-10-26 10:00:00' });
    ok(unescape(encodeURIComponent(job)).length < 9 * 1024, 'صف پیام همگانی (۲۰۰ نفر، طولانی‌ترین متن) در سقف ۹KB هر Script Property جا می‌شود');
  });

  check('۲۱) دوره گزارش هر ماه: ۲۱ ماه قبل تا ۲۰ همان ماه (همه ۱۲ ماه، عبور از سال، اسفند کبیسه)', function () {
    function P(y, m, d) { var p = reportPeriodOf_(y, m, d); return p.jy + '/' + p.jm; }
    eq(P(1405, 7, 20), '1405/7', '۲۰ مهر ← گزارش مهر');
    eq(P(1405, 7, 21), '1405/8', '۲۱ مهر ← گزارش آبان');
    eq(P(1405, 8, 20), '1405/8', '۲۰ آبان ← گزارش آبان');
    eq(P(1405, 12, 21), '1406/1', '۲۱ اسفند ← گزارش فروردین سال بعد');
    eq(P(1403, 12, 30), '1404/1', '۳۰ اسفند کبیسه ← گزارش فروردین');
    eq(P(1406, 1, 20), '1406/1', '۲۰ فروردین ← گزارش فروردین');
    var r = periodRange_(1406, 1);
    eq(r.from + '→' + r.to, '1405/12/21→1406/01/20', 'گزارش فروردین: ۲۱ اسفند تا ۲۰ فروردین');
    for (var m = 1; m <= 12; m++) {
      var g = periodRange_(1405, m), pv = prevJalaliMonth_(1405, m);
      eq(g.from, formatJalali_(pv.jy, pv.jm, 21), 'شروع گزارش ' + JALALI_MONTHS[m - 1]);
      eq(g.to, formatJalali_(1405, m, 20), 'پایان گزارش ' + JALALI_MONTHS[m - 1]);
    }
    eq(periodRangeText_(1405, 8), 'از 1405/07/21 تا 1405/08/20', 'متن بازه آبان');
    var row = reportPeriodOfRow_({ event_jalali: '1405/08/25' });
    eq(row.jy + '/' + row.jm, '1405/9', 'گزارش بدون ستون‌های عددی: از تاریخ متنی خوانده می‌شود');
    var t = todayJalali_(), cp = currentPeriod_(), want = reportPeriodOf_(t.jy, t.jm, t.jd);
    eq(cp.jy + '/' + cp.jm, want.jy + '/' + want.jm, 'دوره امروز');
  });

  check('۲۲) نقش‌ها: خواهر/برادر از روی نقش فرستنده؛ فضای مجازی و بدون نقش فقط در تجمیعی', function () {
    eq(POSITIONS.length, 5, 'پنج نقش');
    eq(positionOf_(' مسئول  نسرا   خواهر '), 'مسئول نسرا خواهر', 'تحمل فاصله اضافه');
    eq(positionOf_('مسئول‌فضاي مجازي'), 'مسئول فضای مجازی', 'تحمل نیم‌فاصله و ی عربی');
    eq(positionOf_('مدیر'), '', 'نقش ناشناخته');
    eq(POSITIONS.map(positionSide_).join(','), 'v,s,s,b,b', 'سمت هر نقش');
    var users = [
      { telegram_id: 1, position: 'فرمانده گردان خواهر' }, { telegram_id: 2, position: 'مسئول نسرا برادر' },
      { telegram_id: 3, position: 'مسئول فضای مجازی' }, { telegram_id: 4, position: '' }, { telegram_id: 5, position: 'مسئول نسرا خواهر' }
    ];
    function R(id, tg, city, pos) { return { report_id: id, status: 'approved', submitter_telegram_id: tg, city_id: city, submitter_position: pos || '', event_year: 1405, event_month: 8, event_day: 1 }; }
    var reps = [R(1, 1, 5), R(2, 2, 5), R(3, 3, 5), R(4, 4, 5), R(5, 5, 6), R(6, 2, 5, 'مسئول نسرا خواهر'),
      { report_id: 7, status: 'pending', submitter_telegram_id: 1, city_id: 5, event_year: 1405, event_month: 8, event_day: 1 }];
    var pos = positionsByTg_(users);
    function ids(list) { return list.map(function (r) { return r.report_id; }).join(','); }
    eq(ids(excelPick_(reps, pos, 1405, 8, 5, 's')), '1,6', 'خواهران شهرستان ۵ (گزارش ۶ با نقشِ لحظه ارسال)');
    eq(ids(excelPick_(reps, pos, 1405, 8, 5, 'b')), '2', 'برادران شهرستان ۵');
    eq(ids(excelPick_(reps, pos, 1405, 8, 5, 'a')), '1,2,3,4,6', 'تجمیعی شهرستان ۵ (شامل فضای مجازی و بدون نقش)');
    eq(ids(excelPick_(reps, pos, 1405, 8, 0, 'a')), '1,2,3,4,5,6', 'همه شهرستان‌ها');
    var sc = sideCounts_(reps, pos, 1405, 8, 5);
    eq([sc.s, sc.b, sc.v, sc.none, sc.all].join(','), '2,1,1,1,5', 'شمارش خواهر/برادر/فضای مجازی/بدون نقش');
  });

  check('۲۳) دکمه‌های فاز ۴ (ماه/شهرستان/خواهر-برادر/نقش) حداکثر ۶۴ بایت + هر نوع کلاس یک برگه', function () {
    var datas = ['A:xm:5', 'A:xs:1405:12', 'A:xa:1405:12', 'A:xc:1405:12:3', 'A:xg:1405:12:32', 'A:xf:1405:12:32:s', 'A:xf:1405:12:32:a',
      'A:uq:99999', 'A:uQ:99999:4', 'A:uQ:99999:x', 'po:4'];
    function collect(kb) { kb.inline_keyboard.forEach(function (row) { row.forEach(function (b) { datas.push(b.callback_data); }); }); }
    collect(excelMonthPickerKb_(0)); collect(excelMonthPickerKb_(5));
    collect(adminCitiesKb_('A:xg:1405:12:', 'A:xc:1405:12:', 0, [], null, {}));
    datas.forEach(function (d) { ok(unescape(encodeURIComponent(d)).length <= 64, 'طول ' + d); });
    eq(XL_SHEETS.map(function (s) { return s.type; }).join(','), CFG.CLASS_TYPES.join(','), 'هر هشتگ نوع کلاس برگه خودش را دارد');
    XL_SHEETS.forEach(function (s) { ok(s.name.length <= 31, 'طول نام برگه ' + s.name); });
    ok(SHEETS.Users.indexOf('position') !== -1 && SHEETS.Reports.indexOf('submitter_position') !== -1, 'ستون‌های تازه در اسکیمای برگه‌ها');
  });

  check('۲۴) کانال‌های اضافه: خواندن آیدی/لینک کانال، کنار گذاشتن کانال اصلی، طول دکمه‌ها', function () {
    eq(parseChannelRef_('@isfahannasraa'), '@isfahannasraa', 'با @');
    eq(parseChannelRef_('isfahannasraa'), '@isfahannasraa', 'بدون @');
    eq(parseChannelRef_('https://t.me/isfahannasraa'), '@isfahannasraa', 'لینک کامل');
    eq(parseChannelRef_('t.me/isfahannasraa/125'), '@isfahannasraa', 'لینک یک پست');
    eq(parseChannelRef_('-1001234567890'), -1001234567890, 'آیدی عددی');
    eq(parseChannelRef_('t.me/+AbCdEf'), null, 'لینک دعوت خصوصی پذیرفته نمی‌شود (باید فوروارد کرد)');
    eq(parseChannelRef_('سلام'), null, 'متن نامعتبر');
    eq(JSON.stringify(extraChannelMsgs_({ extra_channel_msgs: '{"-1009":[5,6]}' })), '{"-1009":[5,6]}', 'خواندن پیام‌های کانال اضافه');
    eq(JSON.stringify(extraChannelMsgs_({ extra_channel_msgs: '' })), '{}', 'گزارش قدیمی بدون کانال اضافه');
    ['A:c', 'A:ca', 'A:cr:-1001234567890123', 'A:cR:-1001234567890123'].forEach(function (d) { ok(unescape(encodeURIComponent(d)).length <= 64, 'طول ' + d); });
    ok(SHEETS.Reports.indexOf('extra_channel_msgs') !== -1, 'ستون پیام‌های کانال اضافه');
  });

  check('۲۵) نمونه گزارش‌ها: ذخیره پست/آلبوم با کپشن و قالب‌بندی، بازسازی آلبوم، منوی تازه', function () {
    var ce = [{ type: 'bold', offset: 0, length: 4 }, { type: 'custom_emoji', offset: 5, length: 2, custom_emoji_id: '1' }];
    var p1 = sampleItemFromMsg_({ message_id: 11, media_group_id: 'G', caption: 'نمونه ✨', caption_entities: ce, photo: [{ file_id: 'small' }, { file_id: 'big' }] });
    eq(p1.k + '|' + p1.f + '|' + p1.g, 'photo|big|G', 'عکس: بزرگ‌ترین اندازه + شناسه آلبوم');
    eq(p1.e.length, 1, 'ایموجی اختصاصی (پریمیوم) حذف و پررنگ حفظ می‌شود');
    var p2 = sampleItemFromMsg_({ message_id: 12, media_group_id: 'G', photo: [{ file_id: 'p2' }] });
    eq(sampleItemFromMsg_({ message_id: 13, animation: { file_id: 'gif' }, document: { file_id: 'gif' } }).k, 'animation', 'گیف به‌صورت گیف (نه فایل)');
    var t = sampleItemFromMsg_({ message_id: 10, text: 'متن آموزشی', entities: [{ type: 'text_link', offset: 0, length: 3, url: 'https://t.me/x' }] });
    var v = sampleItemFromMsg_({ message_id: 14, video: { file_id: 'vid' }, caption: 'ویدیو' });
    eq(sampleItemFromMsg_({ message_id: 15, sticker: { file_id: 's' } }), null, 'استیکر پشتیبانی نمی‌شود');
    var plan = sampleSendPlan_([t, p1, p2, v]);
    eq(plan.map(function (x) { return x.method; }).join(','), 'sendMessage,sendMediaGroup,sendVideo', 'متن، آلبوم دو عکسی، ویدیو');
    eq(plan[0].p.entities[0].url, 'https://t.me/x', 'لینک داخل متن حفظ می‌شود');
    eq(plan[1].p.media.length + '|' + plan[1].p.media[0].caption + '|' + plan[1].p.media[0].caption_entities.length + '|' + (plan[1].p.media[1].caption || ''), '2|نمونه ✨|1|', 'کپشن روی عکس اول آلبوم');
    eq(sampleSendPlan_([p2]).map(function (x) { return x.method; }).join(','), 'sendPhoto', 'آلبوم تک‌عکسی ← sendPhoto');
    var kb = mainMenuKb_().keyboard, labels = [].concat.apply([], kb).map(function (b) { return b.text; });
    ok(labels.indexOf('📝 ارسال گزارش') !== -1 && labels.indexOf('📚 نمونه گزارش‌ها') !== -1, 'منوی اصلی: «ارسال گزارش» و «نمونه گزارش‌ها»');
    ok(labels.indexOf(MENU.NEW_REPORT_OLD) === -1, 'برچسب قدیمی در منو نیست (ولی هنوز پذیرفته می‌شود)');
    var datas = ['A:e', 'A:es'];
    CFG.CLASS_TYPES.forEach(function (x, i) { ['A:et:', 'A:ep:', 'A:er:', 'A:ed:', 'A:eD:', 'sm:'].forEach(function (pre) { datas.push(pre + i); }); });
    samplesMenuKb_().inline_keyboard.forEach(function (row) { row.forEach(function (b) { datas.push(b.callback_data); }); });
    datas.forEach(function (d) { ok(unescape(encodeURIComponent(d)).length <= 64, 'طول ' + d); });
    eq(CFG.CLASS_TYPES.join(','), 'حضوری,مجازی,توانمندسازی,خلاقانه,تولیدات', 'پنج نوع نمونه');
    ok(JSON.stringify([t, p1, p2, v, v, v, v, v, v, v]).length < 45000, '۱۰ مورد در یک خانه برگه جا می‌شود');
    ok(SHEETS.Samples && SHEETS.Samples.indexOf('items_json') !== -1, 'برگه Samples در اسکیما');
  });

  check('۲۶) نام مدرس فقط «نام و نام خانوادگی» + پیشنهادهای موضوع + متن قدم عکس', function () {
    ['حامد کمال', 'سید علی حسینی', 'فاطمه شیخ‌الاسلامی', 'حجت کمالی', 'امیر مهندسی‌فر', 'محمدرضا آقاجانی', 'زهرا  دکتری'].forEach(function (n) {
      eq(instructorNameIssue_(n), '', 'درست: ' + n);
    });
    ['دکتر کمال', 'آقای کمال', 'دکتر حامد کمال', 'اقای حامد کمال', 'حاج آقا کمالی', 'حجت الاسلام حامد کمال', 'حجت‌الاسلام کمال', 'سرهنگ حامد کمال',
      'خانم مریم کمالی', 'مهندس رضا احمدی', 'مسئول نسرا حامد کمال', 'کمال', 'حامد کمال ۲', 'حامد کمال (مدرس دانشگاه)', 'Dr Hamed Kamal', 'آیت‌الله حامد کمال'].forEach(function (n) {
      ok(instructorNameIssue_(n) !== '', 'غلط: ' + n);
    });
    ok(/دکتر/.test(instructorNameIssue_('دکتر حامد کمال')), 'علت شکست، خودِ لقب را نام می‌برد');
    ok(TXT.ASK_TEACHER_NEW.indexOf('حامد کمال') !== -1 && TXT.ASK_TEACHER_NEW.indexOf('دکتر حامد کمال') !== -1, 'راهنمای مدرس جدید مثال درست و غلط دارد');
    eq(SUBJECT_SUGGESTIONS.join('|'), 'سواد رسانه عمومی|توانمندسازی گردان سایبری|جنگ شناختی|آموزش اینستاگرام|آموزش توییتر|آموزش نرم افزارهای موبایلی', 'شش پیشنهاد موضوع');
    SUBJECT_SUGGESTIONS.forEach(function (x, i) { ok(unescape(encodeURIComponent('sj:' + i)).length <= 64, 'طول sj:' + i); });
    ok(TXT.FORM_START.indexOf('حتماً بنر نماد نسرا به همراه موضوع و تاریخ در تصاویر باشد.') !== -1, 'متن تازه قدم عکس');
  });

  if (failed.length) {
    Logger.log('نتیجه: ' + passed + '/' + total + ' passed — شکست‌ها:\n' + failed.join('\n'));
  } else Logger.log(passed + ' passed');
}
