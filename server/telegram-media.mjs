/*
 * Send a stored upload to Telegram as bytes instead of a URL.
 *
 * Telegram's sendPhoto/sendVideo accept either an https URL it downloads itself,
 * or a multipart upload from us. An owner on a laptop has uploaded files that
 * only exist on that laptop, so the URL form can never work for them: Telegram
 * would fetch http://localhost:8787/media/... from its own servers and get
 * nothing. Uploading the bytes is the only honest option.
 *
 * This deliberately bypasses JSON: worker.js builds its publish request as JSON
 * with a `photo`/`video` string, which is correct for a public URL and impossible
 * for a local file. The message body is rebuilt here with the real file, and the
 * caption is taken from the same editorial JSON the worker would have used.
 *
 * Nothing here decides *whether* to publish. The caller has already asked the
 * publish guard; this only chooses the transport.
 */
import fs from 'node:fs';
import path from 'node:path';

import { bytesFor, isUploadRef, uploadName } from './media-upload.mjs';

const PHOTO_MAX = 10 * 1024 * 1024;
const VIDEO_MAX = 50 * 1024 * 1024;

function telegramMethodPath(url) {
  const match = String(url || '').match(/\/bot([^/]+)\/([A-Za-z]+)$/);
  return match ? { token: match[1], method: match[2].toLowerCase() } : null;
}

function parseJsonBody(init) {
  const body = init?.body;
  if (typeof body !== 'string' || !body) return null;
  try { return JSON.parse(body); } catch { return null; }
}

/*
 * Which stored file does this send refer to?
 * - single send: body.photo / body.video / body.animation
 * - album: body.media[].media
 * The guard already classified the request as a publish, so a body we cannot read
 * is simply left to the normal JSON path.
 */
function uploadRefsIn(body) {
  if (!body) return [];
  const refs = [];
  if (isUploadRef(body.photo)) refs.push({ ref: body.photo, field: 'photo', kind: 'photo' });
  if (isUploadRef(body.video)) refs.push({ ref: body.video, field: 'video', kind: 'video' });
  if (isUploadRef(body.animation)) refs.push({ ref: body.animation, field: 'animation', kind: 'video' });
  if (Array.isArray(body.media)) {
    for (const item of body.media) {
      if (!isUploadRef(item?.media)) continue;
      refs.push({ ref: item.media, field: 'media', kind: item.type === 'video' ? 'video' : 'photo', item });
    }
  }
  return refs;
}

export function needsMultipartUpload(url, init = {}) {
  const parsed = telegramMethodPath(url);
  if (!parsed) return null;
  const body = parseJsonBody(init);
  if (!body) return null;
  const refs = uploadRefsIn(body);
  return refs.length ? { ...parsed, body, refs } : null;
}

/**
 * Build one multipart request. Returns null when a file is missing from disk or
 * too large, so the caller can report a precise error instead of a silent no-op.
 */
export function buildMultipartUpload(mediaStore, { url, init = {}, plan }) {
  const { body, refs, method } = plan;
  const form = new FormData();
  const problems = [];

  /* Every scalar field the worker already decided on, except the media itself. */
  for (const [key, value] of Object.entries(body)) {
    if (['photo', 'video', 'animation', 'media'].includes(key)) continue;
    if (value === undefined || value === null) continue;
    form.set(key, typeof value === 'object' ? JSON.stringify(value) : String(value));
  }

  if (method === 'sendmediagroup') {
    /* Telegram wants attach:// references in the media array plus the files. */
    const media = [];
    refs.forEach((entry, index) => {
      const bytes = bytesFor(mediaStore, entry.ref);
      if (!bytes) { problems.push(`فایل ${uploadName(entry.ref) || entry.ref} روی دیسک پیدا نشد`); return; }
      const field = `file${index}`;
      form.set(field, new Blob([bytes.buffer], { type: bytes.mime }), bytes.name);
      const item = { ...entry.item, media: `attach://${field}`, type: entry.kind };
      const { caption } = entry.item || {};
      if (caption) item.caption = caption;
      media.push(item);
    });
    form.set('media', JSON.stringify(media));
  } else {
    const entry = refs[0];
    const bytes = bytesFor(mediaStore, entry.ref);
    if (!bytes) return { error: `فایل ${uploadName(entry.ref) || entry.ref} روی دیسک پیدا نشد` };
    const limit = entry.kind === 'video' ? VIDEO_MAX : PHOTO_MAX;
    if (bytes.buffer.length > limit) {
      return { error: entry.kind === 'video'
        ? 'ویدیو بزرگ‌تر از ۵۰ مگابایت است؛ تلگرام آن را از ربات نمی‌پذیرد'
        : 'تصویر بزرگ‌تر از ۱۰ مگابایت است؛ آن را کوچک‌تر کنید' };
    }
    form.set(entry.field, new Blob([bytes.buffer], { type: bytes.mime }), bytes.name);
  }

  if (problems.length) return { error: problems.join(' · ') };

  /* JSON.stringify(init) must not survive: fetch sets the multipart boundary. */
  return { form, url: String(url), method: init?.method || 'POST', signal: init?.signal };
}

/* Perform the upload, converting a Telegram failure into something readable. */
export async function performMultipartUpload(realFetch, mediaStore, { url, init = {}, plan }) {
  const built = buildMultipartUpload(mediaStore, { url, init, plan });
  if (built?.error) {
    return new Response(JSON.stringify({ ok: false, error_code: 400, description: built.error }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'X-Nasra-Guard': 'upload' }
    });
  }
  const response = await realFetch(built.url, { method: built.method, body: built.form, signal: built.signal });
  if (response.ok) return response;

  /* Telegram answers with error_code/description; keep that text, add the reason. */
  const payload = await response.json().catch(() => null);
  if (!payload) return response;
  const description = String(payload.description || '');
  if (/wrong file identifier|failed to get HTTP URL content|PHOTO_INVALID/i.test(description)) {
    payload.description = `${description} — فایل آپلودی هم پذیرفته نشد. اندازه و فرمت (jpg/png، حداکثر ۱۰ مگابایت) را بررسی کنید.`;
  }
  return new Response(JSON.stringify(payload), {
    status: response.status,
    headers: { 'Content-Type': 'application/json', 'X-Nasra-Guard': 'upload' }
  });
}

/* Delete the stored file behind an upload ref (used when media is pruned). */
export function removeUpload(mediaStore, ref) {
  const resolved = mediaStore.resolve(uploadName(ref));
  if (!resolved) return false;
  try { fs.unlinkSync(path.join(mediaStore.dir, resolved.name)); return true; } catch { return false; }
}
