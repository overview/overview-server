'use strict'

const faker = require('faker')
const browser = require('../lib/BrowserBuilder')

const Url = {
  login: '/login',
  confirm: (token) => `/confirm/${token}`,
}

class RegistrationShortcuts {
  constructor(browser) {
    this.b = browser
  }

  async tryLogIn(email, password) {
    await this.b.assertExists('.session-form', { wait: 'pageLoad' })
    await this.b.sendKeys(email, '.session-form [name=email]')
    await this.b.sendKeys(password, '.session-form [name=password]')
    await this.b.click('.session-form [type=submit]')
  }

  async tryRegister(email, password, password2) {
    await this.b.assertExists('.user-form', { wait: 'pageLoad' })
    await this.b.sendKeys(email, '.user-form [name=email]')
    await this.b.sendKeys(password, '.user-form [name=password]')
    await this.b.sendKeys(password2, '.user-form [name=password2]')
    await this.b.click('.user-form [type=submit]')
  }

  async assertLoggedInAs(email) {
    await this.b.assertExists({ tag: 'div', class: 'logged-in', contains: email, wait: 'pageLoad' })
  }

  async assertLogInFailed() {
    await this.b.assertExists({ tag: 'h1', contains: 'Log in', wait: 'pageLoad' })
    await this.b.assertExists({ class: 'error', contains: 'Wrong email address or password' })
  }

  async logOut() {
    await this.b.click({ link: 'Log out' })
    await this.b.assertExists({ class: 'session-form', wait: 'pageLoad' })
  }
}

describe('Registration', function() {
  beforeEach(async function() {
    this.userEmail = faker.internet.email()
    this.validPassword = 'icrucGofbap4'

    this.browser = await browser.createBrowser()
    this.b = this.browser
    this.adminSession = browser.createUserAdminSession('Registration')
    this.registration = new RegistrationShortcuts(this.browser)
  })

  afterEach(async function() {
    if (this.browser) await this.browser.close()
  })

  it('should not register a bad email format', async function() {
    await this.b.get(Url.login)
    await this.registration.tryRegister('@' + this.userEmail, this.validPassword, this.validPassword)
    await this.b.assertExists('.user-form [name=email]:invalid')
  })

  it('should not register a weak password', async function() {
    await this.b.get(Url.login)
    await this.registration.tryRegister(this.userEmail, 'weak', 'weak')
    await this.b.assertExists('.user-form .control-group.error [name=password]')
    await this.b.assertExists('.user-form .control-group.error p.help-block')
  })

  it('should not register mismatched passwords', async function() {
    await this.b.get(Url.login)
    await this.registration.tryRegister(this.userEmail, this.validPassword, this.validPassword + '1')
    await this.b.assertExists('.user-form .control-group.error [name=password2]')
    await this.b.assertExists('.user-form .control-group.error p.help-block')
  })

  it('should show an error when clicking an invalid token link', async function() {
    await this.b.get(Url.confirm('abcdef'))
    await this.b.assertExists({ tag: 'h1', contains: 'Broken confirmation link' })
    await this.b.assertExists('a[href="/reset-password"]')
  })

  describe('when the user already exists', function() {
    beforeEach(async function() {
      await this.adminSession.createUser({
        email: this.userEmail,
        password: this.validPassword,
      })

      await this.b.get(Url.login)
      await this.registration.tryRegister(this.userEmail, this.validPassword + '1', this.validPassword + '1')
      await this.b.assertExists({ tag: 'h1', contains: 'Check your email', wait: 'pageLoad' })
    })

    afterEach(async function() {
      await this.adminSession.deleteUser({ email: this.userEmail })
    })

    it('should not change the password', async function() {
      // This would be a huge security hole!
      await this.b.get(Url.login)
      await this.registration.tryLogIn(this.userEmail, this.validPassword)
      await this.registration.assertLoggedInAs(this.userEmail)
      await this.registration.logOut()
      await this.registration.tryLogIn(this.userEmail, this.validPassword + '1')
      await this.registration.assertLogInFailed()
    })

    it('should not give the user a confirmation token', async function() {
      const json = await this.adminSession.showUser({ email: this.userEmail })
      const token = JSON.parse(json).confirmation_token

      expect(token).to.eq(null)
    })
  })

  describe('when the user tries to register', function() {
    beforeEach(/* beware -- not async */ function () {
      this.getConfirmationToken = async () => {
        const json = await this.adminSession.showUser({ email: this.userEmail })
        return JSON.parse(json).confirmation_token
      }
    })

    beforeEach(async function() {
      await this.b.get(Url.login)
      await this.registration.tryRegister(this.userEmail, this.validPassword, this.validPassword)
      await this.b.assertExists({ tag: 'h1', contains: 'Check your email', wait: 'pageLoad' })
    })

    afterEach(async function() {
      await this.adminSession.deleteUser({ email: this.userEmail })
    })

    it('should prompt the user to check his or her email', async function() {
      await this.b.assertExists({ tag: 'h1', contains: 'Check your email', wait: 'pageLoad' })
    })

    it('should set a confirmation token on the user', async function() {
      await this.getConfirmationToken().should.eventually.match(/\w+/)
    })

    it('should not let the user log in before confirming', async function() {
      await this.b.get('/login')
      await this.registration.tryLogIn(this.userEmail, this.validPassword)
      await this.b.assertExists({ class: 'error', contains: 'click the link in the confirmation email', wait: 'pageLoad' })
    })

    it('should recognize the user if the password is incorrect', async function() {
      await this.b.get('/login')
      await this.registration.tryLogIn(this.userEmail, this.validPassword + '1')
      await this.registration.assertLogInFailed()
    })

    describe('after clicking the confirmation link', function() {
      beforeEach(async function() {
        const token = await this.getConfirmationToken()
        await this.browser.get(Url.confirm(token))
      })

      it('should log you in and tell you all is well', async function() {
        await this.registration.assertLoggedInAs(this.userEmail)
        await this.b.assertExists({ class: 'alert-success', contains: 'Welcome' })
      })

      it('should disable the confirmation token', async function() {
        await this.getConfirmationToken().should.eventually.not.exist
      })
    })
  })
})
