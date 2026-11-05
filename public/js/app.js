import { API_BASE } from './config.js';
import { faDigits, formatJalali, utcToJalali, relativeTime } from './jalali.js';
import { escapeHtml } from './validators.js';

export const state = { session: null, csrf: '', sessionToken: '', route: 'dashboard', integrations: null, pageLoading: false, timer: null };
const PREVIEW_SESSION_KEY = 'nasra_preview_session';
function setSessionToken(token = '') {
  state.sessionToken = token;
  try { if (token) sessionStorage.setItem(PREVIEW_SESSION_KEY, token); else sessionStorage.removeItem(PREVIEW_SESSION_KEY); } catch {}
}
try { state.sessionToken = sessionStorage.getItem(PREVIEW_SESSION_KEY) || ''; } catch {}
export { faDigits, formatJalali, utcToJalali, relativeTime, escapeHtml };
export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
export const parse = (value, fallback = null) => { if (value === null || value === undefined || value === '') return fallback; if (typeof value !== 'string') return value; try { return JSON.parse(value); } catch { return fallback; } };
export const pretty = value => escapeHtml(JSON.stringify(parse(value, value), null, 2));
export const platformName = p => ({ instagram: 'اینستاگرام', telegram: 'تلگرام', rubika: 'روبیکا' }[p] || p || '—');
export const stateName = s => ({ IDEATION_PENDING: 'ایده‌پردازی', GATE1_PENDING: 'تأیید ایده', SCRIPTING: 'سناریونویسی', FACT_CHECKING: 'راستی‌آزمایی', FACTCHECK_FAIL: 'راستی‌آزمایی ناموفق', GATE2_PENDING: 'تأیید سناریو', PRODUCTION: 'تولید', MEDIA_PENDING: 'لینک رسانه', EDITORIAL: 'تحریریه', TECH_CHECKING: 'بررسی فنی', GATE3_PENDING: 'تأیید بسته نهایی', SCHEDULING: 'تقویم‌چینی', SCHEDULED: 'زمان‌بندی‌شده', PUBLISHING: 'در حال انتشار', PUBLISHED: 'منتشرشده', REJECTED: 'ردشده', QUOTA_EXHAUSTED: 'سهمیه تمام‌شده', ERROR: 'خطا', CHANGES_REQUESTED: 'نیازمند اصلاح' }[s] || s || '—');
export const jobStatusName = s => ({ SCHEDULED: 'زمان‌بندی‌شده', MANUAL_READY: 'بسته دستی آماده', PUBLISHED: 'منتشرشده', FAILED: 'ناموفق', RETRY: 'تلاش مجدد', QUEUED_LIMIT: 'صف به‌علت سهمیه', POLLING_CONTAINER: 'بررسی پردازش ویدیو', RUNNING: 'در حال انجام', CANCELLED: 'لغوشده' }[s] || s || '—');
export function badge(text, kind = '') { return `<span class="badge ${kind}">${escapeHtml(text)}</span>`; }
export function stateBadge(s) { return `<span class="state-pill">${escapeHtml(stateName(s))}</span>`; }
export function statusBadge(s) { const kind = ['PUBLISHED', 'DONE', 'SUCCESS', 'APPROVED', 'SENT'].includes(s) ? 'ok' : ['FAILED', 'ERROR', 'REJECTED'].includes(s) ? 'danger' : ['RETRY', 'QUEUED_LIMIT', 'QUOTA_EXHAUSTED', 'MANUAL_READY', 'CHANGES_REQUESTED'].includes(s) ? 'warn' : 'info'; return badge(jobStatusName(s), kind); }
export function errorText(error) { return error?.payload?.error?.message || error?.message || 'خطای ناشناخته'; }
export function notice(text, kind = 'info') { return `<div class="notice ${kind}">${escapeHtml(text)}</div>`; }
export function loading() { return '<div class="empty"><strong>در حال دریافت داده…</strong><span>لطفاً چند لحظه صبر کنید</span></div>'; }
export function empty(title, text = '') { return `<div class="empty"><strong>${escapeHtml(title)}</strong>${text ? `<span>${escapeHtml(text)}</span>` : ''}</div>`; }

