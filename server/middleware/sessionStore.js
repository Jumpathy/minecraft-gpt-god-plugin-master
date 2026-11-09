const mysql = require("mysql2");

function createSessionStore(fastifySession) {
    const MySQLStore = require('../modules/session')(fastifySession);
    
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

    const isClient = ip !== process.env.server_ip;

    return new MySQLStore({
        pool: mysql.createPool({
            user: process.env.database_username,
            password: process.env.database_password,
            database: process.env.database_name,
            host: isClient ? process.env.server_ip : "localhost",
            port: process.env.database_port,
        })
    });
}

module.exports = createSessionStore;