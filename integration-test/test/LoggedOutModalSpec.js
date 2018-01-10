'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('LoggedOutModal', function() {
  asUserWithDocumentSet('CsvUpload/basic.csv', function() {
    beforeEach(async function() {
      this.browser.loadShortcuts('jquery')
      this.b = this.browser
      this.jquery = this.browser.shortcuts.jquery

      // Prepare for an HTTP request that will fail
      await this.b.click('nav .dropdown-toggle a')
      await this.b.click({ link: 'Edit Fields' })
      await this.b.click({ button: 'Add Field' })
      await this.b.driver.manage().deleteAllCookies()
      await this.b.sendKeys('test', '#metadata-schema-editor-app input[name=name]')
    })

    it('should show a modal when a logged out user tries to perform an ajax action', async function() {
      await this.b.click({ button: 'Add Field' })
      await this.b.assertExists('#logged-out-modal', { wait: 'pageLoad' })
    })

    it('should redirect to the login page on clicking the login button', async function() {
      await this.b.click({ button: 'Add Field' })
      await this.b.assertExists('#logged-out-modal', { wait: 'pageLoad' })
      await this.b.click({ button: 'Log back in' })
      await this.b.assertExists('form[action="/login"]', { wait: 'pageLoad' })
    })

    it('should go to the document set after you log back in', async function() {
      await this.b.click({ button: 'Add Field' })
      await this.b.assertExists('#logged-out-modal', { wait: 'pageLoad' })
      await this.b.click({ button: 'Log back in' })
      await this.b.assertExists('form[action="/login"]', { wait: 'pageLoad' })
      await this.b.sendKeys(this.userEmail, '.session-form [name=email]')
      await this.b.sendKeys(this.userEmail, '.session-form [name=password]')
      await this.b.click('.session-form [type=submit]')
      await this.b.assertExists('#document-list-title', { wait: 'pageLoad' })
    })
  })
})
