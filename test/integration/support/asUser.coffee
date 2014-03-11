browser = require('../lib/browser')
Faker = require('Faker')

Url =
  adminUserIndex: '/admin/users'
  login: '/login'

module.exports =
  # Registers a temporary user and logs in as that user for the duration of
  # this describe block.
  #
  # Usage:
  #
  #     describe 'my test suite', ->
  #       usingTemporaryUser()
  #
  #       it 'should have an email', ->
  #         @userEmail.should.not.be.null
  #
  #       it 'should have a userBrowser', ->
  #         @userBrowser.should.not.be.null
  #
  #       it 'should be logged in', ->
  #         @userBrowser.elementByCss('.logged-in').text().should.eventually.equal(@userEmail)
  #
  # Tests should make use of @userBrowser and @userEmail. The user's password
  # is always equal to @userEmail.
  usingTemporaryUser: ->
    adminBrowser = null

    before ->
      @userEmail = Faker.Internet.email()
      @userBrowser = userBrowser = browser.create()
        .get(Url.login)
        .waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(@userEmail)
        .elementByCss('.session-form [name=password]').type(@userEmail)
        # Don't click yet: wait for the user to be created

      adminBrowser = browser.create()

      adminBrowser
        .get(Url.adminUserIndex)
        .waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(browser.adminLogin.email)
        .elementByCss('.session-form [name=password]').type(browser.adminLogin.password)
        .elementByCss('.session-form [type=submit]').click()
        .waitForElementByCss('.new-user form')
        .elementByCss('.new-user [name=email]').type(@userEmail)
        .elementByCss('.new-user [name=password]').type(@userEmail)
        .listenForJqueryAjaxComplete()
        .elementByCss('.new-user [type=submit]').click()
        .waitForJqueryAjaxComplete()
        .then ->
          userBrowser.elementByCss('.session-form [type=submit]').click()

    after ->
      adminBrowser
        .listenForJqueryAjaxComplete()
        .elementByXPath("//tr[contains(td[@class='email'], '#{@userEmail}')]//a[@class='delete']").click()
        .waitForJqueryAjaxComplete()
        .quit()
