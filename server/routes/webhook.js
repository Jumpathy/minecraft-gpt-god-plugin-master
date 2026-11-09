const pool = require('../db/database');

async function webhookRoutes(fastify) {
    fastify.post('/webhook', async (request, reply) => {
        const { type, api, model, requestId, timestamp, body } = request.body || {};

        if (!type || !api || !model || !requestId || !timestamp || body === undefined) {
            return reply.status(400).send({ ok: false, error: 'Missing required fields' });
        }

        const bodyStr = typeof body === 'string' ? body : JSON.stringify(body);

        try {
            await pool.execute(
                `INSERT INTO gpt_debug_log (request_id, type, api, model, timestamp, body)
                 VALUES (?, ?, ?, ?, ?, ?)`,
                [requestId, type, api, model, new Date(timestamp), bodyStr]
            );
            return { ok: true };
        } catch (err) {
            fastify.log.error(err);
            return reply.status(500).send({ ok: false, error: 'Database error' });
        }
    });
}

module.exports = webhookRoutes;
