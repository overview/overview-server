'use strict'

const BrowserBuilder = require('../lib/BrowserBuilder')

module.exports = {
  // Registers a temporary user and logs in as that user for the duration of
  // this describe block.
  //
  // Usage:
  //
  //     describe('my test suite', function() {
  //       asUser.usingTemporaryUser({ isAdmin: false, propertyName: 'alice' }, function() {
  //         it('should have an email', function() {
  //           expect(this.aliceUser).to.exist
  //           expect(this.aliceEmail).to.exist
  //         })
  //
  //         it('should have a browser', function() {
  //           expect(this.aliceBrowser).to.exist
  //         })
  //
  //         it('should be logged in', async function() {
  //           const text = await this.aliceBrowser.find({ css: '.logged-in' }).getText()
  //           expect(text).to.eq(this.aliceEmail)
  //         })
  //       })
  //     })
  //
  // In other words, we set:
  //
  // * this[`${propertyName}User`]: { email, ... } Object. `password == email`.
  // * this[`${propertyName}Email`]: String
  // * this[`${propertyName}Browser`]: Browser
  //
  // If you omit options or set propertyName to `null`, then the properties
  // will be:
  //
  // * this.user: { email, ... }
  // * this.userEmail: String
  // * this.browser: Browser
  usingTemporaryUser(options, body) {
    // Default: options={}
    if (typeof(options) === 'function') {
      body = options
      options = {}
    }

    describe('with a temporary user', function() {
      let user = null
      let browser = null

      beforeEach(async function() {
        if (!this.adminSession) {
          this.adminSession = BrowserBuilder.createUserAdminSession('asUser.usingTemporaryUser')
        }

        user = await this.adminSession.createTemporaryUser()
        if (options.isAdmin) await this.adminSession.setUserIsAdmin(user.email, true)

        browser = await BrowserBuilder.createBrowser()
        await browser.get('/login')
        await browser.sendKeys(user.email, { css: '.session-form [name=email]' })
        await browser.sendKeys(user.email, { css: '.session-form [name=password]' })
        await browser.click({ css: '.session-form [type=submit]' })

        // Set this.user, this.userEmail, this.browser
        // or this.alice, this.aliceEmail, this.aliceBrowser
        let userProp = 'user'
        let userEmailProp = 'userEmail'
        let browserProp = 'browser'
        if (options.propertyName) {
          const p = options.propertyName
          userProp = p + 'User'
          userEmailProp = p + 'Email'
          browserProp = p + 'Browser'
        }
        this[userProp] = user
        this[userEmailProp] = user.email
        this[browserProp] = browser
      })

      afterEach(async function() {
        if (this.adminSession && user) {
          await this.adminSession.deleteUser(user)
        }

        if (browser) {
          await browser.driver.manage().deleteAllCookies()
          await browser.close()
        }
      })

      body()
    })
  }
}
