'use strict'

const asUser = require('../support/asUser')
const shouldBehaveLikeATree = require('../support/behave/likeATree')

const Url = {
  index: '/documentsets',
  show: /\/documentsets\/(\d+)/,
  csvUpload: '/imports/csv',
  publicDocumentSets: '/public-document-sets',
}

const userToTrXPath = (email) => `//tr[contains(td[@class='email'], '${email}')]`

describe('ExampleDocumentSets', function() {
  asUser.usingTemporaryUser(function() {
    asUser.usingTemporaryUser({ isAdmin: true, propertyName: 'admin' }, function() {
      beforeEach(function() {
        this.adminBrowser
          .loadShortcuts('importCsv')
          .loadShortcuts('documentSet')
          .loadShortcuts('documentSets')

        this.browser
          .loadShortcuts('documentSets')
      })

      describe('after being set as an example', function() {
        beforeEach(async function() {
          await this.adminBrowser.shortcuts.importCsv.startUpload('CsvUpload/basic.csv')
          await this.adminBrowser.shortcuts.importCsv.waitUntilRedirectToDocumentSet('basic.csv')
          await this.adminBrowser.shortcuts.documentSet.waitUntilStable()
          await this.adminBrowser.shortcuts.documentSet.setPublic(true)
        })

        it('should be cloneable', async function() {
          await this.browser.shortcuts.documentSets.clone('basic.csv')
          await this.browser.get(Url.index)
          await this.browser.assertExists({ tag: 'h3', contains: 'basic.csv', wait: 'pageLoad' })
        })

        it('should be removed from the example list when unset as an example', async function() {
          await this.adminBrowser.shortcuts.documentSet.setPublic(false)
          await this.browser.get(Url.publicDocumentSets)
          await this.browser.assertExists({ tag: 'p', contains: 'There are currently no example document sets.', wait: 'pageLoad' })
        })

        describe('the cloned example', function() {
          beforeEach(async function() {
            await this.browser.shortcuts.documentSets.clone('basic.csv')
          })

          it('should remain after the original is deleted', async function() {
            await this.adminBrowser.shortcuts.documentSets.destroy('basic.csv')
            await this.browser.shortcuts.documentSets.open('basic.csv')
            await this.browser.assertExists({ tag: 'a', contains: 'basic.csv', wait: 'pageLoad' })
          })

          shouldBehaveLikeATree({
            documents: [
              { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
            ],
            searches: [
              { query: 'document', nResults: 4 }
            ],
          })
        })
      })
    })
  })
})
