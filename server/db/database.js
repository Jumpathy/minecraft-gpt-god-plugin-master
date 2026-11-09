require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const mysql = require('mysql2/promise');
const { networkInterfaces } = require('os');

const nets = networkInterfaces();
let ip = '';
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

const pool = mysql.createPool({
    host: isClient ? process.env.server_ip : 'localhost',
    port: parseInt(process.env.database_port),
    user: process.env.database_username,
    password: process.env.database_password,
    database: process.env.database_name,
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
});

module.exports = pool;
