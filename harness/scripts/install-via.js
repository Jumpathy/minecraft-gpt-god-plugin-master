#!/usr/bin/env node
// Installs ViaVersion + ViaBackwards (official Modrinth releases, pinned and sha512-checked) into the
// sandbox's plugins/ folder, so mineflayer bots (which speak at most 26.1) can join the 26.3 server.
// This puts third-party plugins into the server: run it only with the server owner's consent.
//   node scripts/install-via.js            (sandbox = GOD_SANDBOX_DIR, default <repo>/../minecraft/srv1)

import '../src/env.js';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { copyFile, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { HARNESS_DIR, SANDBOX } from '../src/sandbox.js';

export const VIA_FILES = [
  {
    file: 'ViaVersion-5.12.2.jar',
    url: 'https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar',
    size: 6508809,
    sha512: 'c6af11f50e14bc1ba239f5a90be653cce85feee30c473a4b53336b4d5527d790369cf43843f7cb06d3ac3a34fc207e7203436737ca719070644101472453401b',
  },
  {
    file: 'ViaBackwards-5.12.2.jar',
    url: 'https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar',
    size: 1439641,
    sha512: '78bc7e5fd33ecf13aee31c62e1de46a854e066a1936f294347c6798371357497fe2cc6f23f1d42003ad3050f94ac5382db95e60ffa90d765fada0322b59b0ef9',
  },
];

const cacheDir = path.join(HARNESS_DIR, '.cache', 'via');
const pluginsDir = path.join(SANDBOX.dir, 'plugins');

const sha512 = (buf) => createHash('sha512').update(buf).digest('hex');

async function fetchVerified({ file, url, size, sha512: want }) {
  const cached = path.join(cacheDir, file);
  if (existsSync(cached)) {
    const buf = await readFile(cached);
    if (sha512(buf) === want) return { buf, from: 'cache' };
  }
  const res = await fetch(url, { signal: AbortSignal.timeout(120_000) });
  if (!res.ok) throw new Error(`GET ${url} -> HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length !== size) throw new Error(`${file}: expected ${size} bytes, got ${buf.length}`);
  if (sha512(buf) !== want) throw new Error(`${file}: sha512 mismatch, refusing to install`);
  await mkdir(cacheDir, { recursive: true });
  await writeFile(cached, buf);
  return { buf, from: url };
}

await mkdir(pluginsDir, { recursive: true });
const existing = await readdir(pluginsDir);
for (const spec of VIA_FILES) {
  const prefix = spec.file.replace(/-[\d.]+\.jar$/, '-');
  for (const f of existing) {
    if (f.startsWith(prefix) && f.endsWith('.jar') && f !== spec.file) {
      await rm(path.join(pluginsDir, f));
      console.log(`removed old ${f}`);
    }
  }
  const { buf, from } = await fetchVerified(spec);
  const dest = path.join(pluginsDir, spec.file);
  if (existsSync(dest) && sha512(await readFile(dest)) === spec.sha512) {
    console.log(`${spec.file}: already installed`);
    continue;
  }
  await writeFile(path.join(cacheDir, spec.file), buf);
  await copyFile(path.join(cacheDir, spec.file), dest);
  console.log(`${spec.file}: installed (${spec.size} bytes, sha512 ok, from ${from}) -> ${dest}`);
}
console.log('restart the server for the plugins to load');
