wd = require('wd')
Q = require('q')
escapeRegexp = require('escape-regexp')
webdriver = require('selenium-webdriver')
FileDetector = require('selenium-webdriver/remote').FileDetector

Browser = require('./Browser')
UserAdminSession = require('./UserAdminSession')

#webdriver.logging.installConsoleHandler()
#webdriver.logging.getLogger().setLevel(webdriver.logging.Level.ALL)

Constants =
  ajaxTimeout: 10000 # timeout waiting for AJAX requests
  asyncTimeout: 12000 # timeout waiting for JavaScript; might involve waiting for HTTP requests
  pageLoadTimeout: 30000 # Travis+SauceLabs is slow, especially for Vimeo
  redirectTimeout: 10000 # timeout waiting for a redirect
  pollLength: 200 # milliseconds between condition checks

options =
  desiredCapabilities:
    browserName: 'firefox'
    version: ''
    platform: 'ANY'
    build: process.env.BUILD_TAG
    handlesAlerts: true
  seleniumLocation:
    host: 'localhost'
    port: 4444
    url: 'http://localhost:4444/wd/hub'

if 'SAUCE_USER_NAME' of process.env
  x = options.seleniumLocation
  x.hostname = process.env.SELENIUM_HOST
  x.port = process.env.SELENIUM_PORT
  x.user = process.env.SAUCE_USER_NAME
  x.pwd = process.env.SAUCE_API_KEY

wd.addPromiseChainMethod 'acceptingNextAlert', ->
  @executeFunction ->
    window.acceptingNextAlert =
      alert: window.alert
      confirm: window.confirm

    after = ->
      for k, v of window.acceptingNextAlert
        window[k] = v
      delete window.acceptingNextAlert

    window.alert = -> after(); undefined
    window.confirm = -> after(); true

wd.addAsyncMethod 'dumpLog', ->
  cb = wd.findCallback(arguments)

  @log 'browser', (__, entries) ->
    for entry in entries
      console.log(entry.timestamp, entry.level, entry.message)
    cb()

# Call this, then do stuff, then call waitForJqueryAjaxComplete.
wd.addPromiseChainMethod 'listenForJqueryAjaxComplete', ->
  @executeFunction ->
    if 'listenForJqueryAjaxComplete' not of window
      $(document).ajaxComplete(-> window.listenForJqueryAjaxComplete.done = true)
    window.listenForJqueryAjaxComplete = { done: false }

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
wd.addPromiseChainMethod 'waitForJqueryAjaxComplete', ->
  @
    .waitForFunctionToReturnTrueInBrowser(
      (-> window.listenForJqueryAjaxComplete.done)
      Constants.ajaxTimeout,
      Constants.pollLength
    )

wd.addPromiseChainMethod 'waitForJqueryReady', ->
  @.waitForFunctionToReturnTrueInBrowser(-> $?.isReady)

wrapJsFunction = (js) -> "(#{js})()"

wd.addPromiseChainMethod 'executeFunction', (js) ->
  @execute(wrapJsFunction(js))

wd.addPromiseChainMethod 'waitForFunctionToReturnTrueInBrowser', (js, timeout, pollLength) ->
  start = new Date()

  timeout ?= Constants.ajaxTimeout
  pollLength ?= Constants.pollLength

  deferred = Q.defer()
  promise = deferred.promise
  wd.transferPromiseness(promise, @)

  jsToExecute = "return true == #{wrapJsFunction(js)};"

  poll = =>
    @execute(jsToExecute)
      .then (r) ->
        if r
          deferred.resolve(null)
        else if new Date() - start + pollLength > timeout
          deferred.reject(new Error("Waited, but never calculated JS to be true: #{jsToExecute}"))
        else
          setTimeout(poll, pollLength)

  poll()

  promise

ValidArgs =
  name: null
  value: null
  class: null
  className: null
  contains: null
  tag: null
  index: null
  visible: true # handled outside argsToXPath()

