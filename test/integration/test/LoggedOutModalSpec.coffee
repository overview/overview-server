asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')

describe 'LoggedOutModal', ->
  asUserWithDocumentSet 'CsvUpload/basic.csv', ->

    it 'should show a modal when a logged out tries to perform an ajax action', ->
      @browser.loadShortcuts('jquery')
      @browser.driver.manage().deleteAllCookies()

      @browser
        .shortcuts.jquery.listenForAjaxComplete()
        .sendKeys('test\uE007', {css: 'input[name=query]'})
        .shortcuts.jquery.waitUntilAjaxComplete()

      @browser.assertExists(id: 'logged-out-modal')

    it 'should redirect to the login page on clicking the login button', ->
      @browser
        .click('tag': 'button', 'contains': 'Log back in')
        .assertExists(css: 'form[action="/login"]', wait: 'pageLoad')

    # we need to log back in so that asUserWithDocumentSet's
    # afterAll hook will pass, so we might as well test the redirect too.
    it 'logging back in should redirect to document set', ->
      @browser
        .sendKeys(@userEmail, css: '.session-form [name=email]')
        .sendKeys(@userEmail, css: '.session-form [name=password]')
        .click(css: '.session-form [type=submit]')
        .assertExists(css: '#document-list-title', wait: 'pageLoad')

