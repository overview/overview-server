'use strict'

const TIMEOUTS = require('../TIMEOUTS')
const Until = require('selenium-webdriver').until
const debug = require('debug')('shortcuts/importFiles')

class ImportFilesShortcuts {
  constructor(browser) {
    browser.loadShortcuts('jquery')
    browser.loadShortcuts('documentSet')

    this.b = browser
    this.s = browser.shortcuts
  }

  // Opens the File-import page.
  async open() {
    debug('open()')
    await this.b.get('/imports/file')
    await this.s.importFiles.waitUntilImportPageLoaded()
  }

  // Waits for the import page to finish loading.
  //
  // Assumes you have navigated to the import page.
  async waitUntilImportPageLoaded() {
    debug('waitUntilImportPageLoaded()')
    await this.s.jquery.waitUntilReady()
    await this.b.waitUntilBlockReturnsTrue(
      'app is initialized and file input is clickable',
      'pageLoad',
      function() {
        return window.jQuery && jQuery('.invisible-file-input')
          .css({ opacity: 1 }) // Selenium needs to see it to click on it
          .length              // If the file input is missing, the app isn't loaded; run this whole block again
      }
    )
  }

  // Chooses files and uploads them.
  //
  // When this method returns, the upload will have begun. (Or it may have
  // finished -- beware of races.)
  async addFiles(paths) {
    debug(`addFiles(${JSON.stringify(paths)})`)
    for (const path of paths) {
      const fullPath = `${__dirname}/../../files/${path}`
      await this.b.sendKeys(fullPath, '.invisible-file-input')
    }
  }

  // Clicks 'Done adding files', fills in the given info, and subnmits.
  //
  // You must call addFiles() before calling this method.
  //
  // Options:
  // * name (required): String name
  // * splitByPage (optional, default false): when True, split by page
  async finish(options) {
    if (!options.name) {
      throw 'Must pass options.name, a String'
    }

    debug(`finish(${JSON.stringify(options)})`)
    await this.b.click({ button: 'Done adding files' })
    await this.b.sendKeys(options.name, { tag: 'input', name: 'name', wait: true }) // wait for dialog to appear

    if (options.splitByPage) {
      await this.b.click({ tag: 'label', contains: 'Each page is one document' })
    }

    await this.b.click({ button: 'Import documents' })
    await this.waitUntilRedirectToDocumentSet(options.name)
  }

  // Waits until the user is redirected to a document set.
  async waitUntilRedirectToDocumentSet(name) {
    debug(`waitUntilRedirectToDocumentSet(${name})`)
    await this.b.driver.wait(Until.titleIs(name), TIMEOUTS.slow)
    await this.s.documentSet.waitUntilStable()
  }
}

// Shortcuts for interacting with the File-import page.
//
// Assumptions shared by all methods:
//
// * No jobs are running that were not started here.
module.exports = function(browser) {
  return new ImportFilesShortcuts(browser)
}
