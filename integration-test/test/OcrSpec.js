'use strict'

const asUser = require('../support/asUser')

// In order to let users analyze document text
// We need to read text that comes from images
describe('Ocr', function() {
  asUser.usingTemporaryUser(function() {
    beforeEach(async function() {
      const b = this.browser
      b.loadShortcuts('documentSets')
      b.loadShortcuts('importFiles')
      await b.execute(() => localStorage.setItem('apps/DocumentDisplay/models/Preferences/text', 'true'))
    })

    describe('when the user uploads with OCR', function() {
      beforeEach(async function() {
        const b = this.browser
        const s = this.browser.shortcuts

        await s.importFiles.open()
        await s.importFiles.addFiles(['Ocr/image.pdf'])
        await b.click({ button: 'Done adding files' })

        await b.sleep(1000) // wait for dialog to animate
        await b.sendKeys('with ocr', { tag: 'input', name: 'name', wait: true }) // wait for dialog to appear
        await b.click({ tag: 'label', contains: 'OCR' })
        await b.click({ button: 'Import documents' })
        await s.importFiles.waitUntilRedirectToDocumentSet('with ocr')
      })

      it('should have text', async function() {
        const b = this.browser
        await b.click({ tag: 'h3', contains: 'image.pdf' })
        await b.assertExists({ tag: 'pre', contains: 'This is an image of text', wait: true }) // wait for load+animate
      })
    })

    describe('when the user uploads without OCR', function() {
      beforeEach(async function() {
        const b = this.browser
        const s = b.shortcuts

        await s.importFiles.open()
        await s.importFiles.addFiles(['Ocr/image.pdf'])
        await b.click({ button: 'Done adding files' })

        await b.sleep(1000) // wait for dialog to animate
        await b.sendKeys('with ocr', { tag: 'input', name: 'name', wait: true }) // wait for dialog to appear
        await b.click({ tag: 'label', contains: 'Assume documents are already text' })
        await b.click({ button: 'Import documents' })
        await s.importFiles.waitUntilRedirectToDocumentSet('with ocr')
      })

      it('should have no text', async function() {
        const b = this.browser
        await b.click({ tag: 'h3', contains: 'image.pdf' })
        await b.assertExists({ css: 'article pre', wait: true }) // wait for load+animate
        await b.assertNotExists({ tag: 'pre', contains: 'This is an image of text' })
      })
    })
  })
})
