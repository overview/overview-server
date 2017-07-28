'use strict'

const debug = require('debug')('shortcuts/api')

class ApiShortcuts {
  constructor(browser) {
    browser.loadShortcuts('jquery')

    this.jquery = browser.shortcuts.jquery
    this.b = browser
  }

  // Creates an API token and returns it in a Promise
  async createApiToken(documentSetId, keyName) {
    debug(`createApiToken(${documentSetId})`)
    await this.b.get(`/documentsets/${documentSetId}/api-tokens`)
    await this.b.waitUntilBlockReturnsTrue(
      'api-token page to load',
      'pageLoad',
      function() { return window.jQuery && window.jQuery.isReady && document.querySelector('#api-token-description') !== null; }
    )

    await this.b.sendKeys(keyName, '#api-token-description')
    await this.jquery.listenForAjaxComplete()
    await this.b.click({ button: 'Generate token' })
    await this.jquery.waitUntilAjaxComplete()
    const token = await this.b.getText([ { tag: 'tr', contains: keyName }, { tag: 'td', class: 'token' } ])

    return token
  }
}

// Shortcuts for manipulating API tokens.
//
// These methods make no assumptions about browser state.
module.exports = function(browser) {
  return new ApiShortcuts(browser)
}
