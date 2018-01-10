'use strict'

const browser = require('../lib/BrowserBuilder')
const faker = require('faker')

const Url = {
  index: '/admin/users',
  login: '/login',
}

const userToTrXPath = (email) => `//tr[contains(td[@class='email'], '${email}')]`

class UserAdminShortcuts {
  constructor(browser) {
    this.b = browser

    browser.loadShortcuts('jquery')
    this.jquery = browser.shortcuts.jquery
  }

  async waitForUserLoaded(email) {
    await this.b.assertExists({ xpath: userToTrXPath(email), wait: 'pageLoad' })
  }

  async deleteUser(email) {
    await this.b.assertExists({ xpath: userToTrXPath(email), wait: 'fast' })
    await this.jquery.listenForAjaxComplete()
    await this.b.click({ xpath: `${userToTrXPath(email)}//a[@class='delete']` })
    await this.b.alert().accept()
    await this.jquery.waitUntilAjaxComplete()
  }
}

describe('UserAdmin', function() {
  beforeEach(async function() {
    this.adminSession = await browser.createUserAdminSession()
    this.adminBrowser = await browser.createBrowser()
    this.adminBrowser.loadShortcuts('jquery')
    this.userAdmin = new UserAdminShortcuts(this.adminBrowser)
    this.jquery = this.adminBrowser.shortcuts.jquery

    // log in. XXX make this more generic
    await this.adminBrowser.get(Url.index)
    await this.adminBrowser.sendKeys(this.adminSession.options.login.email, '.session-form [name=email]')
    await this.adminBrowser.sendKeys(this.adminSession.options.login.password, '.session-form [name=password]')
    await this.adminBrowser.click('.session-form [type=submit]')
    await this.userAdmin.waitForUserLoaded(browser.adminLogin.email)
  })

  describe('index', function() {
    describe('creating a new user', function() {
      let userEmail = null
      let userPassword = 'PhilNettOk7'
      let trXPath = null // XPath string like //tr[...], selecting the added user

      beforeEach(async function() {
        userEmail = faker.internet.email()
        trXPath = `//tr[contains(td[@class='email'], '${userEmail}')]`

        await this.adminBrowser.sendKeys(userEmail, '.new-user input[name=email]')
        await this.adminBrowser.sendKeys(userPassword, '.new-user input[name=password]')
        await this.adminBrowser.click('.new-user input[type=submit]')
      })

      it('should show the user', async function() {
        await this.userAdmin.waitForUserLoaded(userEmail)
        await this.userAdmin.deleteUser(userEmail)
      })

      it('should delete the user', async function() {
        await this.userAdmin.waitForUserLoaded(userEmail)
        await this.userAdmin.deleteUser(userEmail)
        await this.adminBrowser.assertNotExists({ xpath: trXPath })
        await this.adminBrowser.get(Url.index) // refresh
        await this.adminBrowser.assertExists('table.users tbody tr', { wait: 'pageLoad' })
        await this.adminBrowser.assertNotExists({ xpath: trXPath })
      })

      it('should promote and demote the user', async function() {
        const b = this.adminBrowser

        // Load. User is not admin
        await this.userAdmin.waitForUserLoaded(userEmail)
        await b.assertExists({ xpath: `${trXPath}//td[@class='is-admin'][contains(.,'no')]` })

        // Promose user. User is admin
        await this.jquery.listenForAjaxComplete()
        await b.click({ xpath: `${trXPath}//a[@class='promote']` })
        await this.jquery.waitUntilAjaxComplete()
        await b.assertExists({ xpath: `${trXPath}//td[@class='is-admin'][contains(.,'yes')]` })

        // Refresh. User is still admin.
        await b.get(Url.index)
        await this.userAdmin.waitForUserLoaded(userEmail)
        await b.assertExists({ xpath: `${trXPath}//td[@class='is-admin'][contains(.,'yes')]` })

        // Demote. User is not admin.
        await this.jquery.listenForAjaxComplete()
        await b.click({ xpath: `${trXPath}//a[@class='demote']` })
        await this.jquery.waitUntilAjaxComplete()
        await b.assertExists({ xpath: `${trXPath}//td[@class='is-admin'][contains(.,'no')]` })

        // Refresh. User is still not admin.
        await b.get(Url.index)
        await this.userAdmin.waitForUserLoaded(userEmail)
        await b.assertExists({ xpath: `${trXPath}//td[@class='is-admin'][contains(.,'no')]` })

        // delete the user (we created it in beforeEach())
        await this.userAdmin.deleteUser(userEmail)
      })

      it('should create a user who can log in', async function() {
        const b = await browser.createBrowser()

        await b.get(Url.login)
        await b.sendKeys(userEmail, '.session-form [name=email]')
        await b.sendKeys(userPassword, '.session-form [name=password]')
        await b.click('.session-form [type=submit]')
        await b.assertExists({ tag: 'h1', contains: 'Example document sets', wait: 'pageLoad' })
        await b.close()

        await this.userAdmin.deleteUser(userEmail)
      })
    })
  })
})
