/*
 * Image generation for the self-hosted runtime.
 *
 * worker.js asks an agent (poster_designer / infographic_designer) for an SVG
 * and the browser turns it into a JPEG by hand. That manual click is the only
 * step that keeps the pipeline from running unattended, so the runtime fills it
 * by calling a real image model and storing the bytes locally.
 *
 * Provider: Gemini image models through generateContent. The model id is NOT
 * hard-coded, because Google retires image ids often: a list is tried in order
 * and the first model that answers is remembered in DATA_DIR so later calls do
 * not pay for the failed attempts again.
 *
 * Nothing here runs unless IMAGE_PROVIDER and IMAGE_API_KEY are configured, and
 * the function is exported separately so tests can inject a stub instead.
 */
import fs from 'node:fs';
import path from 'node:path';

/* Documented, current image-capable ids first; older aliases kept as fallbacks. */
export const DEFAULT_IMAGE_MODELS = [
  'gemini-2.5-flash-image',
  'gemini-2.5-flash-image-preview',
  'gemini-3-pro-image-preview'
];

const ASPECT_BY_FORMAT = {
  post: '4:5',
  carousel: '4:5',
  story: '9:16',
  reel: '9:16'
};

function cleanText(value, max = 400) {
  return String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max);
}

/**
 * Build a deterministic art-direction prompt.
 *
 * Rules that matter for this project:
 * - Every visible word is passed through verbatim, so the poster cannot invent a
 *   statistic. The model is told to copy, never to write new copy.
 * - Brand colours come from the brand kit and are only used when they are valid
 *   hex values, so a bad setting cannot become a random palette.
 * - No logos are requested. The watermark is text only, because an invented
 *   logo would be a brand and copyright problem.
 */
