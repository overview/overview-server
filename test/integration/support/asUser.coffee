browser = require('../lib/BrowserBuilder')

Url =
  adminUserIndex: '/admin/users'
  deleteUser: (email) -> "/admin/users/#{encodeURIComponent(email)}?X-HTTP-Method-Override=DELETE"
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
  #
  # You may also pass an "options" parameter. It's an Object with any of:
  #
  # * before: block called once the variables are set up.
  # * after: block called before the variables are torn down.
  # * title: title of the block (very useful for debugging).
  # * adminBrowser: if true, lets tests use @adminBrowser.
  usingTemporaryUser: (options={}) ->
    title = options.title || 'usingTemporaryUser'

    before ->
      @adminSession = browser.createUserAdminSession(title)

      @adminSession.createTemporaryUser()
        .then (user) =>
          @userEmail = user.email
          @userBrowser = userBrowser = browser.create("#{title} - #{@userEmail}")
            .get(Url.login)
            .waitForElementByCss('.session-form')
            .elementByCss('.session-form [name=email]').type(@userEmail)
            .elementByCss('.session-form [name=password]').type(@userEmail)
            .elementByCss('.session-form [type=submit]').click()
        .then =>
          if options.adminBrowser
            @adminBrowser = browser.create("#{title} - #{browser.adminLogin.email}")

            @adminBrowser
              .get(Url.login)
              .waitForElementByCss('.session-form')
              .elementByCss('.session-form [name=email]').type(browser.adminLogin.email)
              .elementByCss('.session-form [name=password]').type(browser.adminLogin.password)
              .elementByCss('.session-form [type=submit]').click()
          else
            null
        .then => options.before?.apply(@)

    after ->
      Q(options.after?.apply(@)) # promise or undefined -- both work
        .then => @adminSession.deleteUser(email: @userEmail).then(=> @adminSession = null)
        .then => @userBrowser.deleteAllCookies().quit().then(=> @userBrowser = null)
        .then => Q(@adminBrowser?.deleteAllCookies()?.quit()).then(=> @adminBrowser = null)
