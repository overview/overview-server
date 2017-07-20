'use strict'

const debug = require('debug')('shortcuts/documentSets')

const Urls = {
  publicDocumentSets: '/public-document-sets',
}

class DocumentSetsShortcuts {
  constructor(browser) {
    browser.loadShortcuts('jquery')
    browser.loadShortcuts('documentSet')

    this.b = browser
    this.s = this.b.shortcuts
  }

  // Opens a document set with the given name.
  //
  // After calling this method, the document set will be on the screen with its
  // default view, and all AJAX requests will be finished.
  async open(name) {
    debug(`open(${name})`)
    await this.b.get('/documentsets')
    await this.b.click({ link: name })
    await this.s.documentSet.waitUntilStable()
  }

  // Destroys a document set.
  //
  // After calling this method, the document set will be deleted, but the client
  // will be in an unstable state. Navigate to another page to reuse the client.
  async destroy(name) {
    debug(`destroy(${name})`)

    const li = { tag: 'li', contains: name }

    await this.b.get('/documentsets')
    await this.s.jquery.waitUntilReady()
    await this.b.click([ li, { class: 'dropdown-toggle' } ])

    // The page reloads after the user presses Delete. Detect the reload.
    await this.b.execute(function() { this.pageHasNotReloaded = null })
    await this.b.click([ li, { link: 'Delete' } ])
    await this.b.alert().accept()
    await this.b.waitUntilBlockReturnsTrue(
      'page to reload',
      'pageLoad',
      function() { return !window.hasOwnProperty('pageHasNotReloaded'); }
    )
  }

  // Clones a document set with the given name, producing a new one.
  //
  // After calling this method, the document set will be on the screen with its
  // default view, and all AJAX requests will be finished.
  async clone(name) {
    debug(`clone(${name})`)
    await this.b.get(Urls.publicDocumentSets)
    await this.s.jquery.waitUntilReady()
    await this.b.click([ { class: 'document-set', contains: name }, { tag: 'button', contains: 'Clone' } ])
    await this.s.documentSet.waitUntilStable()
  }
}

// Shortcuts for manipulating the collection of DocumentSets for a user.
//
// Assumptions shared by all methods:
//
// * The browser URL can be anything
// * No jobs are running
// * No two document sets share the same name
module.exports = function(browser) {
  return new DocumentSetsShortcuts(browser)
}
