TIMEOUTS = require('../TIMEOUTS')
Until = require('selenium-webdriver').until
debug_ = require('debug')('shortcuts/importFiles')

# Shortcuts for interacting with the File-import page.
#
# Assumptions shared by all methods:
#
# * No jobs are running that were not started here.
module.exports = importFiles = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  browser.loadShortcuts('jquery')
  browser.loadShortcuts('documentSet')

  # Opens the File-import page.
  open: ->
    debug_('scheduling open()')
    debug('open()')
    browser
      .get('/imports/file')
      .shortcuts.importFiles.waitUntilImportPageLoaded()

  # Waits for the import page to finish loading.
  #
  # Assumes you have navigated to the import page.
  waitUntilImportPageLoaded: ->
    debug_('scheduling waitUntilImportPageLoaded()')
    debug('waitUntilImportPageLoaded()')
    browser
      .shortcuts.jquery.waitUntilReady()
      .waitUntilBlockReturnsTrue 'app is initialized and file input is clickable', 'fast', ->
        $('.invisible-file-input')
          .css(opacity: 1) # Selenium needs to see it to click on it
          .length          # If the file input is missing, the app isn't loaded; run this whole block again

  # Chooses files and uploads them.
  #
  # When this method returns, the upload will have begun. (Or it may have
  # finished -- beware of races.)
  addFiles: (paths) ->
    debug_("scheduling addFiles(#{paths})")
    for path in paths
      fullPath = "#{__dirname}/../../files/#{path}"
      debug("addFile(#{path})")
      browser.sendKeys(fullPath, class: 'invisible-file-input')
    browser

  # Clicks 'Done adding files', fills in the given info, and subnmits.
  #
  # You must call addFiles() before calling this method.
  #
  # Options:
  # * name (required): String name
  # * splitByPage (optional, default false): when True, split by page
  finish: (options) ->
    throw 'Must pass options.name, a String' if !options.name

    debug_("scheduling finish(#{JSON.stringify(options)})")
    debug("finish(#{JSON.stringify(options)})")
    browser
      .click(button: 'Done adding files')
      .sendKeys(options.name, tag: 'input', name: 'name', wait: true) # wait for dialog to appear

    if options.splitByPage
      browser.click(tag: 'label', contains: 'Each page is one document')

    browser
      .click(button: 'Import documents')
      .shortcuts.importFiles.waitUntilRedirectToDocumentSet(options.name)

  # Waits until the user is redirected to a document set.
  waitUntilRedirectToDocumentSet: (name) ->
    debug_("scheduling waitUntilRedirectToDocumentSet(#{name})")
    debug("waitUntilRedirectToDocumentSet(#{name})")
    browser.driver.wait(Until.titleIs(name), TIMEOUTS.slow)
    browser.shortcuts.documentSet.waitUntilStable()
