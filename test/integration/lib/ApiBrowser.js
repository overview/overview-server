'use strict'

const debug = require('debug')('ApiBrowser')
const request = require('request-promise-native')

// A wrapper around request
module.exports = class ApiBrowser {
  constructor(options) {
    if (!options.apiToken) throw new Error('Must pass options.apiToken, an API token')
    if (!options.baseUrl) throw new Error('Must pass options.baseUrl, a URL like "http://localhost:9000/api/v1"')

    this._request = request.defaults({
      baseUrl: options.baseUrl,
      auth: {
        user: options.apiToken,
        password: 'x-auth-token',
        sendImmediately: true,
      },
    })
  }

  async _r(options) {
    const body = await this._request(options)
    return options.json ? body : JSON.parse(body)
  }

  // GETs the url and returns a Promise of { body }.
  async GET(url) { return this._r({ method: 'GET', url: url }) }

  // POSTs the url with JSON data and returns a Promise of { body }.
  async POST(url, json) { return this._r({ method: 'POST', url: url, json: json }) }

  // DELETEs the url and returns a Promise of { body }.
  async DELETE(url) { return this._r({ method: 'DELETE', url: url }) }
}
