import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import tls from 'node:tls';
import { pipeline } from 'node:stream/promises';

// Uses the configured proxy, including for HTTPS redirects.
async function request(url) {
  const target = new URL(url);
  const proxyUrl = process.env.HTTPS_PROXY || process.env.HTTP_PROXY;
  if (!proxyUrl) return new Promise((resolve, reject) => https.get(target, resolve).on('error', reject));
  const proxy = new URL(proxyUrl);
  const socket = await new Promise((resolve, reject) => {
    const req = http.request({ hostname: proxy.hostname, port: proxy.port, method: 'CONNECT', path: `${target.hostname}:443` });
    req.on('connect', (res, connection) => res.statusCode === 200 ? resolve(connection) : reject(new Error(`Proxy: ${res.statusCode}`)));
    req.on('error', reject);
    req.setTimeout(30000, () => req.destroy(new Error('Proxy timeout')));
    req.end();
  });
  const secure = tls.connect({ socket, servername: target.hostname });
  return new Promise((resolve, reject) => {
    const agent = new https.Agent({ keepAlive: false });
    agent.createConnection = () => secure;
    const req = https.get(target, { agent }, resolve);
    req.on('error', reject);
    req.setTimeout(60000, () => req.destroy(new Error('Download timeout')));
  });
}

let url = process.argv[2];
const destination = process.argv[3];
for (let count = 0; count < 8; count++) {
  const response = await request(url);
  if ([301, 302, 303, 307, 308].includes(response.statusCode)) {
    url = new URL(response.headers.location, url).href;
    response.resume();
    continue;
  }
  if (response.statusCode !== 200) throw new Error(`HTTP ${response.statusCode}: ${url}`);
  if (destination) {
    await pipeline(response, fs.createWriteStream(destination));
    console.log(`Downloaded ${destination} (${fs.statSync(destination).size} bytes)`);
  } else {
    console.log(`HTTP ${response.statusCode}: ${url}`);
    response.destroy();
  }
  process.exit(0);
}
throw new Error('Too many redirects');
