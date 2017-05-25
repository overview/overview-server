testMethods = require('../support/testMethods')
asUser = require('../support/asUser-new')

# In order to let users store their own information
# We need to let users identify themselves
describe 'Login', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser.shortcuts.login = ((browser) ->
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

      # We'll start this test suite logged out.
      @browser.shortcuts.login.logOut()

    it 'should log in', ->
      @browser
        .shortcuts.login.tryLogIn(@userEmail, @userEmail)
        .shortcuts.login.assertLoggedInAs(@userEmail)
        .shortcuts.login.logOut()

    it 'should log out', ->
      # This is easy -- we logged out in the before hook ;)
      @browser
        .shortcuts.login.assertLoggedOut()

    it 'should not log in with wrong password', ->
      @browser
        .shortcuts.login.tryLogIn(@userEmail, 'bad-password')
        .shortcuts.login.assertLogInFailed()

    it 'should not log in with the wrong username', ->
      @browser
        .shortcuts.login.tryLogIn("x-#{@userEmail}", @userEmail)
        .shortcuts.login.assertLogInFailed()
