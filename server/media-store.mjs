/*
 * Durable local media store for the self-hosted runtime.
 *
 * Instagram (and Telegram's own fetch) need a public HTTPS URL for every asset.
 * The Cloudflare build relied on an R2 binding; SillyDev has no R2, so generated
 * posters are written to disk and served by this process under /media/<name>.
 *
 * Safety properties:
 * - File names are random and generated here; nothing a client sends becomes a
 *   path. Reads re-validate the name against a strict pattern.
 * - Only the extensions we produce are served, always with an explicit
 *   Content-Type, so the folder can never hand out HTML/JS.
 * - Writes are atomic (temp file + rename) so a crash never leaves a half JPEG
 *   that an upstream API would happily download.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const SAFE_NAME = /^[a-z0-9][a-z0-9._-]{0,120}\.(jpg|jpeg|png|webp|mp4|mov)$/i;
const MIME_BY_EXT = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.mp4': 'video/mp4',
  '.mov': 'video/quicktime'
};

export function mediaMime(name) {
  return MIME_BY_EXT[path.extname(String(name || '')).toLowerCase()] || '';
}

export class MediaStore {
  constructor({ dir, publicBaseUrl = '', urlPath = '/media', logger = console } = {}) {
    this.dir = path.resolve(dir);
    this.urlPath = urlPath.startsWith('/') ? urlPath : `/${urlPath}`;
    this.publicBaseUrl = String(publicBaseUrl || '').replace(/\/+$/, '');
    this.logger = logger;
    fs.mkdirSync(this.dir, { recursive: true });
  }

  /* Absolute URL an external platform can download. Requires PUBLIC_BASE_URL. */
  urlFor(name) {
    if (!this.publicBaseUrl) return '';
    return `${this.publicBaseUrl}${this.urlPath}/${name}`;
  }

  get configured() {
    return Boolean(this.publicBaseUrl);
  }

  /*
   * Platforms will only download over HTTPS, and worker.js rejects anything else
   * when media links are attached. A tunnel URL or a domain satisfies this; a
   * plain http:// base never will, so the runtime must not pretend otherwise.
   */
  get fetchable() {
    return /^https:\/\//i.test(this.publicBaseUrl);
  }

  /**
   * Persist a buffer and return the metadata the worker stores in
   * content_items.media_links_json.
   *
   * `slug` is only a readable hint and is sanitised; the uniqueness comes from
   * a random suffix so two posters never overwrite each other.
   */
  save(buffer, { ext = 'jpg', slug = 'poster', meta = {} } = {}) {
    const cleanExt = String(ext || 'jpg').toLowerCase().replace(/^\./, '');
    if (!MIME_BY_EXT[`.${cleanExt}`]) throw new Error(`Unsupported media extension: ${cleanExt}`);
    const safeSlug = String(slug || 'media').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'media';
    const name = `${safeSlug}-${Date.now().toString(36)}-${crypto.randomBytes(4).toString('hex')}.${cleanExt}`;
    const target = path.join(this.dir, name);
    const temp = `${target}.tmp`;
    fs.writeFileSync(temp, buffer);
    fs.renameSync(temp, target);
    const url = this.urlFor(name);
    return {
      name,
      file: target,
      url,
      bytes: buffer.length,
      kind: cleanExt === 'mp4' || cleanExt === 'mov' ? 'video' : 'image',
      format: cleanExt === 'jpeg' ? 'jpg' : cleanExt,
      mime: MIME_BY_EXT[`.${cleanExt}`],
      ...meta
    };
  }

  /* Resolve a request path to a real file, or null when it is not ours. */
  resolve(name) {
    const raw = String(name || '');
    if (!SAFE_NAME.test(raw)) return null;
    const file = path.join(this.dir, raw);
    if (!file.startsWith(this.dir + path.sep)) return null;
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) return null;
    return { file, mime: mediaMime(raw), name: raw, bytes: fs.statSync(file).size };
  }

  /* Newest first; used by the Autopilot page to show what was actually produced. */
  list(limit = 40) {
    let names = [];
    try { names = fs.readdirSync(this.dir); } catch { return []; }
    return names
      .filter(name => SAFE_NAME.test(name))
      .map(name => {
        const file = path.join(this.dir, name);
        const stat = fs.statSync(file);
        return { name, url: this.urlFor(name), bytes: stat.size, mtime: stat.mtime.toISOString() };
      })
      .sort((a, b) => (a.mtime < b.mtime ? 1 : -1))
      .slice(0, Math.max(1, Math.min(200, Number(limit) || 40)));
  }

  /* Keep disk usage inside the SillyDev quota (default 512 MB). */
  prune({ keep = 200, maxBytes = 300 * 1024 * 1024 } = {}) {
    const files = this.list(10000);
    let total = files.reduce((n, f) => n + f.bytes, 0);
    let removed = 0;
    for (const file of files.slice(keep)) {
      if (total <= maxBytes && removed === 0) break;
      try {
        fs.unlinkSync(path.join(this.dir, file.name));
        total -= file.bytes;
        removed += 1;
      } catch { /* keep going */ }
    }
    return { removed, total_bytes: total };
  }
}