export async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  if (options.body && typeof options.body !== 'string' && !(options.body instanceof FormData)) { headers.set('Content-Type', 'application/json'); options = { ...options, body: JSON.stringify(options.body) }; }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && state.csrf && !path.includes('/auth/login')) headers.set('X-CSRF-Token', state.csrf);
  if (state.sessionToken && !path.includes('/auth/login')) headers.set('Authorization', `Bearer ${state.sessionToken}`);
  const response = await fetch(`${API_BASE}${path}`, { ...options, method, headers, credentials: 'include' });
  const text = await response.text(); let payload;
  try { payload = text ? JSON.parse(text) : {}; } catch { payload = { ok: false, error: { message: text.slice(0, 300) } }; }
  if (response.status === 401 && !path.includes('/auth/login')) showLogin();
  if (!response.ok || (payload.ok === false && payload.error)) { const error = new Error(payload?.error?.message || `HTTP ${response.status}`); error.status = response.status; error.code = payload?.error?.code; error.details = payload?.error?.details; error.payload = payload; throw error; }
  return payload;
}
export async function apiBlob(path) {
  const headers = new Headers();
  if (state.sessionToken) headers.set('Authorization', `Bearer ${state.sessionToken}`);
  const response = await fetch(`${API_BASE}${path}`, { headers, credentials: 'include' });
  if (!response.ok) throw new Error('دانلود ناموفق بود');
  return await response.blob();
}
export function toast(message, kind = '') { const el = $('#toast'); el.textContent = message; el.className = `toast show ${kind}`; clearTimeout(state.toastTimer); state.toastTimer = setTimeout(() => { el.className = 'toast'; }, 4500); }
export function showError(error) { console.error(error); toast(errorText(error), 'error'); }
export function openModal(title, html, wide = false) { $('#modal-title').textContent = title; $('#modal-body').innerHTML = html; $('#modal-root').classList.remove('hidden'); $('#modal-root').classList.toggle('modal-wide', wide); }
export function closeModal() { $('#modal-root').classList.add('hidden'); $('#modal-body').innerHTML = ''; }
export function downloadBlob(blob, filename) { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1500); }
export async function copy(text) { try { await navigator.clipboard.writeText(String(text)); toast('کپی شد', 'success'); } catch { toast('کپی خودکار ممکن نبود؛ متن را دستی انتخاب کنید', 'error'); } }
export function formData(form) { return Object.fromEntries(new FormData(form).entries()); }
export function checked(form, name) { return Boolean(form.querySelector(`[name="${name}"]`)?.checked); }
export function confirmExternal(message = 'این اقدام خارجی پس از تأیید شما انجام می‌شود. ادامه می‌دهید؟') { return window.confirm(message); }
export function countdownText(seconds) { if (!Number.isFinite(seconds) || seconds <= 0) return 'بسته شده'; const d = Math.floor(seconds / 86400), h = Math.floor((seconds % 86400) / 3600), m = Math.floor((seconds % 3600) / 60), s = Math.floor(seconds % 60); return faDigits(`${d ? `${d}روز ` : ''}${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`); }
export function startLiveWidgets() { clearInterval(state.timer); state.timer = setInterval(() => { const now = utcToJalali(); const clock = $('#live-clock'); if (clock) clock.textContent = faDigits(`${now.date} · ${now.time}`); $$('[data-countdown]').forEach(el => { const end = Number(el.dataset.countdown); const seconds = Math.floor((end - Date.now()) / 1000); el.textContent = countdownText(seconds); el.classList.toggle('expired', seconds <= 0); }); }, 1000); }

