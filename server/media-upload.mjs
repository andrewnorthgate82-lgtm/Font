/*
 * Owner uploads for media.
 *
 * Automatically generated posters need an image API key and a public HTTPS base
 * URL, because Telegram fetches a URL. A laptop owner has neither, and the
 * pipeline then parks every item in MEDIA_PENDING forever. This module is the
 * escape hatch: the browser posts the file itself, it is written through the
 * existing MediaStore, and the bytes are handed to Telegram directly at publish
 * time instead of asking Telegram to fetch a URL it cannot reach.
 *
 * Two delivery paths, chosen per file:
 *
 * - upload:<name>  the file lives in data/media. Telegram is given the bytes
 *                  (multipart). Nothing has to be publicly reachable.
 * - https URL      unchanged behaviour: Telegram fetches it itself.
 *
 * A localhost/127.0.0.1 URL is neither: Telegram cannot fetch it from outside the
 * laptop, so publishing with one used to fail with "wrong file identifier". It is
 * now rejected at attach time with an explanation, and the upload path is the fix.
 */
import fs from 'node:fs';
import path from 'node:path';

export const UPLOAD_PREFIX = 'upload:';

/* Accepted by the panel and the API. Kept deliberately narrow: these are the
   formats the platforms accept, and MediaStore refuses anything else. */
const ALLOWED = new Map([
  ['jpg', 'image/jpeg'],
  ['jpeg', 'image/jpeg'],
  ['png', 'image/png'],
  ['webp', 'image/webp'],
  ['mp4', 'video/mp4'],
  ['mov', 'video/quicktime']
]);

export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

export function isUploadRef(url) {
  return String(url || '').startsWith(UPLOAD_PREFIX);
}

export function uploadName(url) {
  return isUploadRef(url) ? String(url).slice(UPLOAD_PREFIX.length) : '';
}

/*
 * A URL a foreign server can actually fetch. localhost and private IPs cannot be
 * fetched by Telegram/Instagram/Meta, so accepting them only moves the failure to
 * publish time, where it is much harder to understand.
 */
export function publisherReachableUrl(value) {
  let url;
  try { url = new URL(String(value || '')); } catch { return { ok: false, reason: 'آدرس معتبر نیست' }; }
  if (url.protocol !== 'https:') return { ok: false, reason: 'فقط HTTPS پذیرفته می‌شود' };
  const host = url.hostname.toLowerCase();
  const isLoopback = host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '0.0.0.0' || host.endsWith('.localhost') || host.endsWith('.local');
  const isPrivate = /^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host);
  if (isLoopback || isPrivate) {
    return {
      ok: false,
      reason: 'تلگرام و اینستاگرام نمی‌توانند آدرس localhost را از بیرون باز کنند؛ فایل را با دکمهٔ آپلود در همین صفحه بفرستید'
    };
  }
  return { ok: true, url: url.toString() };
}

function extensionOf(filename = '', mime = '') {
  const ext = String(filename).toLowerCase().split('.').pop();
  if (ALLOWED.has(ext)) return ext;
  for (const [candidate, type] of ALLOWED) if (type === String(mime).toLowerCase()) return candidate;
  return '';
}

/**
 * Read a raw request body with a hard byte ceiling.
 *
 * The panel sends exactly one file as the body (Content-Type: application/octet-
 * stream) plus the extension and a title in headers, so there is no multipart
 * parser to get wrong and no third-party dependency.
 */
export function readRawBody(req, limit = MAX_UPLOAD_BYTES) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) {
        reject(Object.assign(new Error('فایل بزرگ‌تر از حد مجاز است (۲۰ مگابایت)'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

/** Filename-safe slug from the content title, or a generic fallback. */
export function slugFrom(title = '') {
  const ascii = String(title || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
  return ascii || 'media';
}

/**
 * Store one uploaded file and return the media-link object the worker expects.
 * `mediaStore.save` already validates the extension and writes atomically.
 */
export function storeUpload(mediaStore, { buffer, filename = '', mime = '', slug = 'media' }) {
  if (!buffer || !buffer.length) throw Object.assign(new Error('فایل خالی است'), { statusCode: 400 });
  const ext = extensionOf(filename, mime);
  if (!ext) {
    throw Object.assign(new Error('فقط jpg، png، webp، mp4 و mov پذیرفته می‌شود'), { statusCode: 415 });
  }
  const saved = mediaStore.save(buffer, { ext, slug });
  return {
    url: `${UPLOAD_PREFIX}${saved.name}`,
    kind: saved.kind,
    format: saved.format,
    size_bytes: saved.bytes,
    mime: saved.mime,
    name: saved.name
  };
}

/* Absolute file path for an upload:<name> reference, or null. */
export function uploadFile(mediaStore, ref) {
  const name = uploadName(ref);
  if (!name) return null;
  return mediaStore.resolve(name);
}

/**
 * A Buffer for any media link, or null when it is a remote URL.
 * Used by the Telegram publisher, which can send bytes but cannot send a link
 * only the laptop can resolve.
 */
export function bytesFor(mediaStore, ref) {
  const resolved = uploadFile(mediaStore, ref);
  if (!resolved) return null;
  return { buffer: fs.readFileSync(resolved.file), name: path.basename(resolved.file), mime: resolved.mime };
}
