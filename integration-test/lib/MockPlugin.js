'use strict'

const fs = require('fs')
const http = require('http')
const path = require('path')
const url = require('url')

// To work well with `./standalone`, we don't require any external
// libraries.
function debug(...args) {
  if (process.env.DEBUG === '*') {
    console.log(...args)
  }
}

module.exports = class MockPlugin {
  constructor(name) {
    this.hostname = process.env.HOSTNAME // we're in Docker

    const htmlFilename = path.join(path.dirname(__filename), '../mock-plugins', name + '.html')

    // Read file, to throw an error if it does not exist
    fs.readFileSync(htmlFilename)

    this.server = http.createServer((req, res) => {
      debug([ req.method, req.url ].join(' '))

      // Re-read the file, in case it changed. This is useful when running `./standalone`
      const buf = fs.readFileSync(htmlFilename)

      this.lastRequestUrl = url.parse(req.url, true)
      const pathname = url.parse(req.url).pathname

      switch (pathname) {
        case '/metadata':
          res.writeHead(200, {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Cache-Control': 'no-cache',
          })
          res.end('{}')
          break
        case '/show':
          res.writeHead(200, {
            'Content-Type': 'text/html; charset=utf-8',
            'Cache-Control': 'no-cache',
          })
          res.end(buf)
          break
        case '/filter/01010101':
          res.writeHead(200, {
            'Content-Type': 'application/octet-stream',
            'Cache-Control': 'no-cache',
          })
          res.end(Buffer.from([ 0b01010101 ]))
        default:
          res.writeHead(404, {
            'Content-Type': 'text/plain; charset=utf-8',
            'Cache-Control': 'no-cache',
          })
          res.end('Mock plugins only respond to /metadata and /show')
      }
    })

    this.server.unref()

    // Track connections. Browsers will usually keep connections open a while,
    // but _we_ want to shut down this server quickly. So we'll need to destroy
    // all active connections later.
    // https://stackoverflow.com/questions/14626636/how-do-i-shutdown-a-node-js-https-server-immediately/14636625#14636625
    this.shuttingDown = false
    this.sockets = []
    this.server.on('connection', (socket) => {
      this.sockets.push(socket)
      socket.on('close', () => {
        if (this.shuttingDown) return
        this.sockets.splice(this.sockets.indexOf(socket), 1)
      })
    })
  }

  listen() {
    return new Promise((resolve, reject) => {
      // 0.0.0.0 inside a Docker container, that is
      this.server.listen(3333, '0.0.0.0', (err, ...args) => {
        if (err) return reject(err)
        resolve(...args)
      })
    })
      .then((...args) => {
        debug('Listening on http://0.0.0.0:3333')
        return args
      })
  }

  close() {
    debug('Closing server on http://0.0.0.0:3333')
    return new Promise((resolve, reject) => {
      this.server.close((err, ...args) => {
        if (err) return reject(err)
        debug('Stopped listening on http://0.0.0.0:3333')
        resolve(...args)
      })

      for (const socket of this.sockets) {
        socket.destroy()
      }
    })
  }
}
