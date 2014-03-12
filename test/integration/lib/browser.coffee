wd = require('wd')

Constants =
  ajaxTimeout: 2000 # timeout waiting for AJAX requests
  pollLength: 50 # milliseconds between condition checks

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

# Call this, then do stuff, then call waitForJqueryAjaxComplete.
wd.addAsyncMethod 'listenForJqueryAjaxComplete', ->
  cb = wd.findCallback(arguments)

  js = ->
    if 'listenForJqueryAjaxComplete' not of window
      window.listenForJqueryAjaxComplete =
        current: 0 # number of times we've listened
        total: 0   # number of ajax requests that completed since we first
                   # called listenForAjaxComplete
      $(document).ajaxComplete ->
        window.listenForJqueryAjaxComplete.total += 1
    else
      # Skip all unhandled ajax-complete events
      x = window.listenForJqueryAjaxComplete
      x.current = x.total

  @execute(js, cb)

# Finishes when an $.ajaxComplete method is fired.
#
# Before calling this, you must call listenForJqueryAjaxComplete(). Starting at
# that exact moment, waitForJqueryAjaxComplete() will finish once for every
# jQuery AJAX request that completes.
#
# Note the danger of a race. If a request was pending before you called
# listenForJqueryAjaxComplete(), this method will finish once that pending ajax
# method completes. To avoid races, call listenForJqueryAjaxComplete() when
# there are no pending AJAX requests.
wd.addAsyncMethod 'waitForJqueryAjaxComplete', ->
  cb = wd.findCallback(arguments)

  @waitForConditionInBrowser 'window.listenForJqueryAjaxComplete.current < window.listenForJqueryAjaxComplete.total', Constants.ajaxTimeout, Constants.pollLength, =>
    @execute((-> window.listenForJqueryAjaxComplete.current += 1), cb)

# Finds an element by lots of wonderful stuff.
#
# For instance:
#
#   .elementBy(tag: 'a', contains: 'Reset') # tag name a, text _contains_ 'Reset'
wd.addAsyncMethod 'elementBy', (options) ->
  cb = wd.findCallback(arguments)

  tag = options.tag ? '*'

  attrs = []
  if options.contains
    attrs.push("contains(., '#{options.contains.replace(/'/g, "\\'")}')")

  xpath = "//#{tag}"
  if attrs.length
    xpath += "[#{attrs.join(',')}]"

  @elementByXPath(xpath, cb)

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
        timeout: 15000
        retries: 1
        retryDelay: 10
      .setImplicitWaitTimeout(0)   # we only wait explicitly! We don't want to code race conditions
      .setAsyncScriptTimeout(5000) # in case there are HTTP requests
      .setPageLoadTimeout(15000)   # in case, on a slow computer, something slow happens
