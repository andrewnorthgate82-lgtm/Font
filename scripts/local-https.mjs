#!/usr/bin/env node
/*
 * Local HTTPS wrapper for verifying the media pipeline end to end.
 *
 * Instagram and Telegram only accept https:// media URLs, and worker.js refuses
 * anything else. To test that path on a laptop without a tunnel, this starts the
 * normal server and puts a tiny HTTPS proxy in front of it with a self-signed
 * certificate for localhost.
 *
 *   node scripts/local-https.mjs            # expects server on SERVER_PORT
 *   HTTPS_PORT=9443 SERVER_PORT=8787 node scripts/local-https.mjs
 *
 * It is a development tool only: it is never imported by the runtime.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import https from 'node:https';
import http from 'node:http';
import { execFileSync } from 'node:child_process';

const HTTPS_PORT = Number(process.env.HTTPS_PORT || 9443);
const TARGET_PORT = Number(process.env.SERVER_PORT || 8787);
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nasra-tls-'));
const keyFile = path.join(dir, 'key.pem');
const certFile = path.join(dir, 'cert.pem');

try {
  execFileSync('openssl', [
    'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
    '-keyout', keyFile, '-out', certFile, '-days', '2',
    '-subj', '/CN=localhost',
    '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'
  ], { stdio: 'ignore' });
} catch (error) {
  console.error('[local-https] openssl is required to create the test certificate:', error.message);
  process.exit(1);
}

const server = https.createServer(
  { key: fs.readFileSync(keyFile), cert: fs.readFileSync(certFile) },
  (req, res) => {
    const upstream = http.request(
      { host: '127.0.0.1', port: TARGET_PORT, path: req.url, method: req.method, headers: { ...req.headers, host: `127.0.0.1:${TARGET_PORT}` } },
      response => {
        res.writeHead(response.statusCode || 502, response.headers);
        response.pipe(res);
      }
    );
    upstream.on('error', () => {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('upstream unavailable');
    });
    req.pipe(upstream);
  }
);

server.listen(HTTPS_PORT, '0.0.0.0', () => {
  console.log(`[local-https] https://localhost:${HTTPS_PORT} → http://127.0.0.1:${TARGET_PORT}`);
  console.log('[local-https] self-signed certificate: curl needs -k, and set NODE_TLS_REJECT_UNAUTHORIZED=0 for a local test run only');
});
