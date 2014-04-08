browser = require('../lib/browser')
Faker = require('Faker')

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
  # Tests may also make use of @adminBrowser. Note that this framework expects
  # @adminBrowser to stay on the same page, always, and it expects @userEmail
  # to exist after the test is complete.
  #
  # You may also pass an "options" parameter. It's an Object with any of:
  #
  # * before: block called once the variables are set up.
  # * after: block called before the variables are torn down.
  # * title: title of the block (very useful for debugging).
  usingTemporaryUser: (options={}) ->
    title = options.title || 'usingTemporaryUser'
    blockTitle = if options.title then "usingTemporaryUser - #{options.title}" else 'usingTemporaryUser'

    before blockTitle, ->
      @userEmail = Faker.Internet.email()
      @userBrowser = userBrowser = browser.create("#{title} - user")
        .get(Url.login)
        .waitForElementByCss('.session-form')
        .elementByCss('.session-form [name=email]').type(@userEmail)
        .elementByCss('.session-form [name=password]').type(@userEmail)
        # Don't click yet: wait for the user to be created

      @adminBrowser = browser.create("#{title} - admin")

      @adminBrowser
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
        .then =>
          options.before?.apply(@)

    after blockTitle, ->
      Q(options.after?.apply(@)) # promise or undefined -- both work
        .then =>
          Q.all([
            @adminBrowser
              .get(Url.deleteUser(@userEmail))
              .deleteAllCookies()
              .quit()
            @userBrowser
              .deleteAllCookies()
              .quit()
          ])
