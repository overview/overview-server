TIMEOUTS = require('../TIMEOUTS')
Until = require('selenium-webdriver').until
debug_ = require('debug')('shortcuts/importCsv')

# Shortcuts for interacting with the CSV-import page.
#
# Assumptions shared by all methods:
#
# * No jobs are running that were not started here.
module.exports = importCsv = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  browser.loadShortcuts('jquery')
  browser.loadShortcuts('documentSet')

  # Opens the CSV-import page.
  open: ->
    debug_('scheduling open()')
    debug('open()')
    browser
      .get('/imports/csv')
      .shortcuts.jquery.waitUntilReady()
      .waitUntilBlockReturnsTrue('page loads file input', true, -> $('input[type=file]').length)

  # Chooses a CSV file.
  #
  # Assumptions: you previously called open().
  chooseFile: (csvFilename) ->
    debug_("scheduling startUpload(#{csvFilename})")
    debug("startUpload(#{csvFilename})")
    fullPath = "#{__dirname}/../../files/#{csvFilename}"
    browser
      .sendKeys(fullPath, css: 'input[type=file]')
      .waitUntilBlockReturnsTrue 'our JavaScript processed the selected file', 'fast', ->
        $('div.requirements li.ok').length == 4 || $('div.requirements li.bad').length == 1

  # Chooses a CSV file.
  #
  # When this method returns, the "requirements" list items will be set
  # appropriately.
  openAndChooseFile: (csvFilename) ->
    debug_("scheduling startUpload(#{csvFilename})")
    debug("startUpload(#{csvFilename})")
    browser
      .shortcuts.importCsv.open()
      .shortcuts.importCsv.chooseFile(csvFilename)

  # Chooses a CSV file and clicks the Upload button.
  #
  # When this method returns, an upload progress bar will be visible. (Or it
  # may have disappeared already -- beware of races.)
  startUpload: (csvFilename) ->
    debug_("scheduling startUpload(#{csvFilename})")
    debug("startUpload(#{csvFilename})")
    browser
      .shortcuts.importCsv.openAndChooseFile(csvFilename)
      .click(button: 'Upload')

  # Waits until the user is redirected to a document set.
  waitUntilRedirectToDocumentSet: (filename) ->
    debug_("scheduling waitUntilRedirectToDocumentSet(#{filename})")
    debug("waitUntilRedirectToDocumentSet(#{filename})")
    title = filename.split('/').pop()
    browser.driver.wait(Until.titleIs(title), TIMEOUTS.slow)
    browser.shortcuts.documentSet.waitUntilStable()
