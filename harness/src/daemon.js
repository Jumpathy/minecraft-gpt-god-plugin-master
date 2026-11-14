#!/usr/bin/env node
// The long-running harness process. It owns the sandbox server (its child), the bots, the real clients, the spend
// guard and the session record, and serves every tool three ways on 127.0.0.1:
//   POST /mcp            MCP over streamable HTTP (stateless); harness/src/mcp-server.js forwards stdio MCP here
//   POST /api/<tool>     plain JSON in, the tool's result out (images saved to disk, their paths returned)
//   GET  /api/tools      names, descriptions and JSON schemas;  GET /health
// Because the state lives here, tools can be added and the daemon restarted without restarting Claude Code, and
// several sessions share one set of bots and one server. Start: node harness/src/daemon.js [--port 8790]
// (GOD_HARNESS_PORT; GOD_SANDBOX_DIR picks the sandbox). Stops the server it started when it exits.

import './env.js';
import http from 'node:http';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';
import { INSTRUCTIONS, log, managers, registry, runTool, shutdown } from './tools.js';

const argPort = process.argv.indexOf('--port');
export const PORT = Number(argPort > 0 ? process.argv[argPort + 1] : process.env.GOD_HARNESS_PORT || 8790);

process.on('uncaughtException', (e) => log('uncaught exception (continuing):', e));
process.on('unhandledRejection', (e) => log('unhandled rejection (continuing):', e));

function mcpServer() {
  const server = new McpServer({ name: 'god-harness', version: '0.3.0' }, { instructions: INSTRUCTIONS });
  for (const t of registry) {
    server.registerTool(t.name, { description: t.description, inputSchema: t.shape }, (args, extra) => runTool(t.name, args, extra));
  }
  return server;
}

const send = (res, status, body) => {
  const text = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) });
  res.end(text);
};

async function readBody(req) {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const text = Buffer.concat(chunks).toString('utf8').trim();
  return text ? JSON.parse(text) : {};
}

const httpServer = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  try {
    if (req.socket.remoteAddress && !/^(::1|::ffff:127\.|127\.)/.test(req.socket.remoteAddress)) return send(res, 403, { ok: false, error: 'local only' });
    if (req.headers.origin) return send(res, 403, { ok: false, error: 'browser requests are refused' }); // not for web pages
    if (url.pathname === '/health') return send(res, 200, { ok: true, pid: process.pid, tools: registry.length, sandbox: managers.sandbox.dir, uptimeSec: Math.round(process.uptime()) });
    if (url.pathname === '/api/tools') {
      return send(res, 200, { ok: true, tools: registry.map((t) => ({ name: t.name, description: t.description, inputSchema: z.toJSONSchema(z.object(t.shape)) })) });
    }
    if (url.pathname.startsWith('/api/') && req.method === 'POST') {
      const name = url.pathname.slice(5);
      if (name === 'shutdown') {
        send(res, 200, { ok: true });
        return void shutdown('api shutdown');
      }
      const out = await runTool(name, await readBody(req));
      // images: keep the file path the tool reported, drop the base64
      const texts = out.content.filter((c) => c.type === 'text').map((c) => c.text);
      let result;
      try {
        result = texts.length === 1 ? JSON.parse(texts[0]) : texts;
      } catch {
        result = texts.join('\n');
      }
      return send(res, 200, { isError: !!out.isError, images: out.content.filter((c) => c.type === 'image').length, result });
    }
    if (url.pathname === '/mcp') {
      if (req.method !== 'POST') return send(res, 405, { jsonrpc: '2.0', error: { code: -32000, message: 'Method not allowed' }, id: null });
      const body = await readBody(req);
      const server = mcpServer();
      const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
      res.on('close', () => {
        transport.close().catch(() => {});
        server.close().catch(() => {});
      });
      await server.connect(transport);
      return void (await transport.handleRequest(req, res, body));
    }
    send(res, 404, { ok: false, error: `unknown route ${req.method} ${url.pathname}` });
  } catch (e) {
    log('request failed:', e);
    if (!res.headersSent) send(res, 500, { ok: false, error: String(e?.message ?? e) });
  }
});

httpServer.requestTimeout = 0; // tools may wait for minutes
httpServer.headersTimeout = 0;
httpServer.keepAliveTimeout = 5000;
httpServer.on('error', (e) => {
  log(`cannot listen on 127.0.0.1:${PORT}: ${e.message}`);
  process.exit(1);
});
httpServer.listen(PORT, '127.0.0.1', () => log(`daemon ready on http://127.0.0.1:${PORT} (${registry.length} tools, sandbox ${managers.sandbox.dir})`));

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
