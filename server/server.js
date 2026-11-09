// Author: @Jumpathy
// Name: server.js
// Description: Manages web-server

require('dotenv').config();

const fastify = require("fastify")({ logger: false, bodyLimit: 50 * 1024 * 1024 });
const fastifySession = require('@fastify/session');
const fastifyCookie = require('@fastify/cookie');
const multipart = require('@fastify/multipart');
const cors = require("@fastify/cors");
const path = require("path");
const fs = require("fs");

const createSessionStore = require('./middleware/sessionStore');
const webhookRoutes = require('./routes/webhook');
const apiRoutes = require('./routes/api');

async function init() {    
    await fastify.register(cors, {});

    fastify.addContentTypeParser("text/plain", function (request, payload, done) {
        let body = "";
        payload.on("data", chunk => { body += chunk; });
        payload.on("end", () => {
            done(null, body);
        });
    });

    await fastify.register(multipart, {
        limits: {
            fileSize: 50 * (1024 * 1024)
        }
    });

    const uploadsDir = path.join(__dirname, 'uploads');
    if (!fs.existsSync(uploadsDir)) {
        fs.mkdirSync(uploadsDir);
    }

    await fastify.register(fastifyCookie);

    await fastify.register(fastifySession, {
        secret: process.env.session_secret,
        cookie: {
            secure: "auto",
            maxAge: (1000 * (86400 * 365 * 10)) // 10yrs
        },
        store: createSessionStore(fastifySession),
        saveUninitialized: false,
    });

    const { networkInterfaces } = require('os');
    const nets = networkInterfaces();
    let ip = "";

    for (const name of Object.keys(nets)) {
        for (const net of nets[name]) {
            const familyV4Value = typeof net.family === 'string' ? 'IPv4' : 4;
            if (net.family === familyV4Value && !net.internal) {
                ip = net.address;
                break;
            }
        }
    }

    const isClient = ip !== "82.180.163.23";
    const server = "0.0.0.0";
    const client = "127.0.0.1";

    fastify.setErrorHandler((error, request, reply) => {
        console.log(error);
        reply.status(500).send({ ok: false });
    });

    await fastify.register(webhookRoutes);
    await fastify.register(apiRoutes);

    fastify.get('/', (request, reply) => {
        reply.type('text/html').send(
            fs.readFileSync(path.join(__dirname, 'pages/index.html'), 'utf8')
        );
    });

    fastify.listen({ port: process.env.port, host: server }, (err, address) => {
        if (err) {
            console.log(err);
            process.exit(1);
        }
        console.log(`Server is now listening on ${address}`);
    });
}

init()