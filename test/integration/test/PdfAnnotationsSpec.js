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
      await b.find('iframe#document-contents', { wait: 'fast' }) // wait for PDF to start loading

      await b.switchToFrame('document-contents')
      await b.waitUntilBlockReturnsTrue('notes code is loaded', 'pageLoad', function() {
        return document.querySelector('.noteLayer') !== null
      });

      await b.assertExists('#viewer .textLayer div', { wait: 'pageLoad' })
      await b.click('button#addNote')

      const el = (await b.find({ css: '#viewer .textLayer div' })).driverElement

      await b.driver.actions()
        .mouseDown(el)
        .mouseMove({ x: 200, y: 100 })
        .mouseUp()
        .perform()

      await b.assertExists('#viewer .noteLayer section', { wait: true })
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
      await b.find('iframe#document-contents', { wait: 'fast' })
      await b.switchToFrame('document-contents')
      await b.assertExists({ css: '#viewer .noteLayer section' }, { wait: 'pageLoad' })

      await b.click('#viewer .noteLayer section')
      let text = await b.getAttribute({ css: '.editNoteTool textarea', wait: true }, 'value')
      expect(text).to.eq('Hello, world!')
      await b.click('.editNoteTool button.editNoteClose')

      await b.switchToFrame(null)
    })

    it('should search for annotations', async function() {
      const b = this.browser

      // we start with a single document selected. in on a single document. This
      // test searches, automatically de-selecting the document. To reset, we'll
      // re-select it at the end of the test.

      await b.sendKeys('notes:Hello, world!', '#document-list-params .search input[name=query]')
      await b.click('#document-list-params .search button')
      await b.sleep(1000) // de-select animation

      const text = await(b.getText({ css: '#document-list ul.documents li', wait: 'pageLoad' }))
      expect(text).to.match(/doc1\.pdf/)

      // re-select the document for the next test. First nix the search:
      // otherwise the PDF viewer's find bar will obstruct our note
      await b.click('#document-list-params .search a.nix')
      await b.click({ tag: 'h3', contains: 'doc1.pdf', wait: true })
      await b.sleep(1000) // select animation
    })

    it('should delete annotations', async function() {
      const b = this.browser

      await b.find('iframe#document-contents', { wait: 'fast' })
      await b.switchToFrame('document-contents')

      // Hacky -- we need to wait for the PDF to load because the previous test
      // left us in an inconsistent state
      await b.waitUntilBlockReturnsTrue('notes code is loaded', 'pageLoad', function() {
        return document.querySelector('.noteLayer') !== null
      });

      await b.click('#viewer .noteLayer section')
      await b.click({ css: '.editNoteTool button.editNoteDelete', wait: true })
      await b.assertNotExists('.editNoteTool')
      await b.assertNotExists('#viewer .noteLayer section')

      await b.switchToFrame(null)
    })

    // We haven't tested deleting them from the server.
  })
})
