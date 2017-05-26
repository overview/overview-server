const webdriver = require('selenium-webdriver')
const FileDetector = require('selenium-webdriver/remote').FileDetector

const Browser = require('./Browser')
const UserAdminSession = require('./UserAdminSession')

const Constants = {
  pageLoadTimeout: 30000 // Travis+SauceLabs is slow, especially for Vimeo
}

const options = {
  desiredCapabilities: {
    browserName: 'chrome',
    version: '',
    platform: 'ANY',
    build: process.env.BUILD_TAG,
    handlesAlerts: true,
  },
  seleniumLocation: {
    host: 'localhost',
    port: 4444,
    url: 'http://localhost:4444', // no Selenium Hub: just a straight web driver
  },
}

if (process.env.SAUCE_USER_NAME) {
  const x = options.seleniumLocation
  x.hostname = process.env.SELENIUM_HOST
  x.port = process.env.SELENIUM_PORT
  x.user = process.env.SAUCE_USER_NAME
  x.pwd = process.env.SAUCE_API_KEY
  x.url = 'http://localhost:4444/wd/hub'
}

module.exports = {
  baseUrl: 'http://localhost:9000',

  adminLogin: {
    email: 'admin@overviewdocs.com',
    password: 'admin@overviewdocs.com',
  },

  createUserAdminSession: function(title) {
    return new UserAdminSession({
      baseUrl: module.exports.baseUrl,
      timeout: Constants.pageLoadTimeout,
      login: module.exports.adminLogin,
    })
  },

  createBrowser: async function() {
    const driver = new webdriver.Builder()
      .usingServer(options.seleniumLocation.url)
      .forBrowser('chrome')
      .withCapabilities(options.desiredCapabilities)
      .setLoggingPrefs({ browser: 'ALL' })
      .build()

    driver.setFileDetector(new FileDetector())

    browser = new Browser(driver, { baseUrl: module.exports.baseUrl })
    await browser.init()

    return browser
  },
}
