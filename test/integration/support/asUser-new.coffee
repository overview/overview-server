browser = require('../lib/browser')

module.exports =
  usingTemporaryUser: (body) ->
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
    # Tests should make use of @browser and @userEmail. The user's password
    # is @userEmail.
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
