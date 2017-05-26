'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('LoggedOutModal', function() {
  asUserWithDocumentSet('CsvUpload/basic.csv', function() {
    before(function() {
      this.browser.loadShortcuts('jquery')
      this.b = this.browser
      this.jquery = this.browser.shortcuts.jquery
    })

    // These tests tell a story: they must be executed sequentially.

    it('should show a modal when a logged out tries to perform an ajax action', async function() {
      await this.b.driver.manage().deleteAllCookies()

      await this.jquery.listenForAjaxComplete()
      await this.b.sendKeys('test\uE007', 'input[name=query]')
      await this.jquery.waitUntilAjaxComplete()

      await this.b.assertExists('#logged-out-modal')
    })

    it('should redirect to the login page on clicking the login button', async function() {
      await this.b.click({ tag: 'button', contains: 'Log back in' })
      await this.b.assertExists('form[action="/login"]', { wait: 'pageLoad' })
    })

    // we need to log back in so that asUserWithDocumentSet's
    // afterAll hook will pass, so we might as well test the redirect too.
    it('logging back in should redirect to document set', async function() {
      await this.b.sendKeys(this.userEmail, '.session-form [name=email]')
      await this.b.sendKeys(this.userEmail, '.session-form [name=password]')
      await this.b.click('.session-form [type=submit]')
      await this.b.assertExists('#document-list-title', { wait: 'pageLoad' })
    })
  })
})
