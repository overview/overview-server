'use strict'

const debug = require('debug')('shortcuts/documentSet')
const MockPlugin = require('../MockPlugin')

const clientTests = {
  noJobsInProgress: function() {
    return document.querySelector('progress') === null
  },

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

    // deletion is async, meaning the "Add view" link will move. Wait for it
    // to reach its new spot.
    await this.b.assertNotExists({ link: name, wait: true })
  }

  // Set "public" on or off.
  //
  // After completion, the server will have set "public" on or off on the given
  // document set.
  async setPublic(bool) {
    debug(`scheduling setPublic(${bool})`)

    await this.b.click({ css: 'nav .dropdown-toggle a' })
    await this.b.click({ css: 'a.show-sharing-settings' })

    await this.b.inFrame(0, async () => { // ick -- we use an iframe here
      await this.s.jquery.waitUntilReady()

      // Within the iframe is a JS app. We need to wait for it to finish loading:
      const checked = await this.b.isSelected({ css: '[name=public]', wait: true })

      if (checked != bool) {
        // hack because this is an iframe. Even after jquery.isReady, we don't
        // know that the CSS has loaded. That's important, because the modal
        // dialog will resize once the CSS loads, which will move the checkbox,
        // meaning we won't be able to click the checkbox.
        //
        // There's no easy way to know when the CSS loads. So we'll just wait 1s,
        // assuming it's really, really fast.
        await this.b.sleep(1000)

        await this.s.jquery.listenForAjaxComplete()
        await this.b.click('[name=public]')
        await this.s.jquery.waitUntilAjaxComplete()
      }
    })

    await this.b.click('#sharing-options-modal button.close')
    await this.b.assertNotExists('#sharing-options-modal', { wait: 'fast' })
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

    await this.b.waitUntilBlockReturnsTrue('jobs to complete', 'slow', clientTests.noJobsInProgress)
    await this.s.jquery.waitUntilReady()
    await this.waitUntilDocumentListLoaded()
    await this.b.waitUntilBlockReturnsTrue('plugin data to load', 'pageLoad', clientTests.pluginDataLoaded)
  }

  async createCustomView(name, url) {
    await this.b.click({ link: 'Add view' })
    await this.b.click({ link: 'Custom…', wait: 'fast' })
    // Enter URL first, then name. Entering name will blur URL. Blurring URL
    // makes the browser test the endpoint.
    await this.b.sendKeys(url, { css: '#new-view-dialog-url', wait: 'fast' })
    await this.b.sendKeys(name, { css: '#new-view-dialog-title' })
    await this.b.click({ link: 'use it anyway' , wait: true }) // dismiss not-HTTPS warning
    await this.b.assertExists({ css: '#new-view-dialog .state .ok', wait: 'slow' })
    await this.b.click({ css: 'input[value="Create visualization"]', wait: 'fast' })

    // Wait for the plugin to _begin_ loading. (Let's not assume it _will_ end:
    // that's hard to do, and we might want to test that some things happen _before_
    // load ends, anyway.)
    await this.b.find('#view-app-iframe', { wait: 'fast' })
  }

  /**
   * Starts a MockPlugin listening on :3333; creates a View and returns
   * the MockPlugin.
   *
   * When you're done, you must `await returnedPlugin.close()`
   */
  async createViewAndServer(name) { // returns child process
    debug(`createCustomView(${name})`)

    const server = new MockPlugin(name)
    await server.listen()

    try {
      await this.createCustomView(name, `http://${server.hostname}:3333`)
    } catch (e) {
      await server.close()
      throw e
    }

    return server
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
