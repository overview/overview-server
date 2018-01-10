'use strict'

const asUser = require('./asUser')

module.exports = function asUserWithPdfDocumentSet(dir, options, body) {
  if (body === undefined) {
    body = options
    options = {}
  }

  asUser.usingTemporaryUser(function() {
    describe(`with imported PDF document set ${dir}`, function() {
      beforeEach(async function() {
        this.browser.loadShortcuts('documentSets')
        this.browser.loadShortcuts('documentSet')
        this.browser.loadShortcuts('importFiles')

        const b = this.browser
        const s = this.browser.shortcuts

        await s.importFiles.open()
        await s.importFiles.addAllFilesInDir(dir, '*.pdf')
        await s.importFiles.finish({ name: dir })
        await s.documentSet.waitUntilStable()

        if (options.dismissTour !== false) {
          await b.click([ { class: 'popover' }, { link: '×' } ])
        }
      })

      body()
    })
  })
}
