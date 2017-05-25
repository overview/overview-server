browser = require('../lib/BrowserBuilder')
faker = require('faker')
testMethods = require('../support/testMethods')

Url =
  index: '/admin/users'
  login: '/login'

userToTrXPath = (email) -> "//tr[contains(td[@class='email'], '#{email}')]"

describe 'UserAdmin', ->
  before ->
    @adminSession = browser.createUserAdminSession()

    @adminBrowser = browser.createBrowser()
    @adminBrowser.loadShortcuts('jquery')

    @adminBrowser.shortcuts.userAdmin = ((browser) ->
      waitForUserLoaded: (email) ->
        browser
          .assertExists(xpath: userToTrXPath(email), wait: 'pageLoad')

      deleteUser: (email) ->
        browser
          .assertExists(xpath: userToTrXPath(email), wait: 'fast')
          .shortcuts.jquery.listenForAjaxComplete()
          .click(xpath: "#{userToTrXPath(email)}//a[@class='delete']")
          .alert().accept()
          .shortcuts.jquery.waitUntilAjaxComplete()
    )(@adminBrowser)

    @adminBrowser
      # log in. XXX make this more generic
      .get(Url.index)
      .sendKeys(@adminSession.options.login.email, css: '.session-form [name=email]')
      .sendKeys(@adminSession.options.login.password, css: '.session-form [name=password]')
      .click(css: '.session-form [type=submit]')
      .shortcuts.userAdmin.waitForUserLoaded(browser.adminLogin.email)

  after ->
    @adminBrowser.call => @adminBrowser.driver.manage().deleteAllCookies()
      .close()

  describe 'index', ->
    describe 'creating a new user', ->
      userEmail = null
      userPassword = 'PhilNettOk7'
      trXPath = null # XPath string like //tr[...], selecting the added user

      beforeEach ->
        userEmail = faker.internet.email()
        trXPath = "//tr[contains(td[@class='email'], '#{userEmail}')]"

        @adminBrowser
          .sendKeys(userEmail, css: '.new-user input[name=email]')
          .sendKeys(userPassword, css: '.new-user input[name=password]')
          .click(css: '.new-user input[type=submit]')

      it 'should show the user', ->
        @adminBrowser
          .shortcuts.userAdmin.waitForUserLoaded(userEmail)
          .shortcuts.userAdmin.deleteUser(userEmail)

      it 'should delete the user', ->
        @adminBrowser
          .shortcuts.userAdmin.waitForUserLoaded(userEmail)
          .shortcuts.userAdmin.deleteUser(userEmail)
          .assertNotExists(xpath: trXPath)
          .get(Url.index) # refresh
          .assertExists(css: 'table.users tbody tr', wait: 'pageLoad')
          .assertNotExists(xpath: trXPath)

      it 'should promote and demote the user', ->
        @adminBrowser
          .shortcuts.userAdmin.waitForUserLoaded(userEmail)
          .assertExists(xpath: "#{trXPath}//td[@class='is-admin'][contains(.,'no')]")
          .shortcuts.jquery.listenForAjaxComplete()
          .click(xpath: "#{trXPath}//a[@class='promote']")
          .shortcuts.jquery.waitUntilAjaxComplete()
          .assertExists(xpath: "#{trXPath}//td[@class='is-admin'][contains(.,'yes')]")
          .get(Url.index) # refresh
          .shortcuts.userAdmin.waitForUserLoaded(userEmail)
          .assertExists(xpath: "#{trXPath}//td[@class='is-admin'][contains(.,'yes')]")
          .shortcuts.jquery.listenForAjaxComplete()
          .click(xpath: "#{trXPath}//a[@class='demote']")
          .shortcuts.jquery.waitUntilAjaxComplete()
          .assertExists(xpath: "#{trXPath}//td[@class='is-admin'][contains(.,'no')]")
          .get(Url.index) # refresh
          .shortcuts.userAdmin.waitForUserLoaded(userEmail)
          .assertExists(xpath: "#{trXPath}//td[@class='is-admin'][contains(.,'no')]")
          .shortcuts.userAdmin.deleteUser(userEmail)

      it 'should create a user who can log in', ->
        b = browser.createBrowser()
        b
          .get(Url.login)
          .sendKeys(userEmail, css: '.session-form [name=email]')
          .sendKeys(userPassword, css: '.session-form [name=password]')
          .click(css: '.session-form [type=submit]')
          .assertExists(tag: 'h1', contains: 'Example document sets', wait: 'pageLoad')
          .call -> b.driver.manage().deleteAllCookies()
          .close()
          .then =>
            @adminBrowser
              .shortcuts.userAdmin.deleteUser(userEmail)
