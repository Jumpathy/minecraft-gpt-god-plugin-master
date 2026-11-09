const pool = require('../db/database');

async function apiRoutes(fastify) {
    fastify.get('/api/cycles', async (request, reply) => {
        const limit = parseInt(request.query.limit) || 100;
        const offset = parseInt(request.query.offset) || 0;

        try {
            const [rows] = await pool.execute(
                `SELECT request_id, type, api, model, timestamp, body
                 FROM gpt_debug_log
                 ORDER BY timestamp DESC
                 LIMIT ${limit + limit} OFFSET ${offset}`
            );

            // Group into paired request/response cycles
            const cyclesMap = {};
            for (const row of rows) {
                const id = row.request_id;
                if (!cyclesMap[id]) {
                    cyclesMap[id] = {
                        request_id: id,
                        api: row.api,
                        model: row.model,
                        timestamp: row.timestamp,
                    };
                }

                let body;
                try {
                    body = typeof row.body === 'string' ? JSON.parse(row.body) : row.body;
                } catch {
                    body = { raw: row.body };
                }

                if (row.type === 'request') {
                    cyclesMap[id].request = body;
                    cyclesMap[id].timestamp = row.timestamp;
                } else {
                    cyclesMap[id].response = body;
                    if (!cyclesMap[id].timestamp) cyclesMap[id].timestamp = row.timestamp;
                }
            }

            const cycles = Object.values(cyclesMap).sort(
                (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
            ).slice(0, limit);

            return { ok: true, cycles, count: cycles.length };
        } catch (err) {
            fastify.log.error(err);
            return reply.status(500).send({ ok: false, error: 'Database error' });
        }
    });

    fastify.get('/api/stats', async (request, reply) => {
        try {
            const [[{ total_cycles }]] = await pool.execute(
                `SELECT COUNT(DISTINCT request_id) as total_cycles FROM gpt_debug_log`
            );
            const [[{ total_rows }]] = await pool.execute(
                `SELECT COUNT(*) as total_rows FROM gpt_debug_log`
            );
            const [[{ oldest }]] = await pool.execute(
                `SELECT MIN(timestamp) as oldest FROM gpt_debug_log`
            );
            return { ok: true, total_cycles, total_rows, oldest };
        } catch (err) {
            fastify.log.error(err);
            return reply.status(500).send({ ok: false, error: 'Database error' });
        }
    });
}

module.exports = apiRoutes;
