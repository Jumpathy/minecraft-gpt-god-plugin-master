// Small helpers shared by the bot manager, bridge client, MCP server and scenarios.

export class HarnessError extends Error {
  constructor(message, details) {
    super(message);
    this.name = 'HarnessError';
    this.details = details;
  }
}

export const sleep = (ms) => new Promise((res) => setTimeout(res, ms));

/** Accepts "text" (case-insensitive by default) or "/body/flags" regex literal syntax. */
export function compileRegex(pattern, flags = 'i') {
  if (pattern instanceof RegExp) return pattern;
  const lit = /^\/(.+)\/([a-z]*)$/s.exec(String(pattern));
  try {
    return lit ? new RegExp(lit[1], lit[2]) : new RegExp(String(pattern), flags);
  } catch (e) {
    throw new HarnessError(`Invalid regex ${JSON.stringify(pattern)}: ${e.message}`);
  }
}

/** Serializable view of any thrown value. */
export function errorInfo(e) {
  if (!e) return { error: 'unknown error' };
  const out = { error: e.message || String(e) };
  if (e.code) out.code = e.code;
  if (e.status) out.status = e.status;
  if (e.details) out.details = e.details;
  return out;
}
