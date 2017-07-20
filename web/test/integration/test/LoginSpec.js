'use strict'

const asUser = require('../support/asUser')

class LoginShortcuts {
  constructor(browser) {
    this.b = browser
  }

  async assertLoggedInAs(email) {
    await this.b.assertExists({ tag: 'div', class: 'logged-in', contains: email, wait: 'pageLoad' })
  }

  async assertLogInFailed() {
    await this.b.assertExists({ tag: 'h1', contains: 'Log in', wait: 'pageLoad' })
    await this.b.assertExists({ class: 'error', contains: 'Wrong email address or password' })
  }

  async assertLoggedOut() {
    await this.b.assertExists({ class: 'session-form', wait: 'pageLoad' })
  }

  async tryLogIn(email, password) {
    await this.b.sendKeys(email, '.session-form [name=email]')
    await this.b.sendKeys(password, '.session-form [name=password]')
    await this.b.click('.session-form [type=submit]')
  }

  async logOut() {
    await this.b.click({ link: 'Log out' })
    await this.b.assertExists({ class: 'session-form', wait: 'pageLoad' })
  }
}

// In order to let users store their own information
// We need to let users identify themselves
describe('Login', function() {
  asUser.usingTemporaryUser(function() {
    before(async function() {
      this.login = new LoginShortcuts(this.browser)
      this.b = this.browser

      // We'll start this test suite logged out.
      await this.login.logOut()
    })

    it('should log in', async function() {
      await this.login.tryLogIn(this.userEmail, this.userEmail)
      await this.login.assertLoggedInAs(this.userEmail)
      await this.login.logOut()
    })

    it('should log out', async function() {
      // This is easy -- we logged out in the before hook ;)
      await this.login.assertLoggedOut()
    })

    it('should not log in with wrong password', async function() {
      await this.login.tryLogIn(this.userEmail, 'bad-password')
      await this.login.assertLogInFailed()
    })

    it('should not log in with the wrong username', async function() {
      await this.login.tryLogIn("x-#{this.userEmail}", this.userEmail)
      await this.login.assertLogInFailed()
    })
  })
})
