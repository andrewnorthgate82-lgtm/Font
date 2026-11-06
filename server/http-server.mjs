/*
 * Node HTTP server for the self-hosted runtime.
 *
 * It reuses worker.js unchanged: every /api/*, /webhooks/* and /healthz request
 * is converted to a Fetch API Request, handed to worker.fetch, and the Response
 * is written back. Static files from public/ are served with the same headers
 * Cloudflare Pages applies through public/_headers.
 *
 * Why a synthetic origin is used:
 * worker.js signs cookies/CSRF against the request URL. Behind the SillyDev
 * proxy the public host is not the local socket, so the external origin is
 * rebuilt from X-Forwarded-* headers. A PUBLIC_BASE_URL makes it deterministic.
 */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.map': 'application/json; charset=utf-8'
};

const STATIC_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Cross-Origin-Opener-Policy': 'same-origin'
};

const CSP = "default-src 'self'; base-uri 'self'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self'; connect-src 'self' https:";

const MAX_BODY_BYTES = 55 * 1024 * 1024; // R2 upload route allows 50 MB
const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'content-length']);

function firstHeader(value) {
  if (Array.isArray(value)) return value[0];
  return String(value || '').split(',')[0].trim();
}

/*
 * Rebuild the origin the browser sees. X-Forwarded-* is only trusted when the
 * runtime says so (TRUST_PROXY_HTTPS), because a client could forge them.
 */
export function externalOrigin(req, config) {
  if (config.publicBaseUrl) return config.publicBaseUrl;
  const host = firstHeader(req.headers['x-forwarded-host']) || firstHeader(req.headers.host) || `127.0.0.1:${config.port}`;
  const proto = config.trustedProxyHttps ? (firstHeader(req.headers['x-forwarded-proto']) || 'http') : (req.socket?.encrypted ? 'https' : 'http');
  const safeProto = proto === 'https' ? 'https' : 'http';
  return `${safeProto}://${host}`;
}

function readRawBody(req, limit = MAX_BODY_BYTES) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) {
        const error = new Error('Request body too large');
        error.statusCode = 413;
        reject(error);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function requestHeaders(req, origin) {
  const headers = new Headers();
  for (const [key, value] of Object.entries(req.headers)) {
    if (value === undefined || HOP_BY_HOP.has(key.toLowerCase())) continue;
    if (key.toLowerCase().startsWith('x-forwarded-')) continue;
    if (key.toLowerCase() === 'x-nasra-origin') continue;
    if (Array.isArray(value)) headers.set(key, value.join(','));
    else headers.set(key, value);
  }
  // Keep the original client address for the login rate limiter and audit log.
  const forwarded = firstHeader(req.headers['x-forwarded-for']);
  if (!headers.has('x-forwarded-for')) headers.set('x-forwarded-for', forwarded || req.socket?.remoteAddress || 'local');
  headers.set('x-nasra-origin', origin);
  return headers;
}

function safeStaticPath(publicDir, urlPath) {
  let decoded;
  try { decoded = decodeURIComponent(urlPath.split('?')[0]); } catch { return null; }
  if (decoded.includes('\0')) return null;
  const relative = decoded === '/' ? 'index.html' : decoded.replace(/^\/+/, '');
  const absolute = path.resolve(publicDir, relative);
  if (absolute !== publicDir && !absolute.startsWith(publicDir + path.sep)) return null;
  return absolute;
}

function isApiPath(pathname) {
  return pathname.startsWith('/api/') || pathname.startsWith('/webhooks/') || pathname === '/healthz' || pathname === '/robots.txt';
}

export function createNasraServer({ worker, env, config, db, logger = console, preHandle = null }) {
  let closing = false;

  const server = http.createServer(async (req, res) => {
    const started = Date.now();
    try {
      if (closing) {
        res.writeHead(503, { 'Content-Type': 'application/json', Connection: 'close' });
        res.end(JSON.stringify({ ok: false, error: { code: 'SHUTTING_DOWN', message: 'server is shutting down' } }));
        return;
      }
      const origin = externalOrigin(req, config);
      const url = `${origin}${req.url || '/'}`;
      const parsed = new URL(url);
      const pathname = parsed.pathname;

      // Runtime-owned routes (/api/ops/*, /readyz) are handled before the worker.
      if (preHandle && await preHandle(req, res, parsed)) return;

      if (isApiPath(pathname)) {
        const body = ['GET', 'HEAD'].includes(req.method) ? undefined : await readRawBody(req);
        const request = new Request(url, { method: req.method, headers: requestHeaders(req, origin), body, redirect: 'manual' });
        const background = [];
        const ctx = {
          waitUntil(promise) {
            const p = Promise.resolve(promise).catch(error => logger.error('[background]', error?.message || error));
            background.push(p);
          },
          passThroughOnException() {}
        };
        const response = await worker.fetch(request, env, ctx);
        const headers = {};
        for (const [key, value] of response.headers) {
          if (HOP_BY_HOP.has(key.toLowerCase())) continue;
          headers[key] = value;
        }
        const buffer = Buffer.from(await response.arrayBuffer());
        res.writeHead(response.status, response.statusText || undefined, headers);
        res.end(buffer);
        // Let background jobs (agent runs, publishes) finish, but never hold the
        // HTTP response open for them.
        if (background.length) Promise.allSettled(background).catch(() => {});
        return;
      }

      // Static frontend (Cloudflare Pages equivalent).
      let file = safeStaticPath(config.publicDir, pathname);
      if (file && fs.existsSync(file) && fs.statSync(file).isFile()) {
        const ext = path.extname(file).toLowerCase();
        const headers = {
          ...STATIC_HEADERS,
          'Content-Type': MIME[ext] || 'application/octet-stream',
          'Content-Security-Policy': CSP,
          'X-Frame-Options': 'DENY',
          'Cache-Control': ext === '.html' ? 'no-cache' : ext === '.ttf' ? 'public, max-age=86400' : 'no-cache'
        };
        if (ext === '.html' || ext === '.js') headers['Cache-Control'] = 'no-cache';
        const buffer = fs.readFileSync(file);
        headers['Content-Length'] = buffer.length;
        res.writeHead(200, headers);
        res.end(buffer);
        return;
      }
      // Single-page app fallback for the hash router.
      const index = path.join(config.publicDir, 'index.html');
      if (fs.existsSync(index) && !path.extname(pathname)) {
        const buffer = fs.readFileSync(index);
        res.writeHead(200, { ...STATIC_HEADERS, 'Content-Type': MIME['.html'], 'Content-Security-Policy': CSP, 'Cache-Control': 'no-cache', 'Content-Length': buffer.length });
        res.end(buffer);
        return;
      }
      res.writeHead(404, { ...STATIC_HEADERS, 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found');
    } catch (error) {
      const status = error?.statusCode || 500;
      logger.error('[http]', req.method, req.url, error?.message || error);
      if (!res.headersSent) {
        res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, error: { code: status === 413 ? 'PAYLOAD_TOO_LARGE' : 'INTERNAL_ERROR', message: status === 413 ? 'payload too large' : 'internal error' } }));
      } else {
        res.end();
      }
    } finally {
      if (process.env.HTTP_ACCESS_LOG === '1') logger.log(`${req.method} ${req.url} ${res.statusCode} ${Date.now() - started}ms`);
    }
  });

  server.on('clientError', (error, socket) => {
    if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
  });

  return server;
}
