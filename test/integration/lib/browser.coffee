wd = require('wd')

options =
  desiredCapabilities:
    browserName: 'phantomjs'

wd.addAsyncMethod 'acceptingNextAlert', ->
  cb = wd.findCallback(arguments)

  js = ->
    window.acceptingNextAlert =
      alert: window.alert
      confirm: window.confirm

    after = ->
      for k, v of window.acceptingNextAlert
        window[k] = v
      delete window.acceptingNextAlert

    window.alert = -> after(); undefined
    window.confirm = -> after(); true

  @execute(js, cb)

wd.addAsyncMethod 'dumpLog', ->
  cb = wd.findCallback(arguments)

  @log 'browser', (__, entries) ->
    for entry in entries
      console.log(entry.timestamp, entry.level, entry.message)
    cb()

module.exports =
  baseUrl: 'http://localhost:9000'
  adminLogin:
    email: 'admin@overview-project.org'
    password: 'admin@overview-project.org'

  # Returns a promise of a browser.
  create: ->
    wd
      .promiseChainRemote()
      .init(options.desiredCapabilities)
      .configureHttp
        baseUrl: module.exports.baseUrl
        timeout: 5000
        retries: 1
        retryDelay: 10
