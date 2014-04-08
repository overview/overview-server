testMethods = require('../support/testMethods')
asUser = require('../support/asUser')

Url =
  login: '/login'
  resetPassword: (token) -> "/reset-password/#{token}"

# In order to let users store their own information
# We need to let users identify themselves
# Even after they forget their passwords
describe 'ResetPassword', ->
  newPassword = 'pedEdcitVac8'

  testMethods.usingPromiseChainMethods
    shouldBeLoggedInAs: (email) ->
      @elementByCss('.logged-in strong').text().should.eventually.equal(email)

    shouldHaveFailedToLogIn: ->
      @
        .url().should.eventually.match(/\/login\b/)
        .elementByCss('.session-form').text().should.eventually.contain('Wrong email address or password')

    shouldBeLoggedOut: ->
      @elementByCss('.session-form').should.eventually.exist

    startResetPassword: (email) ->
      @
        .waitForElementBy(tag: 'a', contains: 'Reset')
        .elementBy(tag: 'a', contains: 'Reset').click()
        .elementByCss('body.password-new input[name=email]').type(email)
        .elementByCss('[type=submit]').click()

    shouldShowResetPasswordForm: ->
      @
        .url().should.eventually.match(/\/reset-password\//)
        .elementByCssOrNull('[name=password]').should.eventually.exist
        .elementByCssOrNull('[name=password2]').should.eventually.exist

    fillPasswordsAndClickSubmit: (password1, password2) ->
      @
        .elementByCss('[name=password]').type(password1)
        .elementByCss('[name=password2]').type(password2)
        .elementByCss('input.btn-primary').click()

    tryLogIn: (email, password) ->
      @waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(email)
        .elementByCss('.session-form [name=password]').type(password)
        .elementByCss('.session-form [type=submit]').click()

    logOut: ->
      @elementByXPath("//a[@href='/logout']").click()
        .waitForElementByCss('.session-form')

  asUser.usingTemporaryUser('ResetPassword')

  # We'll start this test suite logged out
  before -> @userBrowser.logOut()

  getUserDataElement = (context) ->
    context.adminBrowser
      .refresh()
      .waitForElementBy(tag: 'tr', contains: context.userEmail)
        .elementByCss('>', 'td.confirmed-at')

  describe 'When the user tries to reset a password', ->
    beforeEach ->
      @userBrowser
        .get(Url.login)
        .startResetPassword(@userEmail)

    it 'should alert the user to check his or her email', ->
      @userBrowser
        .waitForElementByCss('.alert-success')
          .text().should.eventually.contain('sent')

    it 'should do the same thing even on a second attempt', ->
      @userBrowser
        .get(Url.login)
        .startResetPassword(@userEmail)
        .waitForElementByCss('.alert-success')
          .text().should.eventually.contain('sent')

    describe 'when the user clicks the token from the email', ->
      beforeEach ->
        getUserDataElement(@)
          .getAttribute('data-reset-password-token')
          .then (token) =>
            @userBrowser.get(Url.resetPassword(token))

      it 'should show a reset-password form', ->
        @userBrowser
          .shouldShowResetPasswordForm()

      it 'should not click through if the passwords are not set', ->
        @userBrowser
          .fillPasswordsAndClickSubmit('', '')
          .shouldShowResetPasswordForm()

      it 'should not click through if the passwords do not match', ->
        @userBrowser
          .fillPasswordsAndClickSubmit(newPassword, @userEmail)
          .shouldShowResetPasswordForm()

      it 'should not click through if the password is too short', ->
        @userBrowser
          .fillPasswordsAndClickSubmit('short', 'short')
          .shouldShowResetPasswordForm()

      it 'should flash success, log you in and reset the password', ->
        @userBrowser
          .fillPasswordsAndClickSubmit(newPassword, newPassword)
          .elementByCss('.alert-success').text().should.eventually.contain('You have updated your password')
          .shouldBeLoggedInAs(@userEmail)
          .logOut()
          .tryLogIn(@userEmail, @userEmail).shouldBeLoggedOut() # old password doesn't work
          .tryLogIn(@userEmail, newPassword).shouldBeLoggedInAs(@userEmail) # new one does
          .logOut()
