// Tests.gs — تست‌های پذیرش فاز ۱. در ویرایشگر Apps Script تابع runTests را Run کنید؛
// در Execution log باید خروجی «11 passed» را ببینید (در صورت شکست، علت هر مورد نوشته می‌شود).
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

  if (failed.length) {
    Logger.log('نتیجه: ' + passed + '/' + total + ' passed — شکست‌ها:\n' + failed.join('\n'));
  } else Logger.log(passed + ' passed');
}
