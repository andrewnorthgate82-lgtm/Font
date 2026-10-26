// worker.js — Cloudflare Worker واسطه بات نسرا (نام Worker: nasra-relay)
// تلگرام → این Worker → Google Apps Script
// چرا لازم است؟ وبهوک مستقیم به آدرس /exec آپس‌اسکریپت با 302 جواب می‌گیرد، تلگرام آن را خطا می‌شمارد
// و pending_update_count بالا می‌رود. این‌جا تلگرام فوراً 200 می‌گیرد و ارسال به گوگل در پس‌زمینه انجام می‌شود.
// سه Secret را در Settings ← Variables and Secrets تعریف کنید (هرگز در کد ثابت ننویسید):
//   GAS_URL   = آدرس /exec وب‌اپ Apps Script
//   GAS_KEY   = همان GAS_KEY که setup() در لاگ ساخت
//   TG_SECRET = همان TG_SECRET که setup() در لاگ ساخت

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'POST') {
      return new Response('method not allowed', { status: 405 });
    }
    if (request.headers.get('X-Telegram-Bot-Api-Secret-Token') !== env.TG_SECRET) {
      return new Response('forbidden', { status: 403 });
    }
    const body = await request.text();
    ctx.waitUntil(forwardWithRetry(env, body));
    return new Response('ok', { status: 200 }); // تلگرام فوراً ۲۰۰ می‌گیرد؛ pending صفر می‌ماند
  },
};

async function forwardWithRetry(env, body) {
  const url = env.GAS_URL + '?key=' + encodeURIComponent(env.GAS_KEY);
  const delaysMs = [0, 2000, 4000, 6000]; // تلاش اول + حداکثر ۳ تلاش مجدد با فاصله‌های ۲ و ۴ و ۶ ثانیه
  for (let attempt = 0; attempt < delaysMs.length; attempt++) {
    if (delaysMs[attempt] > 0) await sleep(delaysMs[attempt]);
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        redirect: 'follow', // ریدایرکت 302 گوگل این‌جا دنبال می‌شود، نه توسط تلگرام
      });
      const text = (await res.text()).trim();
      // Apps Script وقتی قفل اجرا گرفته نمی‌شود متن 'retry' برمی‌گرداند و آپدیت را seen نکرده است:
      // در آن صورت (یا هر پاسخ غیرموفق) باید دوباره تلاش کرد؛ حذف تکراری خودِ آپدیت در GAS انجام می‌شود.
      if (res.ok && text !== 'retry') return;
      console.error('GAS retry needed:', res.status, text.slice(0, 80), 'attempt', attempt + 1);
    } catch (err) {
      console.error('GAS fetch failed:', String(err), 'attempt', attempt + 1);
    }
  }
  console.error('GAS forward gave up after retries');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
