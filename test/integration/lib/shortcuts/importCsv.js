'use strict'

const TIMEOUTS = require('../TIMEOUTS')
const Until = require('selenium-webdriver').until
const debug = require('debug')('shortcuts/importCsv')

class ImportCsvShortcuts {
  constructor(browser) {
    this.b = browser

    browser.loadShortcuts('jquery')
    browser.loadShortcuts('documentSet')

    this.jquery = browser.shortcuts.jquery
    this.documentSet = browser.shortcuts.documentSet
  }

  // Opens the CSV-import page.
  async open() {
    debug('open()')
    await this.b.get('/imports/csv')
    await this.jquery.waitUntilReady()
    await this.b.waitUntilBlockReturnsTrue(
      'page loads file input',
      true,
      function() { return document.querySelector('input[type=file]') !== null; }
    )
  }

  // Chooses a CSV file.
  //
  // Assumptions: you previously called open().
  async chooseFile(csvFilename) {
    debug(`startUpload(${csvFilename})`)
    const fullPath = `${__dirname}/../../files/${csvFilename}`

    await this.b.sendKeys(fullPath, 'input[type=file]')
    await this.b.waitUntilBlockReturnsTrue(
      'our JavaScript processed the selected file',
      'fast',
      function() {
        return document.querySelectorAll('div.requirements li.ok').length === 4
          || document.querySelectorAll('div.requirements li.bad').length === 1
      }
    )
  }

  // Chooses a CSV file.
  //
  // When this method returns, the "requirements" list items will be set
  // appropriately.
  async openAndChooseFile(csvFilename) {
    debug(`startUpload(${csvFilename})`)

    await this.open()
    await this.chooseFile(csvFilename)
  }

  // Chooses a CSV file and clicks the Upload button.
  //
  // When this method returns, an upload progress bar will be visible. (Or it
  // may have disappeared already -- beware of races.)
  async startUpload(csvFilename) {
    debug(`startUpload(${csvFilename})`)

    await this.openAndChooseFile(csvFilename)
    await this.b.click({ button: 'Upload' })
  }

  // Waits until the user is redirected to a document set.
  async waitUntilRedirectToDocumentSet(filename) {
    debug(`waitUntilRedirectToDocumentSet(${filename})`)
    const title = filename.split('/').pop()

    await this.b.driver.wait(Until.titleIs(title), TIMEOUTS.slow)
    await this.documentSet.waitUntilStable()
  }
}

// Shortcuts for interacting with the CSV-import page.
//
// Assumptions shared by all methods:
//
// * No jobs are running that were not started here.
module.exports = function importCsv(browser) {
  return new ImportCsvShortcuts(browser)
}
