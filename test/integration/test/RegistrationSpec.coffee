faker = require('faker')
browser = require('../lib/BrowserBuilder')

Url =
  login: '/login'
  confirm: (token) -> "/confirm/#{token}"

describe 'Registration', ->
  before ->
    @userEmail = faker.internet.email()
    @validPassword = 'icrucGofbap4'

    @browser = browser.createBrowser()
    @adminSession = browser.createUserAdminSession('Registration')

    @browser.shortcuts.registration = ((browser) ->
      tryLogIn: (email, password) ->
        browser
          .assertExists(class: 'session-form', wait: 'pageLoad')
          .sendKeys(email, css: '.session-form [name=email]')
          .sendKeys(password, css: '.session-form [name=password]')
          .click(css: '.session-form [type=submit]')

      tryRegister: (email, password, password2) ->
        browser
          .assertExists(class: 'user-form', wait: 'pageLoad')
          .sendKeys(email, css: '.user-form [name=email]')
          .sendKeys(password, css: '.user-form [name=password]')
          .sendKeys(password2, css: '.user-form [name=password2]')
          .click(css: '.user-form [type=submit]')

      assertLoggedInAs: (email) ->
        browser
          .assertExists(tag: 'div', class: 'logged-in', contains: email, wait: 'pageLoad')

      assertLogInFailed: ->
        browser
          .assertExists(tag: 'h1', contains: 'Log in', wait: 'pageLoad')
          .assertExists(class: 'error', contains: 'Wrong email address or password')

      logOut: ->
        browser
          .click(link: 'Log out')
          .assertExists(class: 'session-form', wait: 'pageLoad')
    )(@browser)


  after ->
    @browser
      .call => @browser.driver.manage().deleteAllCookies()
      .close()

  it 'should not register a bad email format', ->
    @browser
      .get(Url.login)
      .shortcuts.registration.tryRegister('@' + @userEmail, @validPassword, @validPassword)
      .assertExists(css: '.user-form [name=email]:invalid')

  it 'should not register a weak password', ->
    @browser
      .get(Url.login)
      .shortcuts.registration.tryRegister(@userEmail, 'weak', 'weak')
      .assertExists(css: '.user-form .control-group.error [name=password]')
      .assertExists(css: '.user-form .control-group.error p.help-block')

  it 'should not register mismatched passwords', ->
    @browser
      .get(Url.login)
      .shortcuts.registration.tryRegister(@userEmail, @validPassword, @validPassword + '1')
      .assertExists(css: '.user-form .control-group.error [name=password2]')
      .assertExists(css: '.user-form .control-group.error p.help-block')

  it 'should show an error when clicking an invalid token link', ->
    @browser
      .get(Url.confirm('abcdef'))
      .assertExists(tag: 'h1', contains: 'Broken confirmation link')
      .assertExists(css: 'a[href="/reset-password"]')

  describe 'when the user already exists', ->
    before -> @adminSession.createUser(email: @userEmail, password: @validPassword)
    after -> @adminSession.deleteUser(email: @userEmail)

    before ->
      @browser
        .get(Url.login)
        .shortcuts.registration.tryRegister(@userEmail, @validPassword + '1', @validPassword + '1')

    it 'should prompt the user to check his or her email', ->
      @browser
        .assertExists(tag: 'h1', contains: 'Check your email', wait: 'pageLoad')

    it 'should not change the password', ->
      # This would be a huge security hole!
      @browser
        .get(Url.login)
        .shortcuts.registration.tryLogIn(@userEmail, @validPassword)
        .shortcuts.registration.assertLoggedInAs(@userEmail)
        .shortcuts.registration.logOut()
        .shortcuts.registration.tryLogIn(@userEmail, @validPassword + '1')
        .shortcuts.registration.assertLogInFailed()

    it 'should not give the user a confirmation token', ->
      @adminSession
        .showUser(email: @userEmail)
        .then((u) -> JSON.parse(u).confirmation_token).should.eventually.not.exist

  describe 'when the user tries to register', ->
    before ->
      @getConfirmationToken = =>
        @browser.then => # flush commands
          @adminSession
            .showUser(email: @userEmail)
            .then((u) -> JSON.parse(u).confirmation_token)

    beforeEach ->
      @browser
        .get(Url.login)
        .shortcuts.registration.tryRegister(@userEmail, @validPassword, @validPassword)

    afterEach ->
      @adminSession.deleteUser(email: @userEmail)

    it 'should prompt the user to check his or her email', ->
      @browser
        .assertExists(tag: 'h1', contains: 'Check your email', wait: 'pageLoad')

    it 'should set a confirmation token on the user', ->
      @getConfirmationToken().should.eventually.match(/\w+/)

    it 'should not let the user log in before confirming', ->
      @browser
        .get('/login')
        .shortcuts.registration.tryLogIn(@userEmail, @validPassword)
        .assertExists(class: 'error', contains: 'click the link in the confirmation email', wait: 'pageLoad')

    it 'should recognize the user if the password is incorrect', ->
      @browser
        .get('/login')
        .shortcuts.registration.tryLogIn(@userEmail, @validPassword + '1')
        .shortcuts.registration.assertLogInFailed()

    describe 'after clicking the confirmation link', ->
      beforeEach ->
        @getConfirmationToken().then (token) =>
          @browser.get(Url.confirm(token))

      afterEach ->
        @browser.shortcuts.registration.logOut()

      it 'should log you in and tell you all is well', ->
        @browser
          .shortcuts.registration.assertLoggedInAs(@userEmail)
          .assertExists(class: 'alert-success', contains: 'Welcome')

      it 'should disable the confirmation token', ->
        @getConfirmationToken().should.eventually.not.exist
