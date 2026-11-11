// Minimal PNG reader (8-bit RGB/RGBA/gray, non-interlaced: what PrintWindow captures and the game
// writes) plus region statistics, so scenarios can check screenshots without an image library.

import { inflateSync } from 'node:zlib';
import { HarnessError } from './util.js';

const CHANNELS = { 0: 1, 2: 3, 4: 2, 6: 4 };

export function decodePng(buf) {
  if (!buf || buf.readUInt32BE(0) !== 0x89504e47) throw new HarnessError('not a PNG');
  let p = 8;
  let width, height, depth, colorType, interlace;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const type = buf.toString('ascii', p + 4, p + 8);
    const data = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      depth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    p += 12 + len;
  }
  const ch = CHANNELS[colorType];
  if (depth !== 8 || !ch || interlace) throw new HarnessError(`unsupported PNG (depth ${depth}, color type ${colorType}, interlace ${interlace})`);
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * ch;
  const px = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (stride + 1)];
    const src = y * (stride + 1) + 1;
    const dst = y * stride;
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? px[dst + x - ch] : 0;
      const b = y > 0 ? px[dst - stride + x] : 0;
      const c = x >= ch && y > 0 ? px[dst - stride + x - ch] : 0;
      let v = raw[src + x];
      if (f === 1) v += a;
      else if (f === 2) v += b;
      else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) {
        const pp = a + b - c;
        const pa = Math.abs(pp - a);
        const pb = Math.abs(pp - b);
        const pc = Math.abs(pp - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      px[dst + x] = v & 0xff;
    }
  }
  return { width, height, channels: ch, data: px };
}

/** Region as fractions of the image: {x, y, w, h} in 0..1 (default: everything). */
function bounds(img, r = {}) {
  const x0 = Math.floor((r.x ?? 0) * img.width);
  const y0 = Math.floor((r.y ?? 0) * img.height);
  const x1 = Math.min(img.width, x0 + Math.ceil((r.w ?? 1) * img.width));
  const y1 = Math.min(img.height, y0 + Math.ceil((r.h ?? 1) * img.height));
  return { x0, y0, x1, y1 };
}

/** Mean brightness (0-255) of a region. */
export function meanBrightness(img, region) {
  const { x0, y0, x1, y1 } = bounds(img, region);
  const ch = img.channels;
  let sum = 0;
  let n = 0;
  for (let y = y0; y < y1; y += 2) {
    for (let x = x0; x < x1; x += 2) {
      const i = (y * img.width + x) * ch;
      sum += ch >= 3 ? (img.data[i] + img.data[i + 1] + img.data[i + 2]) / 3 : img.data[i];
      n++;
    }
  }
  return n ? sum / n : 0;
}

/**
 * Compares two same-sized images in a region. Returns the mean absolute RGB difference (0-255) and
 * the fraction of pixels that changed by more than `threshold`.
 */
export function regionDiff(a, b, region, threshold = 40) {
  if (a.width !== b.width || a.height !== b.height) throw new HarnessError(`size mismatch ${a.width}x${a.height} vs ${b.width}x${b.height}`);
  const { x0, y0, x1, y1 } = bounds(a, region);
  let sum = 0;
  let changed = 0;
  let n = 0;
  for (let y = y0; y < y1; y += 2) {
    for (let x = x0; x < x1; x += 2) {
      const ia = (y * a.width + x) * a.channels;
      const ib = (y * b.width + x) * b.channels;
      let d = 0;
      for (let k = 0; k < 3; k++) d += Math.abs(a.data[ia + k] - b.data[ib + k]);
      d /= 3;
      sum += d;
      if (d > threshold) changed++;
      n++;
    }
  }
  return { meanDiff: n ? sum / n : 0, changedFraction: n ? changed / n : 0 };
}
