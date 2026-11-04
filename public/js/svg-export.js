// Safe, local SVG preview and 1080x1350 sRGB JPEG export; no external assets.
export function sanitizeSvg(source) {
  const doc = new DOMParser().parseFromString(String(source || ''), 'image/svg+xml');
  if (doc.querySelector('parsererror')) throw new Error('SVG معتبر نیست');
  const svg = doc.documentElement;
  if (svg.nodeName.toLowerCase() !== 'svg') throw new Error('ریشه SVG یافت نشد');
  svg.setAttribute('viewBox', '0 0 1080 1350');
  svg.setAttribute('width', '1080'); svg.setAttribute('height', '1350');
  svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  svg.querySelectorAll('script,foreignObject,iframe,object,embed,image,video,audio,style,link,animate,animateTransform,animateMotion,set').forEach(el => el.remove());
  [svg, ...svg.querySelectorAll('*')].forEach(el => {
    for (const attr of [...el.attributes]) {
      const name = attr.name.toLowerCase(), value = attr.value;
      if (name.startsWith('on') || name === 'href' || name === 'xlink:href' || /url\s*\(\s*['"]?https?:/i.test(value) || /javascript:/i.test(value)) el.removeAttribute(attr.name);
    }
  });
  return new XMLSerializer().serializeToString(svg);
}
export function svgDataUrl(svg) { return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(sanitizeSvg(svg)); }
export async function svgToJpeg(source, filename = 'nasra-poster.jpg') {
  const svg = sanitizeSvg(source);
  const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  try {
    const image = new Image();
    image.decoding = 'async';
    await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(new Error('مرورگر نتوانست SVG را rasterize کند')); image.src = url; });
    const canvas = document.createElement('canvas'); canvas.width = 1080; canvas.height = 1350;
    const ctx = canvas.getContext('2d', { alpha: false, colorSpace: 'srgb' });
    ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
    const jpeg = await new Promise((resolve, reject) => canvas.toBlob(b => b ? resolve(b) : reject(new Error('ساخت JPEG ناموفق بود')), 'image/jpeg', .92));
    const a = document.createElement('a'); const downloadUrl = URL.createObjectURL(jpeg); a.href = downloadUrl; a.download = filename; a.click();
    setTimeout(() => URL.revokeObjectURL(downloadUrl), 2000);
    return { blob: jpeg, width: 1080, height: 1350, mime: 'image/jpeg', colorSpace: 'sRGB' };
  } finally { URL.revokeObjectURL(url); }
}
export function downloadText(text, filename, mime = 'text/plain;charset=utf-8') { const blob = new Blob([String(text)], { type: mime }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
export async function copyText(text) { if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(String(text)); const area = document.createElement('textarea'); area.value = String(text); document.body.appendChild(area); area.select(); document.execCommand('copy'); area.remove(); }
