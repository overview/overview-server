testMethods = require('../support/testMethods')
asUser = require('../support/asUser')

# In order to let users store their own information
# We need to let users identify themselves
describe 'Login', ->
  testMethods.usingPromiseChainMethods
    shouldBeLoggedInAs: (email) ->
      @elementByCss('.logged-in strong').text().should.eventually.equal(email)

    shouldHaveFailedToLogIn: ->
      @
        .url().should.eventually.match(/\/login\b/)
        .elementByCss('.session-form').text().should.eventually.contain('Wrong email address or password')

    shouldBeLoggedOut: ->
      @elementByCss('.session-form').should.eventually.exist

    tryLogIn: (email, password) ->
      @waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(email)
        .elementByCss('.session-form [name=password]').type(password)
        .elementByCss('.session-form [type=submit]').click()

    logOut: ->
      @elementByXPath("//a[@href='/logout']").click()
        .waitForElementByCss('.session-form')

  asUser.usingTemporaryUser('Login')

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

  it 'should not log in with wrong password', ->
    @userBrowser
      .tryLogIn(@userEmail, 'bad-password')
      .shouldHaveFailedToLogIn()

  it 'should not log in with the wrong username', ->
    @userBrowser
      .tryLogIn('x-' + @userEmail, @userEmail)
      .shouldHaveFailedToLogIn()
