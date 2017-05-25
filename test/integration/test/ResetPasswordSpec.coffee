asUser = require('../support/asUser-new')

Url =
  login: '/login'
  resetPassword: (token) -> "/reset-password/#{token}"

NewPassword = 'pedEdcitVac8'

# In order to let users store their own information
# We need to let users identify themselves
# Even after they forget their passwords
describe 'ResetPassword', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser.loadShortcuts('jquery')

      @browser.shortcuts.resetPassword = ((browser) ->
        assertLoggedInAs: (email) ->
          browser
            .assertExists(tag: 'div', class: 'logged-in', contains: email, wait: 'pageLoad')

        assertLogInFailed: ->
          browser
            .assertExists(tag: 'h1', contains: 'Log in', wait: 'pageLoad')
            .assertExists(class: 'error', contains: 'Wrong email address or password')

        assertLoggedOut: ->
          browser
            .assertExists(class: 'session-form', wait: 'pageLoad')

        startResetPassword: (email) ->
          browser
            .click(link: 'Reset', wait: 'pageLoad')
            .sendKeys(email, css: 'body.password-new input[name=email]', wait: 'pageLoad')
            .click(css: 'input[value="Email instructions to this address"]')

        assertShowsResetPasswordForm: ->
          browser
            .assertExists(css: '[name=password]', wait: 'pageLoad')
            .assertExists(css: '[name=password2]')

        fillPasswordsAndClickSubmit: (password1, password2) ->
          browser
            .sendKeys(password1, css: '[name=password]')
            .sendKeys(password2, css: '[name=password2]')
            .click(css: 'input.btn-primary')

        tryLogIn: (email, password) ->
          browser
            .sendKeys(email, css: '.session-form [name=email]')
            .sendKeys(password, css: '.session-form [name=password]')
            .click(css: '.session-form [type=submit]')

        logOut: ->
          browser
            .click(link: 'Log out')
            .assertExists(class: 'session-form', wait: 'pageLoad')
      )(@browser)

      # We'll start this test suite logged out
      @browser.shortcuts.resetPassword.logOut()

    describe 'When the user tries to reset a password', ->
      beforeEach ->
        @browser
          .get(Url.login)
          .shortcuts.resetPassword.startResetPassword(@userEmail)

      it 'should alert the user to check his or her email', ->
        @browser
          .assertExists(css: '.alert-success', contains: 'sent', wait: 'pageLoad')

      it 'should do the same thing even on a second attempt', ->
        @browser
          .get(Url.login)
          .shortcuts.resetPassword.startResetPassword(@userEmail)
          .assertExists(css: '.alert-success', contains: 'sent', wait: 'pageLoad')

      describe 'when the user clicks the token from the email', ->
        beforeEach ->
          @adminSession.showUser(email: @userEmail)
            .then((x) -> JSON.parse(x).reset_password_token)
            .then (token) =>
              @browser.get(Url.resetPassword(token))
                .shortcuts.jquery.waitUntilReady()

        it 'should show a reset-password form', ->
          @browser
            .shortcuts.resetPassword.assertShowsResetPasswordForm()

        it 'should not click through if the passwords are not set', ->
          @browser
            .shortcuts.resetPassword.fillPasswordsAndClickSubmit('', '')
            .shortcuts.resetPassword.assertShowsResetPasswordForm()

        it 'should not click through if the passwords do not match', ->
          @browser
            .shortcuts.resetPassword.fillPasswordsAndClickSubmit(NewPassword, @userEmail)
            .shortcuts.resetPassword.assertShowsResetPasswordForm()

        it 'should not click through if the password is too short', ->
          @browser
            .shortcuts.resetPassword.fillPasswordsAndClickSubmit('short', 'short')
            .shortcuts.resetPassword.assertShowsResetPasswordForm()

        it 'should flash success, log you in and reset the password', ->
          @browser
            .shortcuts.resetPassword.fillPasswordsAndClickSubmit(NewPassword, NewPassword)
            .assertExists(class: 'alert-success', contains: 'You have updated your password', wait: 'pageLoad')
            .shortcuts.resetPassword.assertLoggedInAs(@userEmail)
            .shortcuts.resetPassword.logOut()
            # old password doesn't work
            .shortcuts.resetPassword.tryLogIn(@userEmail, @userEmail)
            .shortcuts.resetPassword.assertLogInFailed()
            # new password works
            .shortcuts.resetPassword.tryLogIn(@userEmail, NewPassword)
            .shortcuts.resetPassword.assertLoggedInAs(@userEmail)
            .shortcuts.resetPassword.logOut()
