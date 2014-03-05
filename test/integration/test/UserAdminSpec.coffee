expect = require('chai').expect
browser = require('../lib/browser')
Faker = require('Faker')

Url =
  index: '/admin/users'

describe 'UserAdmin', ->
  @timeout(5000)
  adminBrowser = null

  before ->
    adminBrowser = browser.create()
      .get(Url.index) # log in. XXX make this more generic
      .elementByCss('.session-form [name=email]').type(browser.adminLogin.email)
      .elementByCss('.session-form [name=password]').type(browser.adminLogin.password)
      .elementByCss('.session-form [type=submit]').click()

  after ->
    adminBrowser.quit().done()

  describe 'the index', ->
    beforeEach ->
      # The index loads asynchronously. We need to wait for the table contents.
      adminBrowser
        .get(Url.index)
        .waitForElementByCss('table.users tbody tr')

    describe 'creating a new user', ->
      randomEmail = null

      beforeEach ->
        randomEmail = Faker.Internet.email()

        adminBrowser
          .elementByCss('.new-user input[name=email]').type(randomEmail)
          .elementByCss('.new-user input[name=password]').type('PhilNettOk7')
          .elementByCss('.new-user input[type=submit]').click()

      it 'should show the new user', ->
        adminBrowser
          .elementByCss('table.users tbody').text().should.eventually.contain(randomEmail)
          .elementByXPath("//tr[contains(td[@class='email'], '#{randomEmail}')]//a[@class='delete']").click()
          .acceptAlert()
