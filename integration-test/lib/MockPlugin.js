'use strict'

const debug = require('debug')('MockPlugin')
const fs = require('fs')
const http = require('http')
const os = require('os')
const url = require('url')

module.exports = class MockPlugin {
  constructor(name) {
    this.hostname = os.hostname()

    const buf = fs.readFileSync(`${__dirname}/../mock-plugins/${name}.html`)

    this.server = http.createServer((req, res) => {
      debug([ req.method, req.url ].join(' '))

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