const titles = { dashboard: 'داشبورد', approvals: 'صف تأییدها', pipeline: 'کانبان تولید', calendar: 'تقویم جلالی', sources: 'کتابخانه منابع', playbook: 'پلی‌بوک و برند', agents: 'تیم AI و پرامپت‌ها', inbox: 'صندوق ورودی', analytics: 'تحلیل و آزمایش', publish: 'مرکز انتشار', integrations: 'یکپارچه‌سازی‌ها', settings: 'تنظیمات و سلامت', logs: 'لاگ و حسابرسی', backup: 'پشتیبان‌گیری', selftest: 'آزمون پذیرش', content: 'جزئیات محتوا' };
export function currentRoute() { const raw = location.hash.replace(/^#\/?/, '') || 'dashboard'; const [name, id] = raw.split('/'); return { name: titles[name] ? name : 'dashboard', id: id ? decodeURIComponent(id) : null }; }
export function navigate(route) { location.hash = `#/${route}`; }
export async function renderRoute() {
  if (!state.session) return;
  const route = currentRoute(); state.route = route.name;
  $$('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.route === route.name || (route.name === 'content' && el.dataset.route === 'pipeline')));
  $('#page-title').textContent = titles[route.name] || 'نسرا اصفهان';
  $('#page-kicker').textContent = route.name === 'content' ? 'NASRA / CONTENT DETAIL' : `NASRA / ${route.name.toUpperCase()}`;
  $('#sidebar').classList.remove('open'); $('#sidebar-backdrop').classList.add('hidden');
  const app = $('#app'); app.innerHTML = loading(); state.pageLoading = true;
  try { const module = await import('./pages.js'); const page = await module.renderPageRoute(route); app.innerHTML = page.html; if (page.mount) await page.mount(); }
  catch (error) { app.innerHTML = `<div class="notice danger"><strong>صفحه بارگذاری نشد</strong><br>${escapeHtml(errorText(error))}</div><div class="form-actions"><button class="btn ghost" id="page-retry">تلاش دوباره</button></div>`; $('#page-retry')?.addEventListener('click', renderRoute); console.error(error); }
  finally { state.pageLoading = false; }
}
export function showLogin() { state.session = null; state.csrf = ''; setSessionToken(''); $('#app-shell').classList.add('hidden'); $('#login-view').classList.remove('hidden'); }
async function showApp(session) { state.session = session; state.csrf = session.csrf_token || state.csrf; state.integrations = session.integrations || null; $('#login-view').classList.add('hidden'); $('#app-shell').classList.remove('hidden'); startLiveWidgets(); await renderRoute(); refreshApprovalCount(); }
export async function refreshApprovalCount() { try { const r = await api('/api/dashboard'); const n = r.data?.pending_gates?.length || 0; const el = $('#approval-count'); if (el) { el.textContent = faDigits(n); el.classList.toggle('hidden', !n); } } catch {} }

$('#login-form')?.addEventListener('submit', async event => { event.preventDefault(); const button = event.target.querySelector('button'); button.disabled = true; $('#login-error').textContent = ''; try { const r = await api('/api/auth/login', { method: 'POST', body: { password: $('#login-password').value, embedded: window.self !== window.top } }); setSessionToken(r.session_token || ''); state.csrf = r.csrf_token; await showApp({ authenticated: true, csrf_token: r.csrf_token }); $('#login-password').value = ''; } catch (error) { $('#login-error').textContent = errorText(error); } finally { button.disabled = false; } });
$('#logout-btn')?.addEventListener('click', async () => { try { await api('/api/auth/logout', { method: 'POST', body: { embedded: window.self !== window.top } }); } catch {} showLogin(); });
$('#refresh-page')?.addEventListener('click', renderRoute);
$('#open-sidebar')?.addEventListener('click', () => { $('#sidebar').classList.add('open'); $('#sidebar-backdrop').classList.remove('hidden'); });
$('#close-sidebar')?.addEventListener('click', () => { $('#sidebar').classList.remove('open'); $('#sidebar-backdrop').classList.add('hidden'); });
$('#sidebar-backdrop')?.addEventListener('click', () => { $('#sidebar').classList.remove('open'); $('#sidebar-backdrop').classList.add('hidden'); });
$('#main-nav')?.addEventListener('click', event => { const button = event.target.closest('[data-route]'); if (button) navigate(button.dataset.route); });
document.addEventListener('click', event => { if (event.target.closest('[data-close-modal]')) closeModal(); const route = event.target.closest('[data-content-link]'); if (route) navigate(`content/${encodeURIComponent(route.dataset.contentLink)}`); });
window.addEventListener('hashchange', renderRoute);
document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });

async function boot() { try { const r = await api('/api/auth/session'); if (r.authenticated) await showApp(r); else showLogin(); } catch { showLogin(); } }
boot();
