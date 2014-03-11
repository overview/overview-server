testMethods = require('../support/testMethods')
asUser = require('../support/asUser')

Url =
  login: '/login'
  welcome: '/'
  documentsets: '/documentsets'

describe.only 'Login', ->
  testMethods.usingPromiseChainMethods
    shouldBeLoggedInAs: (email) ->
      @elementByCss('.logged-in strong').text().should.eventually.equal(email)

    shouldBeLoggedOut: () ->
      @elementByCss('.session-form').should.eventually.exist

    tryLogIn: (email, password) ->
      @waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(email)
        .elementByCss('.session-form [name=password]').type(password)
        .elementByCss('.session-form [type=submit]').click()

    logOut: ->
      @elementByXPath("//a[@href='/logout']").click()
        .waitForElementByCss('.session-form')

  asUser.usingTemporaryUser()

  before ->
    # We'll start this test suite logged out.
    @userBrowser.logOut()

  it 'should log in', ->
    @userBrowser
      .tryLogIn(@userEmail, @userEmail)
      .waitForElementByCss('.logged-in')
      .shouldBeLoggedInAs(@userEmail)
      .logOut()

  it 'should log out', ->
    # This is easy -- we did this in the before hook ;)
    @userBrowser
      .shouldBeLoggedOut()
