Promise = require('bluebird')
debug = require('debug')('Browser')
request = require('request')

# A wrapper around request.
#
# Unlike Browser, you must call methods here within a promise chain.
module.exports = class ApiBrowser
  constructor: (@options) ->
    throw 'Must pass options.apiToken, an API token' if !@options.apiToken
    throw 'Must pass options.baseUrl, a URL like "http://localhost:9000/api/v1"' if !@options.baseUrl

    @_request = request.defaults
      baseUrl: @options.baseUrl
      auth:
        user: @options.apiToken
        password: 'x-auth-token'
        sendImmediately: true

  _r: (options) ->
    new Promise (resolve, reject) =>
      @_request options, (err, response, body) =>
        if err?
          reject(err)
        else
          if !options.json
            body = JSON.parse(body)
          resolve(response: response, body: body)

  # GETs the url and returns a Promise of { response, body }.
  GET: (url) -> @_r(method: 'GET', url: url)

  # POSTs the url with JSON data and returns a Promise of { response, body }.
  POST: (url, json) -> @_r(method: 'POST', url: url, json: json)

  # DELETEs the url and returns a Promise of { response, body }.
  DELETE: (url) -> @_r(method: 'DELETE', url: url)
