Promise = require('bluebird')
request = require('request')
debug = require('debug')
ms = require('ms')

uniqueNumberCounter = 0

# A request API
module.exports = class Client
  constructor: (options) ->
    throw 'Must pass baseUrl, a String like "https://www.overviewdocs.com"' if !options.baseUrl?
    throw 'Must pass title, a name to identify this client' if !options.title
    @debug = debug(options.title)
    @title = options.title
    @baseUrl = options.baseUrl
    @_lastPromise = Promise.resolve(options.startAfterPromise)
    @jar = request.jar()

  _queue: (fn) ->
    @_lastPromise = @_lastPromise.then(fn)

  GET: (url, options={}) -> @_queue => new Promise (resolve, reject) =>
    absoluteUrl = @baseUrl + url
    uniqueNumber = uniqueNumberCounter += 1
    @debug("GET #{absoluteUrl} [#{uniqueNumber}]...")
    startDate = new Date()
    request.get { url: absoluteUrl, jar: @jar }, (err, response, body) =>
      @debug("[#{uniqueNumber}] status #{response.statusCode} in #{ms(new Date() - startDate)}")
      return reject(err) if err?
      return reject(new Error("Wrong status code from server: #{response.statusCode} for GET #{url}")) if options?.checkStatus != false && !(200 <= response.statusCode < 400)
      if (m = /window.csrfToken\s*=\s*"([-a-z0-9]+)";/.exec(body))?
        @csrfToken = m[1]

      resolve(response)

  POST: (url, data, options={}) -> @_queue => new Promise (resolve, reject) =>
    absoluteUrl = @baseUrl + url
    uniqueNumber = uniqueNumberCounter += 1
    csrfData = { csrfToken: @csrfToken }
    csrfData[k] = v for k, v of data
    @debug("POST #{absoluteUrl} [#{uniqueNumber}] - #{JSON.stringify(csrfData)}")
    startDate = new Date()
    request.post { url: absoluteUrl, form: csrfData, jar: @jar }, (err, response, body) =>
      @debug("[#{uniqueNumber}] status #{response.statusCode} in #{ms(new Date() - startDate)}")
      return reject(err) if err?
      return reject(new Error("Wrong status code from server: #{response.statusCode} for POST #{url}")) if options?.checkStatus != false && !(200 <= response.statusCode < 400)
      resolve(response)

  PUT: (url, data, options={}) -> @_queue => new Promise (resolve, reject) =>
    absoluteUrl = @baseUrl + url
    uniqueNumber = uniqueNumberCounter += 1
    @debug("PUT #{absoluteUrl} [#{uniqueNumber}] - #{JSON.stringify(data)}")
    startDate = new Date()
    request.put { url: absoluteUrl, form: data, jar: @jar }, (err, response, body) =>
      @debug("[#{uniqueNumber}] status #{response.statusCode} in #{ms(new Date() - startDate)}")
      return reject(err) if err?
      return reject(new Error("Wrong status code from server: #{response.statusCode} for PUT #{url}")) if options?.checkStatus != false && !(200 <= response.statusCode < 400)
      resolve(response)
