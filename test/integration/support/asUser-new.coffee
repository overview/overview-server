browser = require('../lib/BrowserBuilder')

module.exports =
  # Returns a browser logged in as the administrator.
  #
  # Usage:
  #
  #     describe 'my test suite', ->
  #       asUser.usingTemporaryUser ->
  #         asUser.usingAdminBrowser ->
  #           it 'should be logged in', ->
  #             @browser.find(css: '.logged-in').getText().should.eventually.eq(@userEmail)
  #
  #           it 'should have admin browser', ->
  #             @adminBrowser.find(css: '.logged-in').getText().should.eventually.eq(@adminEmail)
  #
  # Tests can make use of @adminBrowser and @adminEmail.
  usingAdminBrowser: (body) ->
    describe 'with an administrator logged in', ->
      before ->
        if !@adminSession?
          throw new Error("You must call usingAdminBrowser() within a usingTemporaryUser() block")

        @adminEmail = @adminSession.options.login.email
        @adminBrowser = adminBrowser = browser.createBrowser()
        @adminBrowser
          .get('/login')
          .sendKeys(@adminEmail, css: '.session-form [name=email]')
          .sendKeys(@adminSession.options.login.password, css: '.session-form [name=password]')
          .click(css: '.session-form [type=submit]')

      after ->
        @adminBrowser.driver.manage().deleteAllCookies()
        @adminBrowser.close()

      body()

  # Registers a temporary user and logs in as that user for the duration of
  # this describe block.
  #
  # Usage:
  #
  #     describe 'my test suite', ->
  #       asUser.usingTemporaryUser ->
  #         it 'should have an email', ->
  #           @userEmail.should.not.be.null
  #
  #         it 'should have a browser', ->
  #           @browser.should.not.be.null
  #
  #         it 'should be logged in', ->
  #           @browser.find(css: '.logged-in').getText().should.eventually.eq(@userEmail)
  #
  # Tests should make use of @browser and @userEmail. The password and email are
  # the same.
  usingTemporaryUser: (body) ->
    describe 'with a temporary user', ->
      before ->
        @adminSession = browser.createUserAdminSession('asUser.usingTemporaryUser')
        @adminSession.createTemporaryUser()
          .then (user) =>
            @userEmail = user.email
            @browser = userBrowser = browser.createBrowser()
            @browser
              .get('/login')
              .sendKeys(@userEmail, css: '.session-form [name=email]')
              .sendKeys(@userEmail, css: '.session-form [name=password]')
              .click(css: '.session-form [type=submit]')

      after ->
        @adminSession.deleteUser(email: @userEmail)
          .then =>
            @browser.driver.manage().deleteAllCookies()
            @browser.close()

      body()