argsToXPath = (args) ->
  tag = args.tag ? '*'

  for k, __ of args
    if k not of ValidArgs
      throw "Invalid option #{k} in elementBy() selector"

  attrs = []
  if args.contains
    attrs.push("contains(., '#{args.contains.replace(/'/g, "\\'")}')")

  for className in [ args.className, args['class'] ]
    if className
      attrs.push("contains(concat(' ', @class, ' '), ' #{className} ')")

  for attr in [ 'name', 'value' ]
    if attr of args
      attrs.push("@#{attr}='#{args[attr]}'")

  if 'index' of args
    attrs.push("position()=#{args.index}")

  xpath = "//#{tag}"
  for attr in attrs
    xpath += "[#{attr}]"
  xpath

argsToAsserter = (args) ->
  if args.visible
    wd.asserters.isDisplayed
  else
    undefined

# Finds an element by lots of wonderful stuff.
#
# For instance:
#
#   .elementBy(tag: 'a', contains: 'Reset') # tag name a, text _contains_ 'Reset'
wd.addAsyncMethod 'elementBy', (args) ->
  xpath = argsToXPath(args)
  asserter = argsToAsserter(args)
  cb = wd.findCallback(arguments)

  @elementByXPath(xpath, asserter, cb)

wd.addAsyncMethod 'elementByOrNull', (args) ->
  xpath = argsToXPath(args)
  asserter = argsToAsserter(args)
  cb = wd.findCallback(arguments)

  @elementByXPathOrNull(xpath, asserter, cb)

# Waits for an element by lots of wonderful stuff.
#
# For instance:
#
#   .waitForElementBy(tag: 'a', contains: 'Reset')
wd.addAsyncMethod 'waitForElementBy', (args) ->
  xpath = argsToXPath(args)
  asserter = argsToAsserter(args)

  newArgs = [ 'xpath', xpath ]
  if asserter?
    newArgs.push(asserter)

  newArgs = newArgs.concat(Array.prototype.slice.call(arguments, 1))

  @waitForElement.apply(@, newArgs) # finds callback itself

wd.addPromiseChainMethod 'waitForUrl', (expectUrl, args...) ->
  regex = if expectUrl instanceof RegExp
    expectUrl
  else
    new RegExp("^https?:\\/\\/[^\\/]+#{escapeRegexp(expectUrl)}$")

  asserter = new wd.asserters.Asserter (browser) ->
    browser
      .url().then (currentUrl) ->
        if regex.test(currentUrl)
          Q(currentUrl)
        else
          err = new Error("Expected URL to be #{expectUrl} but it is #{currentUrl}")
          err.retriable = true
          Q.reject(err)

  @waitFor(asserter, args...)

module.exports =
  baseUrl: 'http://localhost:9000'
  adminLogin:
    email: 'admin@overviewdocs.com'
    password: 'admin@overviewdocs.com'

  createUserAdminSession: (title) ->
    new UserAdminSession
      baseUrl: module.exports.baseUrl
      timeout: Constants.pageLoadTimeout
      login: module.exports.adminLogin

  # Returns a promise of a browser.
  #
  # DEPRECATED: we've moved to createBrowser(), because external libraries are
  # too complex.
  create: (title) ->
    desiredCapabilities = { name: title }
    desiredCapabilities[k] = v for k, v of options.desiredCapabilities

    wd.promiseChainRemote(options.seleniumLocation)
      .init(desiredCapabilities)
      .configureHttp
        baseUrl: module.exports.baseUrl
        timeout: Constants.pageLoadTimeout
        retries: -1
      .setWindowSize(1024, 768)
      .setImplicitWaitTimeout(0)   # we only wait explicitly! We don't want to code race conditions
      .setAsyncScriptTimeout(Constants.asyncTimeout) # in case there are HTTP requests
      .setPageLoadTimeout(Constants.pageLoadTimeout) # in case, on a slow computer, something slow happens

  # Returns a Browser.
  createBrowser: ->
    driver = new webdriver.Builder()
      .usingServer(options.seleniumLocation.url)
      .forBrowser('firefox')
      .withCapabilities(options.desiredCapabilities)
      .setLoggingPrefs(driver: 'ALL', server: 'ALL', browser: 'ALL')
      .build()

    driver.setFileDetector(new FileDetector())

    browser = new Browser(driver, baseUrl: module.exports.baseUrl)
    browser.loadShortcuts('jquery')
    browser.loadShortcuts('documentSets')

    browser
