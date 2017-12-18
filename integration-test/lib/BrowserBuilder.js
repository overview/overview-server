const webdriver = require('selenium-webdriver')
const FileDetector = require('selenium-webdriver/remote').FileDetector

const Browser = require('./Browser')
const UserAdminSession = require('./UserAdminSession')

const Constants = {
  pageLoadTimeout: 30000 // Travis+SauceLabs is slow, especially for Vimeo
}

if (!process.env.OVERVIEW_URL) process.env.OVERVIEW_URL="http://overview-web"
if (!process.env.OVERVIEW_ADMIN_EMAIL) process.env.OVERVIEW_ADMIN_EMAIL="admin@overviewdocs.com"
if (!process.env.OVERVIEW_ADMIN_PASSWORD) process.env.OVERVIEW_ADMIN_PASSWORD="admin@overviewdocs.com"

const chromeCapabilities = webdriver.Capabilities.chrome()
chromeCapabilities.set('chromeOptions', {
  args: [
    '--headless',
    '--no-sandbox',
    '--disable-gpu',
  ],
})
chromeCapabilities.set('handlesAlerts', true)

module.exports = {
  baseUrl: process.env.OVERVIEW_URL,
  adminLogin: {
    email: process.env.OVERVIEW_ADMIN_EMAIL,
    password: process.env.OVERVIEW_ADMIN_PASSWORD,
  },

  createUserAdminSession: function(title) {
    return new UserAdminSession({
      baseUrl: process.env.OVERVIEW_URL,
      timeout: Constants.pageLoadTimeout,
      login: {
        email: process.env.OVERVIEW_ADMIN_EMAIL,
        password: process.env.OVERVIEW_ADMIN_PASSWORD,
      },
    })
  },

  createBrowser: async function() {
    const driver = new webdriver.Builder()
      .forBrowser('chrome')
      .usingServer('http://localhost:4444')
      .withCapabilities(chromeCapabilities)
      .setLoggingPrefs({ browser: 'ALL' })
      .build()

    driver.setFileDetector(new FileDetector())

    browser = new Browser(driver, { baseUrl: process.env.OVERVIEW_URL })
    await browser.init()

    return browser
  },
}
