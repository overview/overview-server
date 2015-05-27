asUser = require('./asUser-new')

module.exports = asUserWithDocumentSet = (csvFilename, body) ->
  asUser.usingTemporaryUser ->
    describe "with imported document set #{csvFilename}", ->
      before ->
        @browser.loadShortcuts('importCsv')
        @browser.loadShortcuts('documentSet')
        @browser.loadShortcuts('documentSets')

        @browser
          .shortcuts.importCsv.open()
          .shortcuts.importCsv.startUpload(csvFilename)
          .shortcuts.importCsv.waitUntilRedirectToDocumentSet(csvFilename)
          .shortcuts.documentSet.waitUntilStable()

      after ->
        @browser
          .shortcuts.documentSets.destroy(csvFilename.split('/').pop())

      body()
