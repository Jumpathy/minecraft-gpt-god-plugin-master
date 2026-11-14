#!/usr/bin/env node
// MCP stdio entry point "god-harness" (registered in the repo-root .mcp.json).
//
// Default: a thin forwarder to the harness daemon (daemon.js, http://127.0.0.1:GOD_HARNESS_PORT/mcp), which it starts
// detached when it is not running. The daemon owns the server, bots, real clients, spend guard and session record,
// so they survive this process, several Claude Code sessions share them, and tools added to the daemon appear
// without restarting Claude Code (the tool list is read from the daemon on every tools/list).
//
// --inprocess (or GOD_HARNESS_INPROCESS=1): run every tool inside this process instead, as before the daemon existed
// (used by scripts/selftest.js; a server it starts is stopped when this process exits).
// stdout is reserved for MCP frames; all logging goes to stderr.

import './stdio-guard.js';
import './env.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';

const log = (...a) => console.error('[god-harness]', ...a);
const inprocess = process.argv.includes('--inprocess') || process.env.GOD_HARNESS_INPROCESS === '1';

async function runInProcess() {
  const { McpServer } = await import('@modelcontextprotocol/sdk/server/mcp.js');
  const { INSTRUCTIONS, registry, runTool, shutdown } = await import('./tools.js');
  process.on('uncaughtException', (e) => log('uncaught exception (continuing):', e));
  process.on('unhandledRejection', (e) => log('unhandled rejection (continuing):', e));
  const server = new McpServer({ name: 'god-harness', version: '0.3.0' }, { instructions: INSTRUCTIONS });
  for (const t of registry) server.registerTool(t.name, { description: t.description, inputSchema: t.shape }, (args, extra) => runTool(t.name, args, extra));
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.stdin.on('end', () => shutdown('stdin closed'));
  const transport = new StdioServerTransport();
  transport.onclose = () => shutdown('transport closed');
  await server.connect(transport);
  log(`ready on stdio (in-process, ${registry.length} tools)`);
}

async function runForwarder() {
  const { Server } = await import('@modelcontextprotocol/sdk/server/index.js');
  const { Client } = await import('@modelcontextprotocol/sdk/client/index.js');
  const { StreamableHTTPClientTransport } = await import('@modelcontextprotocol/sdk/client/streamableHttp.js');
  const { CallToolRequestSchema, ListToolsRequestSchema } = await import('@modelcontextprotocol/sdk/types.js');
  const { ensureDaemon } = await import('../call.js');
  const port = Number(process.env.GOD_HARNESS_PORT || 8790);

  /** One daemon round trip on a fresh connection (the daemon is stateless), started again if it went away. */
  async function withDaemon(fn) {
    await ensureDaemon();
    const client = new Client({ name: 'god-harness-stdio', version: '0.3.0' });
    await client.connect(new StreamableHTTPClientTransport(new URL(`http://127.0.0.1:${port}/mcp`)));
    try {
      return await fn(client);
    } finally {
      await client.close().catch(() => {});
    }
  }

  const instructions = await withDaemon(async (c) => c.getInstructions());
  const server = new Server({ name: 'god-harness', version: '0.3.0' }, { capabilities: { tools: {} }, instructions });
  server.setRequestHandler(ListToolsRequestSchema, () => withDaemon((c) => c.listTools()));
  server.setRequestHandler(CallToolRequestSchema, (req) =>
    withDaemon((c) => c.callTool(req.params, undefined, { timeout: 20 * 60_000 })).catch((e) => ({
      content: [{ type: 'text', text: JSON.stringify({ ok: false, error: `harness daemon: ${e.message}` }) }],
      isError: true,
    })),
  );
  process.stdin.on('end', () => process.exit(0)); // the daemon lives on
  await server.connect(new StdioServerTransport());
  log(`ready on stdio (forwarding to the daemon on port ${port})`);
}

if (inprocess) await runInProcess();
else {
  await runForwarder().catch(async (e) => {
    log(`the daemon is unavailable (${e.message}); running the tools in-process`);
    await runInProcess();
  });
}
