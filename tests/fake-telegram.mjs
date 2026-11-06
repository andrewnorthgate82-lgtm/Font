/*
 * A local fake of the Telegram Bot API.
 *
 * It exists so long polling, owner commands and the publish guard can be
 * exercised end-to-end without a token, without internet and without ever
 * touching the real bot or channel. It intentionally implements a tiny subset:
 * getMe, getUpdates, deleteWebhook, and recording endpoints for anything else.
 *
 *   node tests/fake-telegram.mjs           → prints the port it listens on
 *   TELEGRAM_BASE_URL=http://127.0.0.1:<port> ALLOW_INSECURE_TELEGRAM_BASE=1 ...
 */
import http from 'node:http';
import { pathToFileURL } from 'node:url';

export function createFakeTelegram({ botUsername = 'nasra_test_bot' } = {}) {
  const state = {
    updates: [],
    nextUpdateId: 1000,
    offset: 0,
    webhookDeleted: 0,
    getUpdatesCalls: 0,
    sent: [],
    callbacks: []
  };

  const server = http.createServer((req, res) => {
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      let body = {};
      try { body = raw ? JSON.parse(raw) : {}; } catch { body = {}; }
      const match = (req.url || '').match(/^\/bot([^/]+)\/([A-Za-z]+)$/);
      if (!match) return reply(res, 404, { ok: false, description: 'Not Found' });
      const [, token, method] = match;
      if (!token) return reply(res, 401, { ok: false, description: 'Unauthorized' });

      if (method === 'getMe') {
        return reply(res, 200, { ok: true, result: { id: 424242, is_bot: true, first_name: 'Nasra Test', username: botUsername } });
      }
      if (method === 'deleteWebhook') {
        state.webhookDeleted += 1;
        return reply(res, 200, { ok: true, result: true });
      }
      if (method === 'getUpdates') {
        state.getUpdatesCalls += 1;
        const offset = Number(body.offset || 0);
        if (offset) state.offset = offset;
        const pending = state.updates.filter(update => update.update_id >= (offset || state.offset || 0));
        state.updates = state.updates.filter(update => update.update_id >= (offset || state.offset || 0));
        // Long polling: hold the request only when there is nothing to return.
        if (!pending.length) {
          const wait = Math.min(Number(body.timeout || 0), 2) * 1000;
          const timer = setTimeout(() => reply(res, 200, { ok: true, result: [] }), Math.max(50, wait));
          req.on('close', () => clearTimeout(timer));
          return undefined;
        }
        return reply(res, 200, { ok: true, result: pending });
      }
      // Everything else is recorded, never executed against a real service.
      state.sent.push({ method, body, at: new Date().toISOString() });
      if (method === 'sendMessage') return reply(res, 200, { ok: true, result: { message_id: 900 + state.sent.length, chat: { id: body.chat_id } } });
      if (method === 'answerCallbackQuery') return reply(res, 200, { ok: true, result: true });
      return reply(res, 200, { ok: true, result: { message_id: 900 + state.sent.length } });
    });
  });

  function reply(res, status, payload) {
    const body = JSON.stringify(payload);
    res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
    res.end(body);
  }

  return {
    server,
    state,
    /* Queue one owner message. */
    pushMessage({ chatId, text, fromId = chatId, messageId = null }) {
      const update = {
        update_id: state.nextUpdateId++,
        message: {
          message_id: messageId || state.nextUpdateId,
          from: { id: Number(fromId), is_bot: false, first_name: 'Owner' },
          chat: { id: Number(chatId), type: 'private' },
          date: Math.floor(Date.now() / 1000),
          text
        }
      };
      state.updates.push(update);
      return update;
    },
    /* Queue one inline-keyboard press (gate approval flow). */
    pushCallback({ chatId, data, fromId = chatId }) {
      const update = {
        update_id: state.nextUpdateId++,
        callback_query: {
          id: `cb_${state.nextUpdateId}`,
          from: { id: Number(fromId), is_bot: false, first_name: 'Owner' },
          message: { message_id: 1, chat: { id: Number(chatId), type: 'private' }, date: Math.floor(Date.now() / 1000) },
          data
        }
      };
      state.updates.push(update);
      return update;
    },
    listen(port = 0, host = '127.0.0.1') {
      return new Promise(resolve => server.listen(port, host, () => resolve(server.address().port)));
    },
    close() {
      return new Promise(resolve => server.close(() => resolve()));
    }
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const fake = createFakeTelegram();
  const port = await fake.listen(Number(process.env.FAKE_TELEGRAM_PORT || 0), '127.0.0.1');
  console.log(`fake telegram listening on http://127.0.0.1:${port}`);
  console.log(`TELEGRAM_BASE_URL=http://127.0.0.1:${port} ALLOW_INSECURE_TELEGRAM_BASE=1`);
}
