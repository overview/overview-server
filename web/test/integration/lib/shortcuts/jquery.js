'use strict'

const debug = require('debug')('shortcuts/jquery')

class JqueryShortcuts {
  constructor(browser) {
    this.browser = browser
  }

  // Returns when jQuery is loaded and DOMReady has fired.
  //
  // If the site is structured such that all code loads in one big bundle, then
  // this effectively means the page is fully initialized (except for image
  // loading).
  async waitUntilReady() {
    debug('waitUntilReady()')
    await this.browser.waitUntilBlockReturnsTrue(
      'jQuery to load',
      'pageLoad',
      function() { return window.jQuery && window.jQuery.isReady }
    )
  }

  // Mark, used for waitUntilAjaxComplete().
  async listenForAjaxComplete() {
    debug('listenForAjaxComplete()')
    await this.browser.execute(function() {
      window.listenForAjaxComplete = false
      jQuery(document).one('ajaxComplete', function() { window.listenForAjaxComplete = true })
    })
  }

  // Finishes when an jQuery.ajaxComplete method is fired.
  //
  // Before calling this, you must call listenForAjaxComplete().
  // waitUntilAjaxComplete() will finish when there has been at least one
  // AJAX response between the two calls.
  //
  // Note the danger of a races. If a request was pending before you called
  // listenForAjaxComplete(), this method will finish once that pending
  // AJAX method completes. To avoid races, guarantee there are no pending AJAX
  // requests when you call listenForAjaxComplete()
  async waitUntilAjaxComplete() {
    debug('waitUntilAjaxComplete()')

    await this.browser.waitUntilBlockReturnsTrue(
      'AJAX request to finish',
      'pageLoad',
      function() { return window.listenForAjaxComplete }
    )
  }
}

// Shortcuts that deal with AJAX requests.
module.exports = function(browser) {
  return new JqueryShortcuts(browser)
}
