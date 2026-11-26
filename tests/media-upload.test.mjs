/*
 * Uploaded media: the path that replaces "get an image API key and a public URL".
 *
 *   node --test tests/media-upload.test.mjs
 *
 * Everything here is local: the Telegram call is captured before it can leave the
 * process, and no real channel is used. The point is that a file living only on
 * the owner's laptop can reach Telegram, and that a link Telegram could never
 * fetch is refused early instead of failing at publish time.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { MediaStore } from '../server/media-store.mjs';
import {
  UPLOAD_PREFIX, isUploadRef, uploadName, publisherReachableUrl,
  storeUpload, uploadFile, bytesFor, MAX_UPLOAD_BYTES, slugFrom
} from '../server/media-upload.mjs';
import { needsMultipartUpload, performMultipartUpload } from '../server/telegram-media.mjs';

function tempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-upload-'));
}

/* A 1x1 PNG: real bytes, so size and mime handling are exercised. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==',
  'base64'
);

test('a stored upload is referenced, resolvable and readable as bytes', () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  const link = storeUpload(store, { buffer: PNG, filename: 'poster.png', mime: 'image/png', slug: slugFrom('My Poster 🎨') });

  assert.equal(link.url, `${UPLOAD_PREFIX}${link.name}`);
  assert.ok(isUploadRef(link.url));
  assert.equal(uploadName(link.url), link.name);
  assert.match(link.name, /^my-poster-[a-z0-9]+-[a-f0-9]{8}\.png$/, 'a non-ASCII title still yields a safe ASCII name');
  assert.equal(link.kind, 'image');
  assert.equal(link.size_bytes, PNG.length);

  /* No PUBLIC_BASE_URL is needed: the store itself must not invent a URL. */
  assert.equal(store.configured, false);
  const resolved = uploadFile(store, link.url);
  assert.ok(resolved && resolved.mime === 'image/png');
  const bytes = bytesFor(store, link.url);
  assert.deepEqual(bytes.buffer, PNG);
  assert.equal(bytes.mime, 'image/png');
});

test('uploads refuse what the platforms cannot accept', () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  assert.throws(() => storeUpload(store, { buffer: Buffer.from('x'), filename: 'evil.html', mime: 'text/html' }), /فقط jpg/);
  assert.throws(() => storeUpload(store, { buffer: Buffer.alloc(0), filename: 'a.png', mime: 'image/png' }), /خالی/);
  assert.equal(MAX_UPLOAD_BYTES, 20 * 1024 * 1024);
});

test('a localhost link is refused with the upload as the stated fix', () => {
  const local = publisherReachableUrl('https://localhost:8787/media/a.jpg');
  assert.equal(local.ok, false);
  assert.match(local.reason, /آپلود/);

  assert.equal(publisherReachableUrl('https://127.0.0.1/a.jpg').ok, false);
  assert.equal(publisherReachableUrl('http://example.com/a.jpg').ok, false, 'plain http is refused');
  assert.equal(publisherReachableUrl('https://example.com/a.jpg').ok, true);
});

test('an uploaded file is sent to Telegram as bytes, not as a URL', async () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  const link = storeUpload(store, { buffer: PNG, filename: 'a.png', mime: 'image/png' });
  const url = 'https://api.telegram.org/bot123:abc/sendPhoto';
  const init = {
    method: 'POST',
    body: JSON.stringify({ chat_id: '@channel', photo: link.url, caption: 'سلام' })
  };

  const plan = needsMultipartUpload(url, init);
  assert.ok(plan, 'the guard must recognise an upload reference');
  assert.equal(plan.method, 'sendphoto');

  let captured = null;
  const realFetch = async (target, options) => {
    captured = { target, options };
    return new Response(JSON.stringify({ ok: true, result: { message_id: 7 } }), { headers: { 'Content-Type': 'application/json' } });
  };
  const response = await performMultipartUpload(realFetch, store, { url, init, plan });
  assert.equal(response.status, 200);

  assert.equal(captured.target, url);
  assert.ok(captured.options.body instanceof FormData, 'the body must be multipart');
  assert.equal(captured.options.body.get('chat_id'), '@channel');
  assert.equal(captured.options.body.get('caption'), 'سلام');
  const file = captured.options.body.get('photo');
  assert.ok(file instanceof Blob, 'the actual file is attached');
  assert.deepEqual(Buffer.from(await file.arrayBuffer()), PNG);
});

