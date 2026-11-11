// Imported first by the MCP server: stdout carries only MCP protocol frames, so anything a
// library prints with console.log/info/debug/warn is redirected to stderr.
const toStderr = (...args) => console.error(...args);
console.log = toStderr;
console.info = toStderr;
console.debug = toStderr;
console.warn = toStderr;
