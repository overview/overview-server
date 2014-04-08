testMethods = require('../support/testMethods')
Q = require('q')
Faker = require('Faker')
browser = require('../lib/browser')

Url =
  login: '/login'
  confirm: (token) -> "/confirm?token=#{token}"
  deleteUser: (email) -> "/admin/users/#{encodeURIComponent(email)}?X-HTTP-Method-Override=DELETE"
  adminUserIndex: '/admin/users'

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

    tryConfirm: (token) ->
      @
        .elementByCss('input[name=token]').type(token)
        .elementByCss('input[type=submit]').click()

    createUser: (email, password) ->
      @
        .get(Url.adminUserIndex)
        .waitForElementByCss('.new-user form')
        .elementByCss('.new-user [name=email]').type(email)
        .elementByCss('.new-user [name=password]').type(password)
        .listenForJqueryAjaxComplete()
        .elementByCss('.new-user [type=submit]').click()
        .waitForJqueryAjaxComplete()

    deleteUser: (email) ->
      @
        .get(Url.deleteUser(email))

    shouldBeLoggedInAs: (email) ->
      @elementByCss('.logged-in strong').text().should.eventually.equal(email)

    shouldHaveFailedToLogIn: ->
      @
        .url().should.eventually.match(/\/login\b/)
        .elementByCss('.session-form').text().should.eventually.contain('Wrong email address or password')

    shouldBeLoggedOut: ->
      @elementByCss('.session-form').should.eventually.exist

    logOut: ->
      @elementByXPath("//a[@href='/logout']").click()
        .waitForElementByCss('.session-form')

  before ->
    @userEmail = Faker.Internet.email()
    @userBrowser = browser.create('Registration - user')
    @adminBrowser = browser.create('Registration - admin')
      .get(Url.adminUserIndex)
      .tryLogIn(browser.adminLogin.email, browser.adminLogin.password)
      # We don't actually want to do anything. We just keep this around for later.

  after ->
    Q.all([
      @userBrowser.deleteAllCookies().quit()
      @adminBrowser.deleteAllCookies().quit()
    ])

  before ->
    @validPassword = 'icrucGofbap4'

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

  it 'should show an error when entering an invalid token', ->
    @userBrowser
      .get(Url.confirm(''))
      .tryConfirm('abcdef')
      .elementByCss('.control-group.error input[name=token]').should.eventually.exist
      .elementByCss('.control-group.error p.help-block').should.eventually.exist

  it 'should show an error when clicking an invalid token link', ->
    @userBrowser
      .get(Url.confirm('abcdef'))
      .elementByCss('.control-group.error input[name=token]').should.eventually.exist
      .elementByCss('.control-group.error p.help-block').should.eventually.exist

  it 'should show an error when entering no token', ->
    @userBrowser
      .get(Url.confirm(''))
      .tryConfirm('')
      .elementByCss('input[name=token]:invalid').should.eventually.exist

  describe 'when the user already exists', ->
    before -> @adminBrowser.createUser(@userEmail, @userEmail)
    after -> @adminBrowser.deleteUser(@userEmail)

    before ->
      @userBrowser
        .get(Url.login)
        .tryRegister(@userEmail, @validPassword, @validPassword)

    it 'should prompt the user to check his or her email', ->
      @userBrowser
        .elementBy(class: 'alert-success', contains: 'please check your email').should.eventually.exist

    it 'should not change the password', ->
      # This would be a huge security hole!
      @userBrowser
        .get(Url.login)
        .tryLogIn(@userEmail, @userEmail)
        .shouldBeLoggedInAs(@userEmail)
        .logOut()
        .tryLogIn(@userEmail, @validPassword)
        .shouldHaveFailedToLogIn()

    it 'should not give the user a confirmation token', ->
      @adminBrowser
        .get(Url.adminUserIndex)
        .waitForElementBy(tag: 'tr', contains: @userEmail)
        .elementByCss('>', 'td.confirmed-at').getAttribute('data-confirmation-token').should.eventually.not.exist

  describe 'when the user tries to register', ->
    beforeEach ->
      @userBrowser
        .get(Url.login)
        .tryRegister(@userEmail, @validPassword, @validPassword)

    afterEach ->
      @adminBrowser.deleteUser(@userEmail)

    beforeEach ->
      @getConfirmationToken = =>
        @adminBrowser
          .get(Url.adminUserIndex)
          .waitForElementBy(tag: 'tr', contains: @userEmail)
          .elementByCss('>', 'td.confirmed-at').getAttribute('data-confirmation-token')

    it 'should prompt the user to check his or her email', ->
      @userBrowser
        .elementBy(class: 'alert-success', contains: 'please check your email').should.eventually.exist

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

    describe 'after typing the token into the registration form', ->
      beforeEach ->
        @getConfirmationToken().then (token) =>
          @userBrowser.tryConfirm(token)

      it 'should log you in and tell you all is well', ->
        @userBrowser
          .shouldBeLoggedInAs(@userEmail)
          .elementBy(class: 'alert-success', contains: 'Welcome').should.eventually.exist

      it 'should disable the confirmation token', ->
        @getConfirmationToken().should.eventually.not.exist

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
