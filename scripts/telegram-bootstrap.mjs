// Secure Telegram test-channel bootstrap for the manual deployment workflow.
// Only getMe/getUpdates/getChat/getChatMember are used during discovery.
// No sendMessage/sendPhoto/sendMediaGroup or other publishing method exists here.
import fs from 'node:fs';

const mode = String(process.argv[2] || '');
const file = process.argv[3] ? String(process.argv[3]) : '';
const token = process.env.TG_BOT_TOKEN || '';
const telegramBase = process.env.TELEGRAM_API_BASE || 'https://api.telegram.org';
const insecureTest = process.env.ALLOW_INSECURE_TEST_SERVER === '1';
const sleepSignal = () => AbortSignal.timeout(25000);

function secureBase(value, label) {
  const url = new URL(value);
  if (url.protocol !== 'https:' && !(insecureTest && ['127.0.0.1', 'localhost'].includes(url.hostname))) throw new Error(`${label} must use HTTPS`);
  return value.replace(/\/+$/, '');
}
async function responseJson(response, label) {
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${label} failed (HTTP ${response.status})`);
  return data;
}
async function telegram(method, payload = {}) {
  if (!token) throw new Error('TG_BOT_TOKEN is required for Telegram setup');
  const base = secureBase(telegramBase, 'Telegram API');
  let response;
  try {
    response = await fetch(`${base}/bot${token}/${method}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), redirect: 'error', signal: sleepSignal()
    });
  } catch { throw new Error(`Telegram ${method} was unreachable`); }
  const data = await responseJson(response, `Telegram ${method}`);
  if (!data.ok) throw new Error(`Telegram ${method}: ${String(data.description || 'request rejected').slice(0, 300)}`);
  return data.result;
}
function validOwner(value) { return /^\d{1,20}$/.test(String(value || '')); }
function validChannel(value) { return /^-\d{4,20}$/.test(String(value || '')) || /^@[A-Za-z0-9_]{4,64}$/.test(String(value || '')); }
function unique(values) { return [...new Set(values.map(String))]; }
function writeBootstrap(path, data) {
  if (!path) throw new Error('An output file is required');
  fs.writeFileSync(path, JSON.stringify(data), { mode: 0o600 });
}
function readBootstrap(path) {
  if (!path) throw new Error('A bootstrap file is required');
  const value = JSON.parse(fs.readFileSync(path, 'utf8'));
  if (!validOwner(value.ownerChatId) || !validChannel(value.channelId)) throw new Error('Bootstrap data is invalid');
  return value;
}

async function discover(path) {
  const me = await telegram('getMe');
  if (!me?.is_bot || !me?.id) throw new Error('The Telegram token did not return a bot');
  let ownerChatId = String(process.env.TG_OWNER_CHAT_ID || '').trim();
  let requestedChannel = String(process.env.TELEGRAM_CHANNEL_ID || '').trim();
  if (ownerChatId && !validOwner(ownerChatId)) throw new Error('TG_OWNER_CHAT_ID must be the numeric user ID, not a username');
  if (requestedChannel && !validChannel(requestedChannel)) throw new Error('TELEGRAM_CHANNEL_ID must be a numeric channel ID or @publicusername');
  if (!ownerChatId || !requestedChannel) {
    const updates = await telegram('getUpdates', {});
    const owners = [], channels = [];
    for (const update of Array.isArray(updates) ? updates : []) {
      const message = update.message || update.edited_message;
      if (message?.chat?.type === 'private' && /^\/start(?:\s|$)/.test(String(message.text || ''))) owners.push(message.chat.id);
      const membership = update.my_chat_member;
      if (membership?.chat?.type === 'channel' && String(membership.new_chat_member?.user?.id) === String(me.id) && membership.new_chat_member?.status === 'administrator') channels.push(membership.chat.id);
      const channelPost = update.channel_post || update.edited_channel_post;
      if (channelPost?.chat?.type === 'channel') channels.push(channelPost.chat.id);
    }
    const ownerChoices = unique(owners), channelChoices = unique(channels);
    if (!ownerChatId) {
      if (ownerChoices.length !== 1) throw new Error(ownerChoices.length ? 'Multiple owners sent /start; add TG_OWNER_CHAT_ID explicitly in GitHub secrets' : 'Send /start to the bot from the owner Telegram account, then rerun the workflow');
      ownerChatId = ownerChoices[0];
    }
    if (!requestedChannel) {
      if (channelChoices.length !== 1) throw new Error(channelChoices.length ? 'Multiple test channels were found; add TELEGRAM_CHANNEL_ID explicitly in GitHub secrets' : 'Add the bot as a channel administrator with Post Messages permission, then rerun the workflow');
      requestedChannel = channelChoices[0];
    }
  }
  const channel = await telegram('getChat', { chat_id: requestedChannel });
  if (channel?.type !== 'channel' || !channel?.id) throw new Error('The configured Telegram destination is not a channel');
  const membership = await telegram('getChatMember', { chat_id: requestedChannel, user_id: me.id });
  if (membership?.status !== 'administrator' || membership?.can_post_messages === false) throw new Error('The bot must be a channel administrator with Post Messages permission');
  const canonicalChannel = String(channel.id);
  if (!validChannel(canonicalChannel)) throw new Error('Telegram returned an invalid channel ID');
  writeBootstrap(path, { ownerChatId, channelId: canonicalChannel, botId: String(me.id) });
  console.log(`Telegram discovery passed for @${me.username || 'test_bot'}; owner and channel verified. No message sent.`);
}

async function workerSession() {
  const base = secureBase(process.env.WORKER_URL || '', 'Worker URL');
  const password = process.env.OWNER_PASSWORD || '';
  if (!password) throw new Error('OWNER_PASSWORD is required');
  const response = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password }), redirect: 'error', signal: sleepSignal() });
  const login = await responseJson(response, 'Worker login');
  if (!login.session_token || !login.csrf_token) throw new Error('Worker login did not return a session');
  const headers = { 'Content-Type': 'application/json', 'X-Nasra-Session': login.session_token, 'X-CSRF-Token': login.csrf_token, Authorization: `Bearer ${login.session_token}` };
  return async (path, options = {}) => {
    const body = options.body && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body;
    const response = await fetch(`${base}${path}`, { method: options.method || 'GET', headers, body, redirect: 'error', signal: sleepSignal() });
    return await responseJson(response, `Worker ${path}`);
  };
}
async function finish(api, channelId = null) {
  const settingsResponse = await api('/api/settings');
  const current = settingsResponse.data || {};
  if (channelId && current.tg_channel_id !== channelId) await api('/api/settings', { method: 'PUT', body: { ...current, tg_channel_id: channelId } });
  await api('/api/integrations/test', { method: 'POST', body: { platform: 'telegram' } });
  await api('/api/integrations/telegram/webhook', { method: 'POST', body: {} });
  console.log('Telegram bot test, channel setting and secure webhook passed. No post or message sent.');
}

if (mode === 'discover') {
  await discover(file);
} else if (mode === 'finalize') {
  const value = readBootstrap(file);
  const api = await workerSession();
  await finish(api, value.channelId);
} else if (mode === 'has-channel') {
  const api = await workerSession();
  const settings = await api('/api/settings');
  if (!settings.data?.tg_channel_id) process.exit(2);
  console.log('Existing Telegram channel setting found.');
} else if (mode === 'finalize-existing') {
  const api = await workerSession();
  await finish(api);
} else {
  throw new Error('Usage: telegram-bootstrap.mjs discover|finalize|has-channel|finalize-existing [file]');
}