test('an album of uploads becomes attach:// references', async () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  const first = storeUpload(store, { buffer: PNG, filename: '1.png', mime: 'image/png' });
  const second = storeUpload(store, { buffer: PNG, filename: '2.jpg', mime: 'image/jpeg' });
  const url = 'https://api.telegram.org/bot123:abc/sendMediaGroup';
  const init = {
    method: 'POST',
    body: JSON.stringify({ chat_id: '@channel', media: [{ type: 'photo', media: first.url, caption: 'c' }, { type: 'photo', media: second.url }] })
  };
  const plan = needsMultipartUpload(url, init);
  let captured = null;
  const realFetch = async (target, options) => { captured = { target, options }; return new Response(JSON.stringify({ ok: true, result: [] })); };
  await performMultipartUpload(realFetch, store, { url, init, plan });

  const media = JSON.parse(captured.options.body.get('media'));
  assert.equal(media.length, 2);
  assert.equal(media[0].media, 'attach://file0');
  assert.equal(media[1].media, 'attach://file1');
  assert.ok(captured.options.body.get('file0') instanceof Blob);
});

test('a missing file is reported, never silently skipped', async () => {
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  const url = 'https://api.telegram.org/bot123:abc/sendPhoto';
  const init = { method: 'POST', body: JSON.stringify({ chat_id: '@c', photo: 'upload:missing-file.jpg' }) };
  const plan = needsMultipartUpload(url, init);
  const response = await performMultipartUpload(async () => { throw new Error('must not send'); }, store, { url, init, plan });
  assert.equal(response.status, 400);
  const payload = await response.json();
  assert.match(payload.description, /پیدا نشد/);
});

test('a remote URL keeps the old JSON path', () => {
  const url = 'https://api.telegram.org/bot123:abc/sendPhoto';
  const init = { method: 'POST', body: JSON.stringify({ chat_id: '@c', photo: 'https://example.com/a.jpg' }) };
  assert.equal(needsMultipartUpload(url, init), null, 'no upload, no interception');
});

/* ---------------------------------------------- the whole publish decision */

test('the guard sends an uploaded photo as multipart, and only when approved', async () => {
  const { PublishGuard, guardedFetch } = await import('../server/publish-guard.mjs');
  const store = new MediaStore({ dir: tempDir(), publicBaseUrl: '', logger: { warn() {} } });
  const link = storeUpload(store, { buffer: PNG, filename: 'a.png', mime: 'image/png' });
  const guardUrl = 'https://api.telegram.org/bot123:abc/sendPhoto';

  const sent = [];
  const realFetch = async (target, options) => {
    sent.push({ target, options });
    return new Response(JSON.stringify({ ok: true, result: { message_id: 1 } }), { headers: { 'Content-Type': 'application/json' } });
  };
  const quiet = { warn() {}, log() {} };
  const init = () => ({ method: 'POST', body: JSON.stringify({ chat_id: '@testchannel', photo: link.url, caption: 'c' }) });

  /* dry_run: nothing leaves the process, no file is read. */
  const dry = new PublishGuard({ mode: 'dry_run', allowlist: ['@testchannel'], ownerChatIds: [], logger: quiet });
  const dryResponse = await guardedFetch({ guard: dry, realFetch, mediaStore: store, logger: quiet })(guardUrl, init());
  assert.equal((await dryResponse.json()).result.dry_run, true);
  assert.equal(sent.length, 0, 'dry_run must not send the file either');

  /* live without approval: blocked. */
  const live = new PublishGuard({ mode: 'live', allowlist: ['@testchannel'], ownerChatIds: [], logger: quiet });
  await guardedFetch({ guard: live, realFetch, mediaStore: store, logger: quiet })(guardUrl, init());
  assert.equal(sent.length, 0, 'an unapproved publish is blocked');

  /* live with approval: the bytes go out as multipart. */
  live.issueApproval({ platform: 'telegram', action: 'sendphoto', destination: '@testchannel', note: 'test' });
  const response = await guardedFetch({ guard: live, realFetch, mediaStore: store, logger: quiet })(guardUrl, init());
  assert.equal(response.status, 200);
  assert.equal(sent.length, 1);
  assert.ok(sent[0].options.body instanceof FormData);
  assert.deepEqual(Buffer.from(await sent[0].options.body.get('photo').arrayBuffer()), PNG);
});
