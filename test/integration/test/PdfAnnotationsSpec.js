'use strict'

const asUser = require('../support/asUser')

// In order to let users analyze document text
// We need to let them highlight and take notes
describe('PdfAnnotations', function() {
  asUser.usingTemporaryUser(function() {
    before(async function() {
      const b = this.browser
      const s = this.browser.shortcuts
      b.loadShortcuts('documentSets')
      b.loadShortcuts('importFiles')

      await s.importFiles.open()
      await s.importFiles.addFiles([ 'PdfAnnotations/doc1.pdf', 'PdfAnnotations/doc2.pdf' ])
      await s.importFiles.finish({ name: 'annotations' })
    })

    // These tests form a story. If the first one fails, the rest probably will
    // fail as well.

    it('should let the user create an annotation', async function() {
      const b = this.browser

      await b.click({ tag: 'h3', contains: 'doc1.pdf' })
      await b.sleep(1000)                      // animate document selection
      await b.find('article iframe', { wait: 'fast' }) // wait for PDF to start loading

      await b.switchToFrame('document-contents')

      await b.assertExists('#viewer .textLayer', { wait: 'pageLoad' })
      await b.click('button#addNote')

      const el = (await b.find({ css: '#viewer .textLayer div' })).driverElement

      await b.driver.actions()
        .mouseDown(el)
        .mouseMove({ x: 200, y: 100 })
        .mouseUp()
        .perform()

      await b.assertExists('#viewer .noteLayer section', { wait: true })

      await b.switchToFrame(null)
    })

    it('should add text to an annotation', async function() {
      const b = this.browser

      await b.switchToFrame('document-contents')

      await b.click('#viewer .noteLayer section')
      await b.assertExists('.editNoteTool', { wait: true })
      await b.sendKeys('Hello, world!', '.editNoteTool textarea')
      await b.click('.editNoteTool button.editNoteSave')
      await b.assertExists('.editNoteTool button.editNoteSave[disabled]', { wait: true })
      await b.click('.editNoteTool button.editNoteClose')

      await b.switchToFrame(null)
    })

    it('should save and load annotations on the server', async function() {
      const b = this.browser

      await b.click('.document-nav .next')
      await b.click('.document-nav .previous')
      await b.find('article iframe', { wait: 'fast' })
      await b.switchToFrame('document-contents')
      await b.assertExists({ css: '#viewer .noteLayer section' }, { wait: 'pageLoad' })

      await b.click('#viewer .noteLayer section')
      let text = await b.getAttribute({ css: '.editNoteTool textarea', wait: true }, 'value')
      expect(text).to.eq('Hello, world!')
      await b.click('.editNoteTool button.editNoteClose')

      await b.switchToFrame(null)
    })

    it('should delete annotations', async function() {
      const b = this.browser

      await b.switchToFrame('document-contents')

      await b.click('#viewer .noteLayer section')
      await b.click({ css: '.editNoteTool button.editNoteDelete', wait: true })
      await b.assertNotExists('.editNoteTool')
      await b.assertNotExists('#viewer .noteLayer section')

      await b.switchToFrame(null)
    })

    // We haven't tested deleting them from the server.
  })
})
