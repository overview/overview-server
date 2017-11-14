'use strict'

const asUser = require('./asUser')

module.exports = function asUserWithDocumentSet(csvFilename, options, body) {
  if (body === undefined) {
    body = options
    options = {}
  }

  asUser.usingTemporaryUser(function() {
    describe(`with imported document set ${csvFilename}`, function() {
      before(async function() {
        const b = this.browser
        const s = this.browser.shortcuts

        await b.loadShortcuts('importCsv')
        await b.loadShortcuts('documentSet')
        await b.loadShortcuts('documentSets')

        await s.importCsv.open()
        await s.importCsv.startUpload(csvFilename)
        await s.importCsv.waitUntilRedirectToDocumentSet(csvFilename)
        await s.documentSet.waitUntilStable()

        if (options.dismissTour !== false) {
          await b.click([ { class: 'popover' }, { link: '×' } ])
        }
      })

      after(async function() {
        await this.browser.shortcuts.documentSets.destroy(csvFilename.split('/').pop())
      })

      body()
    })
  })
}
