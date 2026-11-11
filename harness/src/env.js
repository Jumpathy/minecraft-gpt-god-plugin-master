// Loads optional harness/.env (KEY=VALUE lines) into process.env without overriding variables that
// are already set. Imported first by every entry point so module-level defaults see the values.

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ENV_FILE = process.env.GOD_HARNESS_ENV || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '.env');

export function loadEnvFile(file = ENV_FILE) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    return {}; // no .env: fine
  }
  const loaded = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const m = /^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(line);
    if (!m) continue;
    let value = m[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    } else {
      value = value.replace(/\s+#.*$/, ''); // trailing comment on unquoted values
    }
    if (process.env[m[1]] === undefined) {
      process.env[m[1]] = value;
      loaded[m[1]] = value;
    }
  }
  return loaded;
}

loadEnvFile();