export function buildPosterPrompt({ brand = {}, format = 'post', textBlocks = [], layout = '', brief = '', altText = '', index = 0, total = 1 } = {}) {
  const colors = [brand.primary_color, brand.secondary_color, brand.accent_color, brand.bg_dark_color, brand.bg_light_color]
    .filter(v => /^#[0-9a-f]{3,8}$/i.test(String(v || '')))
    .map(v => String(v));
  const lines = [];
  if (!colors.length) colors.push('#0F4C5C', '#F8FAFC', '#1B998B');
  lines.push('Design a clean, modern Persian-language social media poster.');
  lines.push(`Platform format: vertical ${ASPECT_BY_FORMAT[format] || '4:5'} poster.`);
  lines.push('Strict requirements:');
  lines.push('- All text MUST be rendered in Persian (Farsi) exactly as provided below, right-to-left, with correct letter joining.');
  lines.push('- Do not translate, paraphrase, add, or invent any text, number, statistic, date, or name.');
  lines.push('- Do not add any logo, emblem, face, or official seal. Text only.');
  lines.push(`- Use this palette: ${colors.slice(0, 4).join(', ')}. Keep strong contrast and generous safe margins.`);
  lines.push('- Flat, editorial, educational style. No clutter, no stock-photo collages, no watermark.');
  if (total > 1) lines.push(`This is slide ${index + 1} of ${total}; keep the same layout grid and palette as the other slides.`);
  if (cleanText(layout)) lines.push(`Layout guidance: ${cleanText(layout, 600)}.`);
  if (cleanText(brief)) lines.push(`Design brief: ${cleanText(brief, 600)}.`);
  const blocks = (Array.isArray(textBlocks) ? textBlocks : [])
    .slice(0, 8)
    .map(block => ({ role: cleanText(block?.role || 'text', 40), text: cleanText(block?.text, 300) }))
    .filter(block => block.text);
  if (blocks.length) {
    lines.push('Text to render, in this visual hierarchy:');
    for (const block of blocks) lines.push(`  [${block.role}] «${block.text}»`);
    lines.push('If the text is too long for the layout, reduce font size — never shorten or reword it.');
  } else {
    lines.push('If no text is supplied, render only the brand watermark text and no body copy.');
  }
  if (cleanText(brand.watermark_text, 60)) lines.push(`Small watermark text at the bottom: «${cleanText(brand.watermark_text, 60)}».`);
  if (cleanText(altText)) lines.push(`(Accessibility description, do not render: ${cleanText(altText, 300)})`);
  return lines.join('\n');
}

export class ImageProvider {
  constructor({
    provider = '',
    apiKey = '',
    models = DEFAULT_IMAGE_MODELS,
    baseUrl = 'https://generativelanguage.googleapis.com/v1beta',
    timeoutMs = 120000,
    stateFile = '',
    logger = console,
    fetchImpl = fetch
  } = {}) {
    this.provider = String(provider || '').toLowerCase();
    this.apiKey = apiKey || '';
    this.models = (Array.isArray(models) ? models : String(models).split(','))
      .map(m => String(m || '').trim())
      .filter(Boolean);
    if (!this.models.length) this.models = [...DEFAULT_IMAGE_MODELS];
    this.baseUrl = String(baseUrl || '').replace(/\/+$/, '');
    this.timeoutMs = Math.max(10000, Number(timeoutMs) || 120000);
    this.stateFile = stateFile;
    this.logger = logger;
    this.fetchImpl = fetchImpl;
    this.workingModel = this.#loadModel();
    this.stats = { calls: 0, images: 0, failures: 0, last_error: null, last_model: null };
  }

  get configured() {
    return this.provider === 'gemini' && Boolean(this.apiKey);
  }

  /* Only the ids we will actually try, working one first. */
  get candidates() {
    const rest = this.models.filter(m => m !== this.workingModel);
    return this.workingModel ? [this.workingModel, ...rest] : rest;
  }

  #loadModel() {
    if (!this.stateFile) return '';
    try {
      const parsed = JSON.parse(fs.readFileSync(this.stateFile, 'utf8'));
      return typeof parsed?.model === 'string' ? parsed.model : '';
    } catch { return ''; }
  }

  #saveModel(model) {
    this.workingModel = model;
    if (!this.stateFile) return;
    try {
      fs.mkdirSync(path.dirname(this.stateFile), { recursive: true });
      fs.writeFileSync(this.stateFile, JSON.stringify({ model, at: new Date().toISOString() }, null, 2));
    } catch (error) {
      this.logger.warn?.(`[image] could not persist the working model: ${error.message}`);
    }
  }

  /**
   * Generate one image. Returns { buffer, mime, model } or throws.
   *
   * Errors are classified the same way the text provider classifies them, so the
   * caller can decide between "retry later" and "this needs the owner".
   */
  async generate(prompt, { aspectRatio = '4:5', imageSize = '1K' } = {}) {
    if (!this.configured) {
      const error = new Error('IMAGE_PROVIDER یا IMAGE_API_KEY تنظیم نشده است');
      error.code = 'NOT_CONFIGURED';
      throw error;
    }
    const attempts = [];
    for (const model of this.candidates) {
      this.stats.calls += 1;
      try {
        const result = await this.#callGemini(model, prompt, { aspectRatio, imageSize });
        this.#saveModel(model);
        this.stats.images += 1;
        this.stats.last_model = model;
        this.stats.last_error = null;
        return { ...result, model, attempts };
      } catch (error) {
        attempts.push({ model, code: error.code || 'IMAGE_ERROR', message: String(error.message).slice(0, 300) });
        this.logger.warn?.(`[image] ${model} failed: ${error.code || ''} ${error.message}`);
        /* A hard configuration problem will not be fixed by another model id. */
        if (error.code === 'IMAGE_AUTH' || error.code === 'IMAGE_QUOTA') throw error;
      }
    }
    this.stats.failures += 1;
    const last = attempts[attempts.length - 1] || { message: 'no image model available' };
    this.stats.last_error = last.message;
    const error = new Error(`تولید تصویر ناموفق بود: ${last.message}`);
    error.code = 'IMAGE_FAILED';
    error.attempts = attempts;
    throw error;
  }

  async #callGemini(model, prompt, { aspectRatio, imageSize }) {
    const url = `${this.baseUrl}/models/${encodeURIComponent(model)}:generateContent`;
    const response = await this.fetchImpl(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': this.apiKey },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { responseModalities: ['IMAGE'], imageConfig: { aspectRatio, imageSize } }
      }),
      signal: AbortSignal.timeout(this.timeoutMs)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = String(data?.error?.message || `HTTP ${response.status}`).slice(0, 400);
      const error = new Error(`Gemini Image ${response.status}: ${message}`);
      if (response.status === 401 || response.status === 403) error.code = 'IMAGE_AUTH';
      else if (response.status === 429) error.code = 'IMAGE_QUOTA';
      else if (response.status === 404) error.code = 'IMAGE_MODEL_MISSING';
      else error.code = 'IMAGE_HTTP';
      throw error;
    }
    const parts = data?.candidates?.[0]?.content?.parts || [];
    const inline = parts.map(part => part?.inlineData || part?.inline_data).find(Boolean);
    if (!inline?.data) {
      const blocked = data?.promptFeedback?.blockReason || data?.candidates?.[0]?.finishReason;
      const error = new Error(`پاسخ مدل تصویر دادهای نداشت${blocked ? ` (${blocked})` : ''}`);
      error.code = blocked ? 'IMAGE_BLOCKED' : 'IMAGE_EMPTY';
      throw error;
    }
    return { buffer: Buffer.from(inline.data, 'base64'), mime: inline.mimeType || inline.mime_type || 'image/png' };
  }

  status() {
    return {
      provider: this.provider || 'none',
      configured: this.configured,
      models: this.models,
      working_model: this.workingModel || null,
      stats: { ...this.stats }
    };
  }
}

/* Gemini returns PNG; the publish specs want JPEG. Extension is derived here. */
export function extensionForMime(mime) {
  const value = String(mime || '').toLowerCase();
  if (value.includes('jpeg') || value.includes('jpg')) return 'jpg';
  if (value.includes('webp')) return 'webp';
  return 'png';
}
