debug = require('debug')('Browser')
webdriver = require('selenium-webdriver')
Until = webdriver.until

Element = require('./Element')
Locator = require('./Locator')
TIMEOUTS = require('./TIMEOUTS')

class LocateError extends Error
  constructor: (@message) -> super # https://github.com/jashkenas/coffeescript/issues/2359

class WaitingLocateError extends Error
  constructor: (@message) -> super # https://github.com/jashkenas/coffeescript/issues/2359

# A wrapper around Selenium, tuned for brevity.
#
# The key concept here is that all these methods are *quasi*-synchronous. The
# underlying browser is in a separate thread, so all methods are *actually*
# asynchronous. But async testing code is a pain to write. Browser (actually,
# the Selenium WebDriver code) keeps a queue of actions to perform, as a
# Promise chain. Most methods here return one of two things:
#
# * a Promise of a value, for testing
# * this Browser, for chaining
#
# This Browser also *mimics* a Promise, with a special `then` method. that
# always tacks its arguments to the end of the promise chain. That lets you
# return this Browser to mocha at the end of a unit test.
module.exports = class Browser
  constructor: (@driver, @options) ->
    # Debugs "synchronously": that is, schedules a debug message to be printed
    # just before the next command runs.
    @debug = (args...) -> @driver.call(-> debug(args...))

    debug("scheduling Browser construct")
    # As of 2015-05-27, ~99% of our users are >= 1024x768
    @driver.manage().window().setSize(1024, 768)

    @debug("Browser constructed")
    throw 'Must pass options.baseUrl, a URL like "http://localhost:9000"' if !@options.baseUrl?

    @shortcuts = {}

  # Make browser.shortcuts.namespace.doSomething() does something.
  #
  # Shortcut modules are in lib/shortcuts/[namespace].coffee.
  #
  # For instance, if you call `loadShortcuts('foobar')` and the file
  # `lib/shortcuts/foobar.coffee` contains this code:
  #
  #     module.exports = (browser) ->
  #       clickLink: (text) ->
  #         browser.click(link: text)
  #
  # Then subsequent code will be able to call
  # `browser.shortcuts.foobar.clickLink('baz')`.
  loadShortcuts: (namespace) ->
    @debug("loadShortcuts(#{namespace})")
    if namespace not of @shortcuts
      @shortcuts[namespace] = require("./shortcuts/#{namespace}")(@)
    @

  # Returns a Promise of an Element.
  #
  # This must be chained with Element methods such as .click(). Element methods
  # all return this Browser.
  #
  # If the element is hidden, it will not be found.
  #
  # `locator` may contain the following attributes:
  #
  # * `class`, `className`: selects by class name
  # * `contains`: a substring of the text within the HTML element
  # * `enabled`: `true` or `false`, or `null` (the default)
  # * `index`: selects the Nth (one-based) element (instead of all of them)
  # * `name`: selects by "name" attribute
  # * `tag`: an HTML tag name
  # * `css`: a CSS selector (excludes xpath and others)
  # * `xpath`: an XPath selector (excludes css and others)
  # * `link`, `button`: shortcut for `{ tag: '(a|button)', contains: '(value)'}`
  #
  # You may pass an *Array* of such locator objects to find children. (This is
  # useful for finding, say, children of an element with text 'foo'.)
  #
  # You may also pass the special `wait` property. If set, the browser will
  # wait at most `wait` milliseconds for the element to appear. You may also
  # pass `wait: true` or `wait: 'slow'` for reasonable defaults.
  #
  # If the find does not find any elements, the whole test fails.
  find: (locator) ->
    debug("scheduling find(#{JSON.stringify(locator)})")
    @debug("find(#{JSON.stringify(locator)})")

    options = {}

    if locator instanceof Array
      options = locator
    else
      for k, v of locator when k != 'wait'
        options[k] = v

    locateBy = new Locator(options).toBy()

    elementPromise = if locator.wait
      timeout = TIMEOUTS[locator.wait]
      throw new Error("wait option must be #{Object.keys(TIMEOUTS).join(' or ')}") if !timeout?
      @_findByWithTimeout(locateBy, timeout)
    else
      @_findBy(locateBy)

    elementPromise.then((el) -> new Element(el))

  # Just like webdriver's findElement(), but it filters out invisible elements.
  #
  # The caller should wrap this in @driver.call() to insert this call
  # in the correct position in the WebDriver "flow". (Stupid "flow"....)
  _findBy: (locateBy) ->
    firstDisplayedEl = (els, index=0) ->
      # WebDriver's Promise.future sometimes ... erm ... gives up and moves on
      # to the next step in the promise chain.
      if index >= els.length
        throw new LocateError("Could not find visible element matching #{locateBy}.")
      else
        el = els[index]
        el.isDisplayed().then (isDisplayed) ->
          if isDisplayed
            el
          else
            firstDisplayedEl(els, index + 1)

    @driver.findElements(locateBy).then(firstDisplayedEl)

  # Calls _findBy() in a loop, until timeout expires.
  #
  # The caller should wrap this in @driver.call() to insert this call
  # in the correct position in the WebDriver "flow". (Stupid "flow"....)
  _findByWithTimeout: (locateBy, timeout) ->
    start = new Date()

    # A "then" on a WebDriver promise puts it at the head of the
    # promise chain
    step = =>
      @_findBy(locateBy)
        .catch (err) =>
          if err instanceof LocateError
            if (new Date()) - start < timeout
              # Don't use nextTick() or setTimeout()! They break
              # selenium-webdriver assumptions
              return @driver.flow_.execute(step) # a Promise
            else
              throw new WaitingLocateError("Could not find visible element matching #{locateBy} within #{timeout}ms")
          else
            throw err

    step()

  # Tests that the element exists, optionally waiting for it.
  #
  # This is just like find(), but it returns the Browser, for chaining.
  assertExists: (locator) ->
    debug("scheduling assertExists(#{JSON.stringify(locator)})")
    @debug("assertExists(#{JSON.stringify(locator)})")
    @find(locator)
    @

  # Tests that the element does _not_ exist
  assertNotExists: (locator) ->
    debug("scheduling assertNotExists(#{JSON.stringify(locator)})")
    @debug("assertNotExists(#{JSON.stringify(locator)})")
    @find(locator)
      .then(-> throw new Error("Element matching #{locator} was found; expected not to find it"))
      .catch (err) =>
        if err instanceof LocateError
          null # success
        else
          throw err
    @

  # Alias for find(locator).click()
  click: (locator) ->
    debug("scheduling click(#{JSON.stringify(locator)})")
    @debug("click(#{JSON.stringify(locator)})")
    @find(locator).then((el) -> el.click())
    @

  # Alias for find(locator).sendKeys(keys)
  sendKeys: (keys, locator) ->
    throw new Error("Why no keys?") if !keys?
    debug("scheduling sendKeys(#{keys}, #{JSON.stringify(locator)})")
    @debug("sendKeys(#{keys}, #{JSON.stringify(locator)})")
    @find(locator).then((el) -> el.sendKeys(keys))
    @

  # Alias for find(locator).clear()
  clear: (locator) ->
    debug("scheduling clear(#{JSON.stringify(locator)})")
    @debug("clear(#{JSON.stringify(locator)})")
    @find(locator).then((el) -> el.clear())
    @

  # Alias for find(locator).getText()
  getText: (locator) ->
    debug("scheduling getText(#{JSON.stringify(locator)})")
    @debug("getText(#{JSON.stringify(locator)})")
    @find(locator).then((el) -> el.getText())

  # Alias for find(locator).getAttribute(attribute)
  getAttribute: (locator, attribute) ->
    debug("scheduling getAttribute(#{JSON.stringify(locator)},#{JSON.stringify(attribute)})")
    @debug("getAttribute(#{JSON.stringify(locator)},#{JSON.stringify(attribute)})")
    @find(locator).then((el) -> el.getAttribute(attribute))

  # Alias for find(locator).isSelected()
  isSelected: (locator) ->
    debug("scheduling isSelected(#{JSON.stringify(locator)})")
    @debug("isSelected(#{JSON.stringify(locator)})")
    @find(locator).then((el) -> el.isSelected())

  # Sets the current frame (e.g., to an iframe)
  switchToFrame: (frame) ->
    debug("scheduling setFrame(#{JSON.stringify(frame)})")
    @debug("setFrame(#{JSON.stringify(frame)})")
    @driver.switchTo().frame(frame)
    @

  # Returns the "alert" interface. Usage:
  #
  # browser.alert().exists(): Promise of boolean
  # browser.alert().sendKeys(text): Sends text to the alert. Returns browser, for chaining.
  # browser.alert().accept(): Accepts the alert. Returns browser, for chaining.
  # browser.alert().cancel(): Cancels the alert. Returns browser, for chaining.
  # browser.alert().text(): Promise of text
  #
  # Only `exists()` is safe to call when there is no alert open.
  alert: ->
    debug('scheduling alert()')
    @debug('alert()')

    exists: =>
      @driver.switchTo().alert().then((-> true), (-> false))

    getText: =>
      @driver.switchTo().alert().getText()

    sendKeys: (keys) =>
      @driver.switchTo().alert().sendKeys(keys)
      @

    accept: =>
      @driver.switchTo().alert().accept()
      @

    cancel: =>
      @driver.switchTo().alert().cancel()
      @

  # Schedules a function. Returns the browser, for chaining.
  #
  # For instance:
  #
  #   browser = new Browser(...)
  #   browser
  #     .click(button: 'submit')
  #     .call(-> expect(browser.find(button: 'submit')).to.eventually.be.disabled)
  call: (fn) ->
    debug('scheduling call()')
    @debug('call()')
    @driver.call(fn)
    @

  # Blocks until the given JavaScript runs on the browser and returns true.
  #
  # Re-runs the JavaScript every 50ms.
  waitUntilBlockReturnsTrue: (message, timeout, block) ->
    debug("scheduling waitForJavascriptBlock(#{message}, #{timeout} -- #{block})")
    @debug("waitForJavascriptBlock(#{message}, #{timeout}) -- #{block}")
    timeout = TIMEOUTS[timeout]
    throw new Error("timeout must be #{Object.keys(TIMEOUTS).join(' or ')}") if !timeout?

    @driver.call =>
      start = new Date()
      lastRetval = {} # we log return values, but not duplicates

      step = =>
        @driver.executeScript(block)
          .then (retval) =>
            now = new Date()

            if !lastRetval.hasOwnProperty('value') || retval != lastRetval.value
              debug("#{retval} after #{now - start}ms")
            lastRetval.value = retval

            if retval
              return retval
            else if now - start < timeout
              # Don't use nextTick() or setTimeout()! They break
              # selenium-webdriver assumptions
              return @driver.flow_.execute(step) # a Promise
            else
              throw new Error("Timed out waiting for #{message}")

      step()

    @

  # Schedules a synchronous JavaScript function on client; returns a Promise of
  # the result.
  #
  # The first argument must be a function. Subsequent arguments are its
  # arguments. Only plain old data objects will work.
  execute: (func, args...) ->
    debug('scheduling execute()')
    @debug('execute()')
    @driver.executeScript(func, args...)
    @

  # Browses to a new web page. Returns the Browser.
  #
  # The next command will execute after the new page has finished loading.
  #
  # This takes a path, not a URL: for instance, `browser.get('/foo/bar')`
  get: (path) ->
    debug("scheduling get(#{path})")
    @debug("get(#{path})")
    @driver.get(@options.baseUrl + path)
    @

  # Refreshes the page. Returns the Browser.
  refresh: ->
    debug('scheduling refresh()')
    @debug("refresh()")
    @driver.navigate().refresh()
    @

  # Make Browser a Promise, so you can `return browser` in async unit tests.
  #
  # Selenium WebDriver handles our promise chain: the list of commands that
  # we've _typed_ synchronously but which haven't executed yet. We want mocha's
  # before/after/beforeEach/afterEach to finish when the browser has caught up
  # with the promise chain, so that errors will get thrown there rather than in
  # subsequent tests.
  then: (onSuccess, onError) ->
    debug('scheduling queue flush')
    @driver
      .call(-> debug('queue flushed'))
      .then(
        () -> process.nextTick(onSuccess),
        (err) -> process.nextTick(onError, err)
      )

  # Returns the current URL as a Promise.
  getUrl: ->
    debug('scheduling getUrl()')
    @debug('getUrl()')
    @driver.getCurrentUrl()

  # Frees all resources associated with this Browser.
  close: ->
    debug('scheduling close()')
    @debug('close()')
    @driver.quit()
    @

  # Schedules a wait.
  #
  # Useful for debugging.
  sleep: (ms) ->
    debug("scheduling sleep(#{ms})")
    @debug("sleep(#{ms})")
    @driver.sleep(ms)
    @
