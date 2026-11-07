#!/usr/bin/env node
/**
 * Zero-dependency WebSocket-to-TCP Bridge Proxy for the Team 3 Web Simulator.
 *
 * Bridges Chrome's WebSocket connection (ws://0.0.0.0:7466) to the Java Test
 * Harness BridgeServer (TCP 127.0.0.1:7465, newline-delimited JSON).
 *
 * Usage:
 *   1. Start the Java bridge in one terminal:
 *        cd th_src && .\gradlew.bat unityBridge
 *   2. Start this WebSocket proxy in another terminal:
 *        node web/bridge-proxy.mjs
 *   3. In Chrome (or on a Chromebook), switch MODE to "Java Bridge (ws://localhost:7466)".
 */

import http from 'node:http';
import net from 'node:net';
import crypto from 'node:crypto';

const WS_PORT = Number(process.env.WS_PORT || 7466);
const TCP_HOST = process.env.TCP_HOST || '127.0.0.1';
const TCP_PORT = Number(process.env.TCP_PORT || 7465);

function encodeWsFrame(text) {
  const payload = Buffer.from(text, 'utf8');
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.alloc(2);
    header[0] = 0x81; // FIN + text frame
    header[1] = len;
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

function decodeWsFrames(buffer, onMessage) {
  let offset = 0;
  while (offset + 2 <= buffer.length) {
    const byte0 = buffer[offset];
    const byte1 = buffer[offset + 1];
    const opcode = byte0 & 0x0f;
    const masked = (byte1 & 0x80) !== 0;
    let payloadLen = byte1 & 0x7f;
    let headerLen = 2;

    if (payloadLen === 126) {
      if (offset + 4 > buffer.length) break;
      payloadLen = buffer.readUInt16BE(offset + 2);
      headerLen = 4;
    } else if (payloadLen === 127) {
      if (offset + 10 > buffer.length) break;
      payloadLen = Number(buffer.readBigUInt64BE(offset + 2));
      headerLen = 10;
    }

    const maskLen = masked ? 4 : 0;
    const frameLen = headerLen + maskLen + payloadLen;
    if (offset + frameLen > buffer.length) break;

    if (opcode === 0x08) {
      // Close frame
      return { remaining: Buffer.alloc(0), closed: true };
    }

    if (opcode === 0x01) {
      const payload = Buffer.from(
        buffer.subarray(offset + headerLen + maskLen, offset + frameLen)
      );
      if (masked) {
        const mask = buffer.subarray(offset + headerLen, offset + headerLen + 4);
        for (let i = 0; i < payload.length; i++) {
          payload[i] ^= mask[i & 3];
        }
      }
      onMessage(payload.toString('utf8'));
    }

    offset += frameLen;
  }

  return { remaining: buffer.subarray(offset), closed: false };
}

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Team 3 WebSocket-to-TCP Bridge Proxy running.\n');
});

server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  if (!key) {
    socket.destroy();
    return;
  }

  const accept = crypto
    .createHash('sha1')
    .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
    .digest('base64');

  socket.write(
    [
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${accept}`,
      '\r\n',
    ].join('\r\n')
  );

  console.log('[BridgeProxy] Browser connected via WebSocket. Connecting to Java TCP bridge...');

  const tcp = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => {
    console.log(`[BridgeProxy] Connected to Java BridgeServer at ${TCP_HOST}:${TCP_PORT}`);
    // Kick off first step
    tcp.write(JSON.stringify({ type: 'input', gamepad1: {} }) + '\n');
  });

  let tcpBuffer = '';
  tcp.on('data', (chunk) => {
    tcpBuffer += chunk.toString('utf8');
    let newlineIdx;
    while ((newlineIdx = tcpBuffer.indexOf('\n')) !== -1) {
      const line = tcpBuffer.slice(0, newlineIdx).trim();
      tcpBuffer = tcpBuffer.slice(newlineIdx + 1);
      if (line) {
        socket.write(encodeWsFrame(line));
      }
    }
  });

  tcp.on('error', (err) => {
    console.error(`[BridgeProxy] TCP error (${TCP_HOST}:${TCP_PORT}): ${err.message}`);
    socket.end();
  });

  tcp.on('close', () => {
    socket.end();
  });

  let wsBuffer = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    wsBuffer = Buffer.concat([wsBuffer, chunk]);
    const res = decodeWsFrames(wsBuffer, (msg) => {
      if (!tcp.destroyed) {
        tcp.write(msg.trim() + '\n');
      }
    });
    wsBuffer = res.remaining;
    if (res.closed) {
      tcp.end();
      socket.end();
    }
  });

  socket.on('close', () => {
    tcp.end();
  });

  socket.on('error', () => {
    tcp.end();
  });
});

server.listen(WS_PORT, '0.0.0.0', () => {
  console.log(
    `[BridgeProxy] Listening on ws://localhost:${WS_PORT} -> forwarding to TCP ${TCP_HOST}:${TCP_PORT}`
  );
});
