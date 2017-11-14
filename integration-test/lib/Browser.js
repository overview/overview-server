const debug = require('debug')('Browser')
const webdriver = require('selenium-webdriver')

const Element = require('./Element')
const Locator = require('./Locator')
const TIMEOUTS = require('./TIMEOUTS')

class LocateError extends Error {}
class WaitingLocateError extends Error {}

// A wrapper around Selenium WebDriver, tuned for brevity.
module.exports = class Browser {
  constructor(driver, options) {
    this.driver = driver
    this.options = options
    this.shortcuts = {}
    this.initCalled = false

    if (!options.baseUrl) {
      throw 'Must pass options.baseUrl, a URL like "http://localhost:9000"'
    }

    debug("Browser constructor")
  }

  async init() {
    // As of 2015-05-27, ~99% of our users are >= 1024x768
    await this.driver.manage().window().setSize(1024, 768)
    this.initCalled = true
  }

  assertInitCalled() {
    if (!this.initCalled) {
      throw new Error("You called this Browser method before calling init(). Call init() first!")
    }
  }

  // Make browser.shortcuts.namespace.doSomething() do something.
  //
  // Shortcut modules are in lib/shortcuts/[namespace].js.
  //
  // For instance, if you call `loadShortcuts('foobar')` and the file
  // `lib/shortcuts/foobar.js` contains this code:
  //
  //     module.exports = function(browser) {
  //       return {
  //         clickLink: async (text) => { await browser.click({ link: test }) },
  //       }
  //     }
  //
  // Then subsequent code will be able to call
  // `browser.shortcuts.foobar.clickLink('baz')`.
  loadShortcuts(namespace) {
    if (!this.shortcuts[namespace]) {
      this.shortcuts[namespace] = require(`./shortcuts/${namespace}`)(this)
    }
    return this
  }

  // Returns a Promise of an Element.
  //
  // If the element is hidden, it will not be found.
  //
  // `locator` may contain the following attributes:
  //
  // * `class`, `className`: selects by class name
  // * `contains`: a substring of the text within the HTML element
  // * `enabled`: `true` or `false`, or `null` (the default)
  // * `index`: selects the Nth (one-based) element (instead of all of them)
  // * `name`: selects by "name" attribute
  // * `tag`: an HTML tag name
  // * `css`: a CSS selector (excludes xpath and others)
  // * `xpath`: an XPath selector (excludes css and others)
  // * `link`, `button`: shortcut for `{ tag: '(a|button)', contains: '(value)'}`
  //
  // You may pass an *Array* of such locator objects to find children. (This is
  // useful for finding, say, children of an element with text 'foo'.)
  //
  // You may also pass the special `wait` property -- or you may pass it in a
  // second "options" argument. If set, the browser will wait at most `wait`
  // milliseconds for the element to appear. You may also pass `wait: true` or
  // `wait: 'slow'` for reasonable defaults.
  //
  // Finally, you may pass a String `locator` as shorthand for `css`.
  //
  // If no elements are found, this method will throw a LocateError.
  async find(locator, options) {
    debug(`find(${JSON.stringify(locator)})`)

    options = Object.assign({ throwOnNull: true }, options)

    let locateOptions = null

    if (Array.isArray(locator)) {
      locateOptions = locator
    } else if (typeof locator === 'string' || locator instanceof String) {
      locateOptions = [ locator ]
    } else {
      locateOptions = [ Object.assign({}, locator) ]
      if (locator.wait) {
        delete locateOptions[0].wait
        options.wait = locator.wait
      }
    }

    const locateBy = new Locator(locateOptions).toBy()

    let element

    if (options.wait) {
      const timeout = TIMEOUTS[options.wait]
      if (!timeout) {
        throw new Error(`wait option must be ${Object.keys(TIMEOUTS).join(' or ')}`)
      }
      element = await this._findByWithTimeout(locateBy, timeout)
    } else {
      element = await this._findBy(locateBy)
    }

    if (element === null) {
      if (options.throwOnNull) {
        throw new LocateError(`Could not find visible element matching ${JSON.stringify(locateBy)}.`)
      } else {
        return null
      }
    } else {
      return new Element(element)
    }
  }

  // Just like webdriver's findElement(), but it filters out invisible elements
  // and returns null if there's a StaleElementReferenceError (a race that
  // ends up deleting an element we're looking for).
  async _findBy(locateBy) {
    const elements = await this.driver.findElements(locateBy)

    for (let i = 0; i < elements.length; i++) {
      const element = elements[i]
      try {
        if (await element.isDisplayed()) {
          return element
        }
      } catch (e) {
        if (e.name === 'StaleElementReferenceError') {
          // ignore the error. It means the element isn't displayed any more:
          // it isn't even on the page
        } else {
          throw e
        }
      }
    }

    return null
  }

  // Calls _findBy() in a loop, until timeout expires.
  async _findByWithTimeout(locateBy, timeout) {
    const start = new Date()

    while (true) {
      const element = await this._findBy(locateBy)
      if (element) {
        return new Element(element)
      } else if (new Date() - start > timeout) {
        return null
      }
    }
  }

  // Tests that the element exists, optionally waiting for it.
  //
  // This is just like find(..., { throwOnNull: true }).
  async assertExists(locator, options) {
    debug(`assertExists(${JSON.stringify(locator)}, ${JSON.stringify(options)})`)
    await this.find(locator, Object.assign({}, options, { throwOnNull: true }))
  }

  // Tests that the element does _not_ exist.
  //
  // You cannot specify a timeout for this method.
  async assertNotExists(locator) {
    debug(`assertNotExists(${JSON.stringify(locator)}})`)

    if (await this.find(locator, { throwOnNull: false })) {
      throw new Error(`Element matching ${JSON.stringify(locator)} was found; expected not to find it`)
    }
  }

  // find(locator).clear()
  async clear(locator) {
    debug(`click(${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    await element.clear()
  }

  // find(locator).click()
  async click(locator) {
    debug(`click(${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    await element.click()
  }

  // find(locator).sendKeys(keys)
  async sendKeys(keys, locator) {
    if (keys == null) {
      throw new Error("You called this method with no keys")
    }

    debug(`sendKeys(${keys}, ${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    await element.sendKeys(keys)
  }

  // find(locator).clear()
  async clear(locator) {
    debug(`clear(${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    await element.clear()
  }

  // find(locator).getText()
  async getText(locator) {
    debug(`getText(${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    return element.getText()
  }

  // find(locator).getAttribute(attribute)
  async getAttribute(locator, attribute) {
    debug(`getAttribute(${JSON.stringify(locator)}, ${JSON.stringify(attribute)})`)
    const element = await this.find(locator)
    return element.getAttribute(attribute)
  }

  // find(locator).isSelected()
  //
  // This will indicate, for instance, whether a checkbox is checked.
  async isSelected(locator) {
    debug(`isSelected(${JSON.stringify(locator)})`)
    const element = await this.find(locator)
    return element.isSelected()
  }

  // Sets the current frame (e.g., to an iframe)
  async switchToFrame(frame) {
    debug(`setFrame(${JSON.stringify(frame)})`)
    return this.driver.switchTo().frame(frame)
  }

  // Returns the "alert" interface. Usage:
  //
  // browser.alert().exists(): Promise of boolean
  // browser.alert().sendKeys(text): Sends text to the alert. Returns browser, for chaining.
  // browser.alert().accept(): Accepts the alert. Returns browser, for chaining.
  // browser.alert().cancel(): Cancels the alert. Returns browser, for chaining.
  // browser.alert().text(): Promise of text
  //
  // Only `exists()` is safe to call when there is no alert open.
  alert() {
    debug('alert()')

    const driver = this.driver

    return {
      exists: async () => {
        return !!(await driver.switchTo().alert())
      },

      getText: async () => {
        return await driver.switchTo().alert().getText()
      },

      sendKeys: async (keys) => {
        return await driver.switchTo().alert().sendKeys(keys)
      },

      accept: async () => {
        return await driver.switchTo().alert().accept()
      },

      cancel: async () => {
        return await driver.switchTo().alert().cancel()
      },
    }
  }

  // Runs and runs the given JavaScript in _node_ until it returns truthy.
  async waitUntilFunctionReturnsTrue(message, timeout, block) {
    timeout = TIMEOUTS[timeout]
    if (!timeout) {
      throw new Error(`timeout must be ${Object.keys(TIMEOUTS).join(' or ')}`)
    }

    debug(`waitForJavascriptBlock(${message}, ${timeout} -- ${block})`)

    const start = new Date()
    const lastRetval = {} // we log return values, but not duplicates

    do {
      const retval = await block()

      if (!lastRetval.hasOwnProperty('value') || retval !== lastRetval.value) {
        lastRetval.value = retval
        debug(`Function returned ${JSON.stringify(retval)}`)
      }

      if (retval) return
    } while (new Date() - start < timeout)

    throw new Error(`Timed out waiting for ${message}`)
  }

  // Runs and runs the given JavaScript in the browser until it returns truthy.
  async waitUntilBlockReturnsTrue(message, timeout, block) {
    return this.waitUntilFunctionReturnsTrue(message, timeout, async () => {
      return this.driver.executeScript(block)
    })
  }

  // Runs JavaScript on the browser; returns a Promise of the result.
  //
  // The first argument must be a function. Subsequent arguments are its
  // arguments. Only plain old data objects will work.
  async execute(func, ...args) {
    debug('execute()')
    return await this.driver.executeScript(func, ...args)
  }

  // Browses to a new web page. Returns a Promise that resolves when the
  // page has "finished loading" (whatever that means -- it's a Selenium thing)
  //
  // This takes a path, not a URL: for instance, `browser.get('/foo/bar')`
  async get(path) {
    debug(`get(${path})`)
    await this.driver.get(this.options.baseUrl + path)
  }

  // Refreshes the current web page. Returns a Promise that resolves when the
  // page has "finished loading" (whatever that means -- it's a Selenium thing)
  async refresh() {
    debug('refresh()')
    await this.driver.navigate().refresh()
  }

  // Returns the current URL as a Promise.
  async getUrl() {
    debug('getUrl()')
    return await this.driver.getCurrentUrl()
  }

  // Frees all resources associated with this Browser.
  async close() {
    debug('close()')
    await this.driver.manage().deleteAllCookies()
    await this.driver.quit()
  }

  // Returns a Promise that resolves after a bit of nothing.
  //
  // Useful for debugging.
  async sleep(ms) {
    debug(`sleep(${ms})`)
    await this.driver.sleep(ms)
  }
}
