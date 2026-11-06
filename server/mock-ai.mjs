/*
 * Offline AI stub for local testing only.
 *
 * Enabled with MOCK_AI=1 (never on by default). It answers with deterministic
 * JSON so the whole pipeline — ideation, script, fact-check, production,
 * editorial, tech check, gates — can be exercised on a laptop without an API
 * key and without any network access.
 *
 * The shapes match the output schemas seeded in schema.sql. When MOCK_AI is off
 * this module is not imported at all.
 */
// The same deterministic answers used by dev-preview.mjs.
function mockAgentOutput(systemPrompt, userText) {
  const id = systemPrompt.match(/##\s+([a-z_]+)/)?.[1] || '';
  let input = {};
  try { input = JSON.parse(userText); } catch {}
  const format = input.content?.selected_format || 'post';
  const common = { risk_flags: [], needs_info: [] };
  if (id === 'trend_scout') return { topics: [{ title: 'چک‌لیست سه‌مرحله‌ای برای بررسی یک پیام مشکوک', why_now: 'ورودی آزمایشی پیش‌نمایش؛ پیش از انتشار باید منبع واقعی اضافه شود.', pillar: 'شایعه‌شکنی و راستی‌آزمایی', source_refs: [], sensitivity: 'کم', urgency: 'این‌هفته' }], ...common };
  if (id === 'ideation') return { ideas: [
    { format: 'post', hook: 'قبل از بازنشر، این سه نشانه را ببین', angle: 'یک چک‌لیست کوتاه و قابل ذخیره', key_message: 'مکث و بررسی منبع، تصمیم رسانه‌ای بهتری می‌سازد.', cta: 'چک‌لیست را برای دوستت بفرست', effort: 'کم', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: چک‌لیست‌های کوتاه ذخیره بیشتری می‌گیرند.', confidence: 'کم' } },
    { format: 'carousel', hook: 'سه سؤال پیش از باور کردن یک خبر', angle: 'اسلایدهای گام‌به‌گام', key_message: 'منبع، تاریخ و تصویر را جدا بررسی کن.', cta: 'ذخیره کن و مرور کن', effort: 'متوسط', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: ساختار مرحله‌ای خوانایی را بیشتر می‌کند.', confidence: 'کم' } },
    { format: 'reel', hook: 'یک ثانیه مکث کن؛ لینک را دقیق بخوان', angle: 'بازنمایی کوتاه بدون ترساندن', key_message: 'عجله، نشانه رایج فیشینگ است.', cta: 'برای خانواده بفرست', effort: 'زیاد', why_it_may_work: { hypothesis: 'فرضیه برای آزمایش: مثال ملموس یادآوری را بیشتر می‌کند.', confidence: 'کم' } }
  ], ...common };
  if (id === 'scriptwriter') return {
    format,
    post_body: ['post', 'story'].includes(format) ? 'قبل از بازنشر یک پیام مشکوک، این سه نشانه را بررسی کن:\n۱. منبع مشخص و قابل اعتماد نیست.\n۲. تاریخ یا زمینه پیام روشن نیست.\n۳. از شما می‌خواهد عجله کنید یا رمز و اطلاعات شخصی بدهید.\n\nاگر یکی از این نشانه‌ها را دیدید، مکث کنید و پیام را از مسیر رسمی بررسی کنید.' : '',
    beats: format === 'reel' ? [{ t_start: 0, t_end: 3, voiceover: 'قبل از بازنشر یک ثانیه مکث کن.', on_screen_text: 'یک ثانیه مکث کن', visual: 'نمای نزدیک از صفحه پیام' }, { t_start: 3, t_end: 12, voiceover: 'منبع و تاریخ را بررسی کن.', on_screen_text: 'منبع و تاریخ', visual: 'نمایش چک‌لیست' }] : [],
    slides: format === 'carousel' ? [{ n: 1, headline: 'سه سؤال مهم', body: 'چه کسی منتشر کرده؟ چه زمانی؟ مدرک کجاست؟', visual_note: 'سه کارت با حاشیه امن' }] : [],
    claims_list: [], duration_estimate_sec: format === 'reel' ? 12 : 30, reading_level: 'ساده و عمومی', ...common
  };
  if (id === 'fact_checker') return { claims: [], overall: 'pass', notes: 'در ورودی آزمایشی ادعای قابل راستی‌آزمایی ثبت نشده است.', ...common };
  if (id === 'video_editor') return { shot_list: [{ n: 1, duration_sec: 3, visual: 'نمای صفحه پیام', on_screen_text: 'مکث کن', transition: 'cut' }], cut_plan: { pace: 'آرام و خوانا', beat_points: [0, 3] }, subtitle_srt: '1\n00:00:00,000 --> 00:00:03,000\nمکث کن', cover_frame_idea: 'قاب چک‌لیست با کنتراست بالا', export_spec: { ratio: '9:16', resolution: '1080x1920', fps: 30 }, tool_notes: 'مشخصات را با فایل نهایی تطبیق دهید.', ...common };
  if (id === 'poster_designer' || id === 'infographic_designer') {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 1350"><rect width="1080" height="1350" fill="#0F4C5C"/><text x="980" y="220" text-anchor="end" direction="rtl" fill="#F8FAFC" font-family="Tahoma,Arial" font-size="64">نسرا اصفهان</text><text x="980" y="420" text-anchor="end" direction="rtl" fill="#F8FAFC" font-size="52">مکث، بررسی، بعد بازنشر</text><text x="980" y="1180" text-anchor="end" direction="rtl" fill="#1B998B" font-size="34">سوادرسانه برای همه</text></svg>';
    if (id === 'poster_designer') return { brief: 'پوستر آموزشی ساده با سه رنگ برند', layout_description: 'تیتر بالا، پیام مرکزی، واترمارک پایین', text_blocks: [{ role: 'headline', text: 'مکث، بررسی، بعد بازنشر' }], svg, alt_text: 'پوستر آموزشی نسرا اصفهان درباره بررسی پیش از بازنشر', export_note: 'در مرورگر به JPEG سایز ۱۰۸۰×۱۳۵۰ تبدیل شود.', ...common };
    return { slides: [{ n: 1, title: 'سه گام بررسی', svg, data_points: [] }], alt_text: 'اینفوگرافیک سه گام بررسی محتوا', ...common };
  }
  if (id === 'sound_engineer') return { voiceover_script: 'مکث کن [مکث کوتاه] منبع را بررسی کن.', pacing: 'آرام، با مکث بعد از هر گام', sound_design_cues: [{ t: 0, cue: 'یک افکت کوتاه و ملایم' }], mix_notes: 'صدای گوینده واضح و موسیقی کم‌تر از گفتار', recording_tips: 'در فضای ساکت و با فاصله ثابت از میکروفون ضبط کنید.', ...common };
  if (id === 'music_composer') return { mood: 'آرام و امیدوارکننده', bpm_range: '۸۰ تا ۹۰', structure: [{ section: 'ورود', seconds: 4, instrumentation: 'پد ملایم' }], style_description: 'پس‌زمینه مینیمال بدون احساس اضطراب', prompts_for_external_generators: ['ambient minimal educational background, no vocals'], licensing_note: 'مجوز هر قطعه باید پیش از استفاده بررسی شود؛ این خروجی صدا تولید نمی‌کند.', ...common };
  if (id === 'editorial') return { caption: 'قبل از بازنشر، سه سؤال ساده بپرس\nچه کسی منتشر کرده؟ چه زمانی؟ مدرکش کجاست؟\n\nاین چک‌لیست را ذخیره کن و برای خانواده بفرست.', hashtags: ['#سوادرسانه', '#نسرا_اصفهان'], alt_text: 'چک‌لیست سه‌مرحله‌ای بررسی منبع و تاریخ و مدرک', cta: 'ذخیره و بازنشر آگاهانه', posting_slots: [], checklist_results: [{ item: 'قلاب مستقل', ok: true, note: 'بررسی پیش‌نمایش' }], trial_advice: { hypothesis: 'داده کافی نیست؛ ابتدا آمار جمع شود.', confidence: 'کم' }, experiment: { variable: 'فرمت', hypothesis: 'فرضیه برای آزمایش: کروسل چک‌لیستی ذخیره بیشتری بگیرد.', metric: 'saves' }, ...common };
  if (id === 'tech_advisor') return { checks: [], suggestions: [], unknown_specs: [], ...common };
  if (id === 'community_admin') {
    if (input.task?.mode === 'reply' || input.extra?.inbound_message) return { category: 'سؤال', sentiment: 'خنثی', escalate: false, reason: 'پیش‌نویس پاسخ آموزشی', reply_draft: 'سپاس از پیام شما. لطفاً منبع و زمان دریافت را بررسی کنید؛ اگر نشانه مشکوکی هست، جزئیات را بدون اطلاعات شخصی بفرستید.', do_not_reply: false, ...common };
    return { schedule: [], conflicts: [], ...common };
  }
  return common;
}

export default mockAgentOutput;
