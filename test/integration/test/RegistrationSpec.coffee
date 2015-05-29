testMethods = require('../support/testMethods')
Q = require('q')
faker = require('faker')
browser = require('../lib/BrowserBuilder')

Url =
  login: '/login'
  confirm: (token) -> "/confirm/#{token}"

describe 'Registration', ->
  testMethods.usingPromiseChainMethods
    tryLogIn: (email, password) ->
      @waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(email)
        .elementByCss('.session-form [name=password]').type(password)
        .elementByCss('.session-form [type=submit]').click()

    tryRegister: (email, password, password2) ->
      @waitForElementByCss('.user-form')
        .elementByCss('.user-form [name=email]').type(email)
        .elementByCss('.user-form [name=password]').type(password)
        .elementByCss('.user-form [name=password2]').type(password2)
        .elementByCss('.user-form [type=submit]').click()

    shouldBeLoggedInAs: (email) ->
      @elementByCss('.logged-in strong').text().should.eventually.equal(email)

    shouldHaveFailedToLogIn: ->
      @
        .url().should.eventually.match(/\/login\b/)
        .elementByCss('.session-form').text().should.eventually.contain('Wrong email address or password')

    logOut: ->
      @elementByXPath("//a[@href='/logout']").click()
        .waitForElementByCss('.session-form')

  before ->
    @userEmail = faker.internet.email()
    @validPassword = 'icrucGofbap4'

    @userBrowser = browser.create('Registration - user')
    @adminSession = browser.createUserAdminSession('Registration')

  after -> @userBrowser.deleteAllCookies().quit()

  it 'should not register a bad email format', ->
    @userBrowser
      .get(Url.login)
      .tryRegister('@' + @userEmail, @validPassword, @validPassword)
      .elementByCss('.user-form [name=email]:invalid').should.eventually.exist

  it 'should not register a weak password', ->
    @userBrowser
      .get(Url.login)
      .tryRegister(@userEmail, 'weak', 'weak')
      .elementByCss('.user-form .control-group.error [name=password]').should.eventually.exist
      .elementByCss('.user-form .control-group.error p.help-block').should.eventually.exist

  it 'should not register mismatched passwords', ->
    @userBrowser
      .get(Url.login)
      .tryRegister(@userEmail, @validPassword, @validPassword + '1')
      .elementByCss('.user-form .control-group.error [name=password2]').should.eventually.exist
      .elementByCss('.user-form .control-group.error p.help-block').should.eventually.exist

  it 'should show an error when clicking an invalid token link', ->
    @userBrowser
      .get(Url.confirm('abcdef'))
      .elementBy(tag: 'h1', contains: 'Broken confirmation link').should.eventually.exist
      .elementByCss('a[href="/reset-password"]').should.eventually.exist

  describe 'when the user already exists', ->
    before -> @adminSession.createUser(email: @userEmail, password: @validPassword)
    after -> @adminSession.deleteUser(email: @userEmail)

    before ->
      @userBrowser
        .get(Url.login)
        .tryRegister(@userEmail, @validPassword + '1', @validPassword + '1')

    it 'should prompt the user to check his or her email', ->
      @userBrowser
        .elementBy(tag: 'h1', contains: 'Check your email').should.eventually.exist

    it 'should not change the password', ->
      # This would be a huge security hole!
      @userBrowser
        .get(Url.login)
        .tryLogIn(@userEmail, @validPassword)
        .shouldBeLoggedInAs(@userEmail)
        .logOut()
        .tryLogIn(@userEmail, @validPassword + '1')
        .shouldHaveFailedToLogIn()

    it 'should not give the user a confirmation token', ->
      @adminSession
        .showUser(email: @userEmail)
        .then((u) -> JSON.parse(u).confirmation_token).should.eventually.not.exist

  describe 'when the user tries to register', ->
    before ->
      @getConfirmationToken = =>
        @adminSession
          .showUser(email: @userEmail)
          .then((u) -> JSON.parse(u).confirmation_token)

    beforeEach ->
      @userBrowser
        .get(Url.login)
        .tryRegister(@userEmail, @validPassword, @validPassword)

    afterEach ->
      @adminSession.deleteUser(email: @userEmail)

    it 'should prompt the user to check his or her email', ->
      @userBrowser
        .elementBy(tag: 'h1', contains: 'Check your email').should.eventually.exist

    it 'should set a confirmation token on the user', ->
      @getConfirmationToken().should.eventually.match(/\w+/)

    it 'should not let the user log in before confirming', ->
      @userBrowser
        .get('/login')
        .tryLogIn(@userEmail, @validPassword)
        .elementByCss('.control-group.error .help-block').text().should.eventually.contain('click the link in the confirmation email')

    it 'should recognize the user if the password is incorrect', ->
      @userBrowser
        .get('/login')
        .tryLogIn(@userEmail, @validPassword + '1')
        .elementByCss('.control-group.error .help-block').text().should.eventually.contain('Wrong email address or password')

    describe 'after clicking the confirmation link', ->
      beforeEach ->
        @getConfirmationToken().then (token) =>
          @userBrowser.get(Url.confirm(token))

      it 'should log you in and tell you all is well', ->
        @userBrowser
          .shouldBeLoggedInAs(@userEmail)
          .elementBy(class: 'alert-success', contains: 'Welcome').should.eventually.exist

      it 'should disable the confirmation token', ->
        @getConfirmationToken().should.eventually.not.exist
