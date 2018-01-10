'use strict'

const asUser = require('../support/asUser')
const shouldBehaveLikeATree = require('../support/behave/likeATree')

describe('CsvUpload', function() {
  asUser.usingTemporaryUser(function() {
    beforeEach(function() {
      this.b = this.browser

      this.browser
        .loadShortcuts('importCsv')
        .loadShortcuts('documentSet')
        .loadShortcuts('documentSets')

      this.importCsv = this.browser.shortcuts.importCsv
      this.documentSet = this.browser.shortcuts.documentSet
      this.documentSets = this.browser.shortcuts.documentSets
    })

    describe('finding a character set', function() {
      it('should load UTF-8', async function() {
        await this.importCsv.openAndChooseFile('CsvUpload/basic-utf8.csv')
        const text = await this.b.getText('.preview table')
        expect(text).to.contain('achète avec des €')
      })

      it('should load Windows-1252', async function() {
        await this.importCsv.openAndChooseFile('CsvUpload/basic-windows-1252.csv')
        await this.b.assertExists('.requirements li.text.bad')
        const text = await this.b.getText('.preview table')
        expect(text).to.contain('ach�te avec des �')

        await this.b.click('select[name=charset] option[value=windows-1252]')
        const text2 = await this.b.getText('.preview table')
        expect(text2).to.contain('achète avec des €')
      })

      it('should reset the form, including encoding', async function() {
        await this.importCsv.openAndChooseFile('CsvUpload/basic-windows-1252.csv')
        await this.b.click('select[name=charset] option[value=windows-1252]')
        await this.b.click({ button: 'Reset' })
        await this.importCsv.chooseFile('CsvUpload/basic-utf8.csv')
        const text = await this.b.getText('.preview table')
        expect(text).to.contain('achète avec des €')
      })

      it('should show an error when there is no text column', async function() {
        await this.importCsv.openAndChooseFile('CsvUpload/basic-no-text.csv')
        await this.b.assertExists('.requirements li.header.bad')
      })
    })

    describe('after uploading a document set', function() {
      beforeEach(async function() {
        await this.importCsv.startUpload('CsvUpload/basic.csv')
        await this.importCsv.waitUntilRedirectToDocumentSet('CsvUpload/basic.csv')
        await this.documentSet.waitUntilStable()
      })

      it('should show the document set', async function() {
        await this.b.assertExists({ tag: 'h2', contains: 'basic.csv' })
      })

      shouldBehaveLikeATree({
        documents: [
          { type: 'text', title: 'Fourth', text: 'This is the fourth document.' }
        ],
        searches: [
          { query: 'document', nResults: 4 }
        ],
      })
    })
  })
})
