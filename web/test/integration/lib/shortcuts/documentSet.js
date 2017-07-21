'use strict'

const debug = require('debug')('shortcuts/documentSet')

const clientTests = {
  noJobsInProgress: function() {
    return window.$ && window.$.isReady && $('progress').length === 0
  }, // needs jQuery check because page refreshes

  pluginDataLoaded: function() {
    return document.querySelector('a[data-plugin-url="about:tree"]') !== null
  }
}

class DocumentSetShortcuts {
  constructor(browser) {
    browser.loadShortcuts('jquery')

    this.b = browser
    this.s = browser.shortcuts
  }

  // Destroys the view with the given name.
  //
  // After completion, the server will have deleted the document set but the
  // client will be in an undefined state. You must open another page to
  // continue using it.
  async destroyView(name) {
    debug(`destroyView(${name})`)
    await this.b.click([ { link: name }, { class: 'toggle-popover' } ])
    await this.b.click({ button: 'Delete' })
    await this.b.alert().accept()
  }

  // Set "public" on or off.
  //
  // After completion, the server will have set "public" on or off on the given
  // document set.
  async setPublic(bool) {
    debug(`scheduling setPublic(${bool})`)

    await this.b.click({ css: 'nav .dropdown-toggle a' })
    await this.b.click({ css: 'a.show-sharing-settings' })

    await this.b.switchToFrame(0) // ick -- we use an iframe here.
    await this.s.jquery.waitUntilReady()

    // Within the iframe is a JS app. We need to wait for it to finish loading:
    const checked = await this.b.isSelected({ css: '[name=public]', wait: 'pageLoad' })

    if (checked != bool) {
      await this.s.jquery.listenForAjaxComplete()
      await this.b.click('[name=public]')
      await this.s.jquery.waitUntilAjaxComplete()
    }

    await this.b.switchToFrame(null)
    await this.b.click('#sharing-options-modal button.close')
  }

  // Hides the "Tour" dialog forevermore for this user
  async hideTour() {
    debug('hideTour()')
    browser.click('.popover-title a.skip')
  }

  // Creates a new Tree view with the given name.
  //
  // options.tag sets an optional tag name. If unset, all documents will be
  // reclustered.
  async recluster(name, options) {
    options = Object.assign({}, options)

    debug(`recluster(${name}, ${JSON.stringify(options)})`)
    await this.b.click({ link: 'Add view' })
    await this.b.click({ css: 'a[data-plugin-url="about:tree"]', wait: true }) // wait for menu to appear
    await this.b.sendKeys(name, { css: 'input[name=tree_title]', wait: true }) // wait because modal fades in

    if (options.tag) {
      // wait because tags come in an AJAX response
      await this.b.click({ tag: 'option', contains: options.tag, wait: true })
    }

    await this.b.click({ button: 'Create Tree' })
    await this.b.shortcuts.documentSet.waitUntilStable()
  }

  // Renames a view
  async renameView(oldTitle, newTitle) {
    debug(`renameView(${oldTitle}, ${newTitle})`)
    await this.b.click([ { link: oldTitle }, { class: 'toggle-popover' } ])
    await this.b.click({ link: 'rename', wait: true }) // wait for popover to appear
    await this.b.sendKeys(newTitle, { css: 'input[name=title]', wait: true }) // wait for form to appear
    await this.s.jquery.listenForAjaxComplete()
    await this.b.click({ button: 'Save' })
    await this.s.jquery.waitUntilAjaxComplete()
    await this.b.click({ link: 'Close' })
  }

  async renameDocument(oldTitle, newTitle) {
    debug(`renameDocument(${oldTitle}, ${newTitle})`)

    await this.b.click({ tag: 'h3', contains: oldTitle })
    await this.b.sleep(500) // animation
    await this.b.click({ css: 'header .edit-title', wait: true }) // wait for popover to appear
    await this.b.sendKeys(newTitle, 'input[name=title]')
    await this.s.jquery.listenForAjaxComplete()
    await this.b.click({ button: 'Save' })
    await this.s.jquery.waitUntilAjaxComplete()
    await this.b.click({ link: 'Back to list' })
    await this.b.sleep(500) // animation
  }

  async search(q) {
    debug(`search(${q})`)
    await this.b.sendKeys(q, '#document-list-params .search input[name=query]')
    await this.b.click('#document-list-params .search button')
  }

  // Waits for #document-list:not(.loading) to exist.
  async waitUntilDocumentListLoaded() {
    debug('waitUntilDocumentListLoaded()')
    // Search for #document-list and _then_ #document-list:not(.loading). That
    // way if #document-list:not(.loading) isn't found, we can diagnose whether
    // our timeout is too short or whether the page isn't loading properly.
    await this.b.assertExists({ css: '#document-list', wait: 'pageLoad' })
    await this.b.assertExists({ css: '#document-list:not(.loading)', wait: 'pageLoad' })
  }

  // Waits until the document set page is completely stable.
  //
  // This means:
  //
  // * The plugin list has been loaded.
  // * A document list is visible.
  // * All pending jobs are finished.
  async waitUntilStable() {
    debug('waitUntilStable()')

    await this.s.jquery.waitUntilReady()
    await this.b.waitUntilBlockReturnsTrue('jobs to complete', 'slow', clientTests.noJobsInProgress)
    await this.waitUntilDocumentListLoaded()
    await this.b.waitUntilBlockReturnsTrue('plugin data to load', 'pageLoad', clientTests.pluginDataLoaded)
  }
}

// Shortcuts for doing stuff while on the DocumentSet page.
//
// Assumptions shared by all methods:
//
// * You are on the document set page, and the page has stabilized. (Use
//   documentSets#open() to reach this state.)
module.exports = function(browser) {
  return new DocumentSetShortcuts(browser)
}
