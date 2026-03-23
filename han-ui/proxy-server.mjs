/**
 * 本地代理服务器 — 解决 Node.js 24 Vite http-proxy ECONNREFUSED 问题
 * 用法: node proxy-server.mjs
 * 将 localhost:9090 转发到 10.18.35.95:9090
 */
import http from 'node:http'

const TARGET_HOST = '10.18.35.95'
const TARGET_PORT = 9090
const LOCAL_PORT = 9090

const server = http.createServer((req, res) => {
  const options = {
    hostname: TARGET_HOST,
    port: TARGET_PORT,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `${TARGET_HOST}:${TARGET_PORT}` },
    family: 4
  }

  const proxyReq = http.request(options, (proxyRes) => {
    // Add CORS headers
    res.writeHead(proxyRes.statusCode, {
      ...proxyRes.headers,
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Expose-Headers': '*'
    })
    proxyRes.pipe(res)
  })

  proxyReq.on('error', (err) => {
    console.error('Proxy error:', err.message)
    res.writeHead(502)
    res.end('Bad Gateway')
  })

  req.pipe(proxyReq)
})

// Handle CORS preflight
server.on('request', (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(200, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Max-Age': '86400'
    })
    res.end()
  }
})

server.listen(LOCAL_PORT, '127.0.0.1', () => {
  console.log(`Proxy: localhost:${LOCAL_PORT} → ${TARGET_HOST}:${TARGET_PORT}`)
})
