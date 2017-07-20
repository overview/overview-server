'use strict'

const asUser = require('../support/asUser')

const Url = {
  login: '/login',
  resetPassword: (token) => `/reset-password/${token}`,
}

const NewPassword = 'pedEdcitVac8'

class ResetPasswordShortcuts {
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

  async startResetPassword(email) {
    await this.b.click({ link: 'Reset', wait: 'pageLoad' })
    await this.b.sendKeys(email, { css: 'body.password-new input[name=email]', wait: 'pageLoad' })
    await this.b.click('input[value="Email instructions to this address"]')
  }

  async assertShowsResetPasswordForm() {
    await this.b.assertExists('[name=password]', { wait: 'pageLoad' })
    await this.b.assertExists('[name=password2]')
  }

  async fillPasswordsAndClickSubmit(password1, password2) {
    await this.b.sendKeys(password1, '[name=password]')
    await this.b.sendKeys(password2, '[name=password2]')
    await this.b.click('input.btn-primary')
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
// Even after they forget their passwords
describe('ResetPassword', function() {
  asUser.usingTemporaryUser(function() {
    before(async function() {
      this.browser.loadShortcuts('jquery')

      this.jquery = this.browser.shortcuts.jquery
      this.resetPassword = new ResetPasswordShortcuts(this.browser)

      this.b = this.browser

      // We'll start this test suite logged out
      await this.resetPassword.logOut()
    })

    describe('When the user tries to reset a password', function() {
      beforeEach(async function() {
        await this.b.get(Url.login)
        await this.resetPassword.startResetPassword(this.userEmail)
      })

      it('should alert the user to check his or her email', async function() {
        await this.b.assertExists({ class: 'alert-success', contains: 'sent', wait: 'pageLoad' })
      })

      it('should do the same thing even on a second attempt', async function() {
        await this.b.get(Url.login)
        await this.resetPassword.startResetPassword(this.userEmail)
        await this.b.assertExists({ class: 'alert-success', contains: 'sent', wait: 'pageLoad' })
      })

      describe('when the user clicks the token from the email', function() {
        beforeEach(async function() {
          const json = await this.adminSession.showUser({ email: this.userEmail })
          const token = JSON.parse(json).reset_password_token
          await this.b.get(Url.resetPassword(token))
          await this.jquery.waitUntilReady()
        })

        it('should show a reset-password form', async function() {
          await this.resetPassword.assertShowsResetPasswordForm()
        })

        it('should not click through if the passwords are not set', async function() {
          await this.resetPassword.fillPasswordsAndClickSubmit('', '')
          await this.resetPassword.assertShowsResetPasswordForm()
        })

        it('should not click through if the passwords do not match', async function() {
          await this.resetPassword.fillPasswordsAndClickSubmit(NewPassword, this.userEmail)
          await this.resetPassword.assertShowsResetPasswordForm()
        })

        it('should not click through if the password is too short', async function() {
          await this.resetPassword.fillPasswordsAndClickSubmit('short', 'short')
          await this.resetPassword.assertShowsResetPasswordForm()
        })

        it('should flash success, log you in and reset the password', async function() {
          await this.resetPassword.fillPasswordsAndClickSubmit(NewPassword, NewPassword)
          await this.b.assertExists({ class: 'alert-success', contains: 'You have updated your password', wait: 'pageLoad' })
          await this.resetPassword.assertLoggedInAs(this.userEmail)
          await this.resetPassword.logOut()

          // old password doesn't work
          await this.resetPassword.tryLogIn(this.userEmail, this.userEmail)
          await this.resetPassword.assertLogInFailed()

          // new password works
          await this.resetPassword.tryLogIn(this.userEmail, NewPassword)
          await this.resetPassword.assertLoggedInAs(this.userEmail)
          await this.resetPassword.logOut()
        })
      })
    })
  })
})
