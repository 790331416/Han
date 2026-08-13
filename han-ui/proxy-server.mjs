/**
 * 本地代理服务器 — 解决 Node.js 24 Vite http-proxy ECONNREFUSED 问题
 * 用法: PROXY_TARGET=http://<host>:<port> node proxy-server.mjs
 *
 * 只监听 127.0.0.1，仅供本地联调使用。
 */
import http from 'node:http'

const rawTarget = process.env.PROXY_TARGET
if (!rawTarget) {
  console.error('缺少 PROXY_TARGET 环境变量，例如：PROXY_TARGET=http://127.0.0.1:9090 node proxy-server.mjs')
  process.exit(1)
}

let target
try {
  target = new URL(rawTarget)
} catch {
  console.error(`PROXY_TARGET 不是合法 URL: ${rawTarget}`)
  process.exit(1)
}

const TARGET_HOST = target.hostname
const TARGET_PORT = Number(target.port || 80)
const LOCAL_PORT = Number(process.env.PROXY_PORT || 9090)

/** 只回显本机来源，不再对所有响应无条件放开 Access-Control-Allow-Origin: *。 */
const isLocalOrigin = (origin) =>
  typeof origin === 'string' && /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin)

const buildCorsHeaders = (req) => {
  const origin = req.headers.origin
  if (!isLocalOrigin(origin)) {
    return {}
  }
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,PATCH,OPTIONS',
    'Access-Control-Allow-Headers': req.headers['access-control-request-headers'] || 'Content-Type,Authorization',
    'Access-Control-Expose-Headers': 'Content-Disposition'
  }
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      ...buildCorsHeaders(req),
      'Access-Control-Max-Age': '86400'
    })
    res.end()
    return
  }

  const options = {
    hostname: TARGET_HOST,
    port: TARGET_PORT,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `${TARGET_HOST}:${TARGET_PORT}` },
    family: 4
  }

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, {
      ...proxyRes.headers,
      ...buildCorsHeaders(req)
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

server.listen(LOCAL_PORT, '127.0.0.1', () => {
  console.log(`Proxy: 127.0.0.1:${LOCAL_PORT} -> ${TARGET_HOST}:${TARGET_PORT}`)
})
