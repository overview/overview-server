'use strict'

const browser = require('../lib/BrowserBuilder')

module.exports = {
  // Returns a browser logged in as the administrator.
  //
  // Usage:
  //
  //     describe 'my test suite', ->
  //       asUser.usingTemporaryUser ->
  //         asUser.usingAdminBrowser ->
  //           it 'should be logged in', ->
  //             @browser.find(css: '.logged-in').getText().should.eventually.eq(@userEmail)
  //
  //           it 'should have admin browser', ->
  //             @adminBrowser.find(css: '.logged-in').getText().should.eventually.eq(@adminEmail)
  //
  // Tests can make use of @adminBrowser and @adminEmail.
  usingAdminBrowser: function(body) {
    describe('with an administrator logged in', function() {
      before(async function() {
        if (!this.adminSession) {
          throw new Error("You must call usingAdminBrowser() within a usingTemporaryUser() block")
        }

        this.adminEmail = this.adminSession.options.login.email
        const ab = this.adminBrowser = await browser.createBrowser()

        await ab.get('/login')
        await ab.sendKeys(this.adminEmail, '.session-form [name=email]')
        await ab.sendKeys(this.adminSession.options.login.password, '.session-form [name=password]')
        await ab.click('.session-form [type=submit]')
      })

      after(async function() {
        const ab = this.adminBrowser
        await ab.driver.manage().deleteAllCookies()
        await ab.close()
      })

      body()
    })
  },

  // Registers a temporary user and logs in as that user for the duration of
  // this describe block.
  //
  // Usage:
  //
  //     describe 'my test suite', ->
  //       asUser.usingTemporaryUser ->
  //         it 'should have an email', ->
  //           @userEmail.should.not.be.null
  //
  //         it 'should have a browser', ->
  //           @browser.should.not.be.null
  //
  //         it 'should be logged in', ->
  //           @browser.find(css: '.logged-in').getText().should.eventually.eq(@userEmail)
  //
  // Tests should make use of @browser and @userEmail. The password and email are
  // the same.
  usingTemporaryUser: function(body) {
    describe('with a temporary user', function() {
      before(async function() {
        this.adminSession = browser.createUserAdminSession('asUser.usingTemporaryUser')
        const user = await this.adminSession.createTemporaryUser()

        this.user = user
        this.userEmail = user.email
        const b = this.browser = await browser.createBrowser()
        await b.get('/login')
        await b.sendKeys(this.userEmail, { css: '.session-form [name=email]' })
        await b.sendKeys(this.userEmail, { css: '.session-form [name=password]' })
        await b.click({ css: '.session-form [type=submit]' })
      })

      after(async function() {
        await this.adminSession.deleteUser(this.user)
        const b = this.browser
        await b.driver.manage().deleteAllCookies()
        await b.close()
      })

      body()
    })
  },
}
