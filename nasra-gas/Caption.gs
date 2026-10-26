// Caption.gs — ساخت کپشن استاندارد کانال، دقیقاً با همان قالب و ایموجی‌های خواسته‌شده.
// فقط بات کپشن می‌سازد و هنگام ارسال parse_mode نمی‌فرستد (متن خام).

function buildCaption_(f) {
  // f: publish_jalali, city_hashtag, class_type, operation_name, subject, instructor_name,
  //    event_year, event_month, event_day, event_time, duration_min, participants, audience, place
  var lines = [
    'تاریخ درج در کانال :' + f.publish_jalali,
    '⚜️ نام استان : #اصفهان',
    '✅  معاونت : #نسرا',
    '🗓  ماه : #' + monthTag_(num_(f.event_year), num_(f.event_month)),
    '⚜️ ناحیه برگزار کننده : ' + f.city_hashtag,
    'نوع کلاس/ برنامه:  #' + f.class_type,
    '💢 نام عملیات : ' + (f.operation_name || ''),
    '📄 موضوع : ' + f.subject,
    '👨‍🏫مدرس یا سخنران : ' + f.instructor_name,
    'تاریخ برگزاری:' + formatJalali_(num_(f.event_year), num_(f.event_month), num_(f.event_day)),
    '⏰ساعت برگزاری : ' + f.event_time,
    '⏳مدت زمان برگزاری :' + f.duration_min + ' دقیقه',
    '🚻 تعداد نفرات شرکت کننده :' + f.participants + ' نفر',
    '👥 نوع مخاطبین : #' + f.audience,
    '⭕️ بستر / مکان برگزاری : ',
    String(f.place || '')
  ];
  return lines.join('\n');
}

function captionFromData_(data, cityHashtag, publishJalali) {
  return buildCaption_({
    publish_jalali: publishJalali, city_hashtag: cityHashtag,
    class_type: data.class_type, operation_name: data.operation_name || '',
    subject: data.subject, instructor_name: data.instructor_name,
    event_year: data.event_year, event_month: data.event_month, event_day: data.event_day,
    event_time: data.event_time, duration_min: data.duration_min,
    participants: data.participants, audience: data.audience, place: data.place
  });
}

function captionFromReport_(rep, publishJalali) {
  var city = getCityById_(rep.city_id);
  return buildCaption_({
    publish_jalali: publishJalali, city_hashtag: city ? city.hashtag : '',
    class_type: rep.class_type, operation_name: rep.operation_name || '',
    subject: rep.subject, instructor_name: rep.instructor_name,
    event_year: rep.event_year, event_month: rep.event_month, event_day: rep.event_day,
    event_time: rep.event_time, duration_min: rep.duration_min,
    participants: rep.participants, audience: rep.audience, place: rep.place
  });
}
