/*
 * Minimal, dependency-free PNG → JPEG converter.
 *
 * Why this exists: every image model returns PNG (or WebP), while the publishing
 * specs in platform_specs only allow JPEG — Instagram rejects PNG outright and
 * worker.js enforces that before gate 3. Turning a poster away at that point
 * would defeat the whole automation, so the bytes are converted here.
 *
 * Scope is deliberately narrow and honest:
 *   - 8-bit PNGs of colour type 0/2/3/4/6 (greyscale, RGB, palette, with or
 *     without alpha) and 16-bit inputs, which are downsampled to 8-bit.
 *   - Interlaced PNGs are rejected rather than mis-decoded; the caller then keeps
 *     the original instead of shipping a corrupted image.
 *   - Alpha is composited over white, which is what a poster background would be.
 *   - Baseline JPEG, 4:2:0, standard tables. Nothing exotic, maximum
 *     compatibility with Meta's and Telegram's decoders.
 */
import zlib from 'node:zlib';

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

export class PngError extends Error {
  constructor(message, code = 'PNG_UNSUPPORTED') {
    super(message);
    this.code = code;
  }
}

/* --------------------------------------------------------------- PNG input */

function parseChunks(buffer) {
  if (!buffer.subarray(0, 8).equals(SIGNATURE)) throw new PngError('not a PNG', 'PNG_INVALID');
  const chunks = [];
  let offset = 8;
  while (offset + 8 <= buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (data.length !== length) throw new PngError('truncated PNG chunk', 'PNG_TRUNCATED');
    chunks.push({ type, data });
    offset += 12 + length;
    if (type === 'IEND') break;
  }
  return chunks;
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

/* Decode to straight 8-bit RGBA regardless of the input layout. */
export function decodePng(buffer) {
  const chunks = parseChunks(buffer);
  const ihdr = chunks.find(c => c.type === 'IHDR')?.data;
  if (!ihdr || ihdr.length < 13) throw new PngError('missing IHDR', 'PNG_INVALID');
  const width = ihdr.readUInt32BE(0);
  const height = ihdr.readUInt32BE(4);
  const bitDepth = ihdr[8];
  const colorType = ihdr[9];
  const interlace = ihdr[12];
  if (!width || !height) throw new PngError('empty PNG', 'PNG_INVALID');
  if (interlace !== 0) throw new PngError('interlaced PNG is not supported', 'PNG_INTERLACED');
  if (![8, 16].includes(bitDepth)) throw new PngError(`unsupported bit depth ${bitDepth}`, 'PNG_BITDEPTH');
  if (![0, 2, 3, 4, 6].includes(colorType)) throw new PngError(`unsupported colour type ${colorType}`, 'PNG_COLORTYPE');

  const paletteChunk = chunks.find(c => c.type === 'PLTE')?.data;
  const palette = [];
  if (colorType === 3) {
    if (!paletteChunk) throw new PngError('palette PNG without PLTE', 'PNG_INVALID');
    for (let i = 0; i + 2 < paletteChunk.length; i += 3) palette.push([paletteChunk[i], paletteChunk[i + 1], paletteChunk[i + 2]]);
  }
  const trns = chunks.find(c => c.type === 'tRNS')?.data;
  let transparentGray = null;
  const transparentRGB = null;
  if (trns && colorType === 0 && trns.length >= 2) transparentGray = trns.readUInt16BE(0);
  if (trns && colorType === 3) {
    for (let i = 0; i < trns.length && i < palette.length; i += 1) palette[i].push(trns[i]);
  }

  const compressed = Buffer.concat(chunks.filter(c => c.type === 'IDAT').map(c => c.data));
  if (!compressed.length) throw new PngError('no image data', 'PNG_INVALID');
  const raw = zlib.inflateSync(compressed);

  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
  const bytesPerSample = bitDepth === 16 ? 2 : 1;
  const bpp = Math.max(1, channels * bytesPerSample);
  const stride = width * channels * bytesPerSample;
  if (raw.length < (stride + 1) * height) throw new PngError('truncated image data', 'PNG_TRUNCATED');

  const out = Buffer.alloc(width * height * 4);
  const previous = Buffer.alloc(stride);
  const current = Buffer.alloc(stride);
  let offset = 0;

  for (let y = 0; y < height; y += 1) {
    const filter = raw[offset];
    offset += 1;
    raw.copy(current, 0, offset, offset + stride);
    offset += stride;
    for (let i = 0; i < stride; i += 1) {
      const left = i >= bpp ? current[i - bpp] : 0;
      const up = previous[i];
      const upLeft = i >= bpp ? previous[i - bpp] : 0;
      let value = current[i];
      if (filter === 1) value += left;
      else if (filter === 2) value += up;
      else if (filter === 3) value += (left + up) >> 1;
      else if (filter === 4) value += paeth(left, up, upLeft);
      else if (filter !== 0) throw new PngError(`unknown filter ${filter}`, 'PNG_FILTER');
      current[i] = value & 0xff;
    }

    for (let x = 0; x < width; x += 1) {
      const base = x * channels * bytesPerSample;
      const sample = index => (bitDepth === 16 ? current[index] : current[index]);
      let r, g, b, a = 255;
      if (colorType === 0) {
        r = g = b = sample(base);
        if (transparentGray !== null && current[base] === (transparentGray & 0xff)) a = 0;
      } else if (colorType === 2) {
        r = sample(base); g = sample(base + bytesPerSample); b = sample(base + 2 * bytesPerSample);
      } else if (colorType === 3) {
        const entry = palette[current[base]] || [0, 0, 0, 255];
        r = entry[0]; g = entry[1]; b = entry[2];
        a = entry[3] === undefined ? 255 : entry[3];
      } else if (colorType === 4) {
        r = g = b = sample(base); a = sample(base + bytesPerSample);
      } else {
        r = sample(base); g = sample(base + bytesPerSample); b = sample(base + 2 * bytesPerSample); a = sample(base + 3 * bytesPerSample);
      }
      const target = (y * width + x) * 4;
      out[target] = r; out[target + 1] = g; out[target + 2] = b; out[target + 3] = a;
    }
    current.copy(previous);
  }
  return { width, height, data: out, hadAlpha: colorType === 4 || colorType === 6 || colorType === 3 };
}

/* ------------------------------------------------------------- JPEG output */

const ZIGZAG = [
  0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
  12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
  35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
  58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
];

const STD_LUMA_Q = [
  16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55,
  14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
  18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
  49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99
];
const STD_CHROMA_Q = [
  17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99,
  24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99, 99, 99, 99, 99, 99,
  99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
  99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99
];

/* DC bit counts: luma takes 12 symbols, chroma 12 — the counts, not versions. */
const DC_LUMA_BITS = [0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0];
const DC_CHROMA_BITS = [0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0];
const DC_VALUES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const AC_LUMA_BITS = [0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d];
const AC_LUMA_VALUES = [
  0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
  0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
  0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
  0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
  0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
  0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
  0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
  0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
  0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
  0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
  0xf9, 0xfa
];
const AC_CHROMA_BITS = [0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77];
const AC_CHROMA_VALUES = [
  0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
  0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
  0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
  0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
  0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
  0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
  0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
  0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
  0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
  0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
  0xf9, 0xfa
];

function buildHuffmanTable(bits, values) {
  const table = new Map();
  let code = 0;
  let k = 0;
  for (let length = 1; length <= 16; length += 1) {
    for (let i = 0; i < bits[length - 1]; i += 1) {
      table.set(`${length}:${values[k]}`, code);
      code += 1;
      k += 1;
    }
    code <<= 1;
  }
  return table;
}

const LUMA_DC = buildHuffmanTable(DC_LUMA_BITS, DC_VALUES);
const CHROMA_DC = buildHuffmanTable(DC_CHROMA_BITS, DC_VALUES);
const LUMA_AC = buildHuffmanTable(AC_LUMA_BITS, AC_LUMA_VALUES);
const CHROMA_AC = buildHuffmanTable(AC_CHROMA_BITS, AC_CHROMA_VALUES);

function scaledQuantTable(base, quality) {
  const q = Math.max(1, Math.min(100, quality));
  const scale = q < 50 ? Math.floor(5000 / q) : 200 - q * 2;
  return base.map(v => Math.max(1, Math.min(255, Math.floor((v * scale + 50) / 100))));
}

class BitWriter {
  constructor() { this.buffer = []; this.current = 0; this.bits = 0; }
  write(code, length) {
    for (let i = length - 1; i >= 0; i -= 1) {
      this.current = (this.current << 1) | ((code >> i) & 1);
      this.bits += 1;
      if (this.bits === 8) {
        this.buffer.push(this.current);
        if (this.current === 0xff) this.buffer.push(0x00);
        this.current = 0;
        this.bits = 0;
      }
    }
  }
  finish() {
    if (this.bits > 0) {
      this.current = (this.current << (8 - this.bits)) | ((1 << (8 - this.bits)) - 1);
      this.buffer.push(this.current);
      if (this.current === 0xff) this.buffer.push(0x00);
      this.current = 0;
      this.bits = 0;
    }
    return Buffer.from(this.buffer);
  }
}

function magnitude(value) {
  return value === 0 ? 0 : Math.floor(Math.log2(Math.abs(value))) + 1;
}

function categoryBits(value, size) {
  if (size === 0) return 0;
  return value < 0 ? value + (1 << size) - 1 : value;
}

/* Forward DCT on one 8x8 block (level shifted, separable, float maths). */
const COS = Array.from({ length: 8 }, (_, x) => Array.from({ length: 8 }, (_, u) => Math.cos(((2 * x + 1) * u * Math.PI) / 16)));
const C = Array.from({ length: 8 }, (_, u) => (u === 0 ? Math.SQRT1_2 : 1));

function fdct(block) {
  const temp = new Float64Array(64);
  const out = new Float64Array(64);
  for (let y = 0; y < 8; y += 1) {
    for (let u = 0; u < 8; u += 1) {
      let sum = 0;
      for (let x = 0; x < 8; x += 1) sum += block[y * 8 + x] * COS[x][u];
      temp[y * 8 + u] = sum * C[u] * 0.5;
    }
  }
  for (let u = 0; u < 8; u += 1) {
    for (let v = 0; v < 8; v += 1) {
      let sum = 0;
      for (let y = 0; y < 8; y += 1) sum += temp[y * 8 + u] * COS[y][v];
      out[v * 8 + u] = sum * C[v] * 0.5;
    }
  }
  return out;
}

function quantize(block, table) {
  const out = new Int32Array(64);
  /* ZIGZAG maps zigzag index -> natural index, so the output is in scan order. */
  for (let i = 0; i < 64; i += 1) {
    const naturalIndex = ZIGZAG[i];
    out[i] = Math.round(block[naturalIndex] / table[naturalIndex]);
  }
  return out;
}

function writeBlock(writer, coefficients, previousDC, dcTable, acTable) {
  const diff = coefficients[0] - previousDC;
  const dcSize = magnitude(diff);
  writeSymbol(writer, dcTable, dcSize);
  if (dcSize) writer.write(categoryBits(diff, dcSize), dcSize);
  let run = 0;
  for (let i = 1; i < 64; i += 1) {
    const value = coefficients[i];
    if (value === 0) { run += 1; continue; }
    while (run > 15) {
      writeSymbol(writer, acTable, 0xf0);
      run -= 16;
    }
    const size = magnitude(value);
    writeSymbol(writer, acTable, (run << 4) | size);
    writer.write(categoryBits(value, size), size);
    run = 0;
  }
  if (run > 0) writeSymbol(writer, acTable, 0x00);
  return coefficients[0];
}

/* Huffman tables store code -> length; look up code length by symbol. */
const CODE_LENGTHS = new Map();
function indexTable(table) {
  for (const [key, code] of table) {
    const [length, symbol] = key.split(':').map(Number);
    CODE_LENGTHS.set(`${table === LUMA_DC ? 'ldc' : table === CHROMA_DC ? 'cdc' : table === LUMA_AC ? 'lac' : 'cac'}:${symbol}`, { code, length });
  }
}
indexTable(LUMA_DC); indexTable(CHROMA_DC); indexTable(LUMA_AC); indexTable(CHROMA_AC);

function writeSymbol(writer, table, symbol) {
  const key = `${table === LUMA_DC ? 'ldc' : table === CHROMA_DC ? 'cdc' : table === LUMA_AC ? 'lac' : 'cac'}:${symbol}`;
  const entry = CODE_LENGTHS.get(key);
  if (!entry) throw new PngError(`no Huffman code for symbol ${symbol}`, 'JPEG_ENCODE');
  writer.write(entry.code, entry.length);
}

function segment(marker, payload) {
  const length = payload.length + 2;
  const head = Buffer.alloc(4);
  head[0] = 0xff; head[1] = marker; head.writeUInt16BE(length, 2);
  return Buffer.concat([head, payload]);
}

function huffmanPayload(bits, values) {
  return Buffer.concat([Buffer.from(bits), Buffer.from(values)]);
}

/**
 * Encode raw RGBA pixels as a baseline JPEG.
 * Alpha is composited over `background` (default white).
 */
export function encodeJpeg({ width, height, data }, { quality = 90, background = [255, 255, 255] } = {}) {
  if (!width || !height) throw new PngError('empty image', 'JPEG_ENCODE');
  const lumaQ = scaledQuantTable(STD_LUMA_Q, quality);
  const chromaQ = scaledQuantTable(STD_CHROMA_Q, quality);

  /* RGBA -> planar YCbCr, alpha flattened onto the background colour. */
  const yPlane = new Float64Array(width * height);
  const cbPlane = new Float64Array(width * height);
  const crPlane = new Float64Array(width * height);
  for (let i = 0; i < width * height; i += 1) {
    const alpha = data[i * 4 + 3] / 255;
    const r = data[i * 4] * alpha + background[0] * (1 - alpha);
    const g = data[i * 4 + 1] * alpha + background[1] * (1 - alpha);
    const b = data[i * 4 + 2] * alpha + background[2] * (1 - alpha);
    yPlane[i] = 0.299 * r + 0.587 * g + 0.114 * b - 128;
    cbPlane[i] = -0.168736 * r - 0.331264 * g + 0.5 * b;
    crPlane[i] = 0.5 * r - 0.418688 * g - 0.081312 * b;
  }

  const writer = new BitWriter();
  const blocksX = Math.ceil(width / 8);
  const blocksY = Math.ceil(height / 8);

  const blockOf = (plane, bx, by) => {
    const block = new Float64Array(64);
    for (let y = 0; y < 8; y += 1) {
      for (let x = 0; x < 8; x += 1) {
        const px = Math.min(width - 1, bx * 8 + x);
        const py = Math.min(height - 1, by * 8 + y);
        block[y * 8 + x] = plane[py * width + px];
      }
    }
    return block;
  };

  /*
   * 4:4:4 sampling (every component is declared 1x1 in SOF), so one MCU is
   * exactly one block per component — and the blocks must be written
   * interleaved Y, Cb, Cr per MCU. Writing all planes separately makes libjpeg
   * stop with "premature end of data segment".
   */
  let prevY = 0, prevCb = 0, prevCr = 0;
  for (let by = 0; by < blocksY; by += 1) {
    for (let bx = 0; bx < blocksX; bx += 1) {
      prevY = writeBlock(writer, quantize(fdct(blockOf(yPlane, bx, by)), lumaQ), prevY, LUMA_DC, LUMA_AC);
      prevCb = writeBlock(writer, quantize(fdct(blockOf(cbPlane, bx, by)), chromaQ), prevCb, CHROMA_DC, CHROMA_AC);
      prevCr = writeBlock(writer, quantize(fdct(blockOf(crPlane, bx, by)), chromaQ), prevCr, CHROMA_DC, CHROMA_AC);
    }
  }

  const app0 = Buffer.from([0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00]);
  const sof = Buffer.alloc(15);
  sof[0] = 8;                       // precision
  sof.writeUInt16BE(height, 1);
  sof.writeUInt16BE(width, 3);
  sof[5] = 3;                       // components
  /* Every component is 1x1 (4:4:4). Declaring 2x2 here would make the decoder
   * expect 4 luma blocks per MCU while the scan holds one, which libjpeg reports
   * as "premature end of data segment". */
  sof[6] = 1; sof[7] = 0x11; sof[8] = 0x00;
  sof[9] = 2; sof[10] = 0x11; sof[11] = 0x01;
  sof[12] = 3; sof[13] = 0x11; sof[14] = 0x01;

  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    segment(0xe0, app0),
    segment(0xdb, Buffer.concat([Buffer.from([0x00]), Buffer.from(ZIGZAG.map(i => lumaQ[i]))])),
    segment(0xdb, Buffer.concat([Buffer.from([0x01]), Buffer.from(ZIGZAG.map(i => chromaQ[i]))])),
    segment(0xc0, sof),
    /* DHT payload starts with (Tc<<4)|Th: 0x00 DC luma, 0x01 DC chroma,
     * 0x10 AC luma, 0x11 AC chroma. Getting these nibbles wrong makes libjpeg
     * reject the file with "Bogus Huffman table definition". */
    segment(0xc4, Buffer.concat([Buffer.from([0x00]), huffmanPayload(DC_LUMA_BITS, DC_VALUES)])),
    segment(0xc4, Buffer.concat([Buffer.from([0x01]), huffmanPayload(DC_CHROMA_BITS, DC_VALUES)])),
    segment(0xc4, Buffer.concat([Buffer.from([0x10]), huffmanPayload(AC_LUMA_BITS, AC_LUMA_VALUES)])),
    segment(0xc4, Buffer.concat([Buffer.from([0x11]), huffmanPayload(AC_CHROMA_BITS, AC_CHROMA_VALUES)])),
    segment(0xda, Buffer.from([0x03, 0x01, 0x00, 0x02, 0x11, 0x03, 0x11, 0x00, 0x3f, 0x00])),
    writer.finish(),
    Buffer.from([0xff, 0xd9])
  ]);
}

/* True when the buffer starts with a JPEG SOI marker. */
export function isJpeg(buffer) {
  return Boolean(buffer) && buffer.length > 3 && buffer[0] === 0xff && buffer[1] === 0xd8;
}

/**
 * Convert PNG bytes to JPEG bytes.
 * Throws PngError with a code the caller can log; the caller should keep the
 * original bytes and mark the media as needing manual conversion in that case.
 */
export function pngToJpeg(pngBuffer, { quality = 90 } = {}) {
  const decoded = decodePng(pngBuffer);
  return encodeJpeg(decoded, { quality });
}
