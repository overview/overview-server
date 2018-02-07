'use strict'

const asUser = require('../support/asUser')

// In order to let users analyze document text
// We need to let them highlight and take notes
describe('PdfNotes', function() {
  asUser.usingTemporaryUser(function() {
    beforeEach(async function() {
      const b = this.browser
      const s = this.browser.shortcuts
      b.loadShortcuts('documentSet')
      b.loadShortcuts('documentSets')
      b.loadShortcuts('importFiles')
      b.loadShortcuts('pdfNotes')

      await s.importFiles.open()
      await s.importFiles.addFiles([ 'PdfNotes/doc1.pdf', 'PdfNotes/doc2.pdf' ])
      await s.importFiles.finish({ name: 'annotations' })
      await s.documentSet.waitUntilStable()

      await b.shortcuts.documentSet.openDocumentFromList('doc1.pdf')
      await b.find('iframe#document-contents', { wait: 'fast' }) // wait for PDF to start loading
    })

    it('should let the user create an annotation', async function() {
      const b = this.browser

      await b.shortcuts.pdfNotes.createNote() // No error? Then it worked
    })

    it('should save and load annotations on the server', async function() {
      const b = this.browser

      await b.shortcuts.pdfNotes.createNote()

      // Reload the page
      await b.shortcuts.documentSets.open('annotations')
      await b.shortcuts.documentSet.openDocumentFromList('doc1.pdf')

      await b.find('iframe#document-contents', { wait: 'fast' })
      await b.inFrame('document-contents', async () => {
        await b.assertExists({ css: '#viewer .noteLayer section' }, { wait: 'pageLoad' })

        await b.click('#viewer .noteLayer section')
        let text = await b.getAttribute({ css: '.editNoteTool textarea', wait: true }, 'value')
        expect(text).to.eq('Hello, world!')
      })
    })

    it('should search for annotations', async function() {
      const b = this.browser

      await b.shortcuts.pdfNotes.createNote()
      await b.shortcuts.documentSet.goBackToDocumentList()

      await b.sendKeys('notes:Hello, world!', '#document-list-params .search input[name=query]')
      await b.click('#document-list-params .search button')

      const text = await(b.getText({ css: '#document-list ul.documents li', wait: 'pageLoad' }))
      expect(text).to.match(/doc1\.pdf/)
    })

    it('should delete annotations', async function() {
      const b = this.browser

      await b.shortcuts.pdfNotes.createNote()

      await b.inFrame('document-contents', async () => {
        await b.click('#viewer .noteLayer section')
        await b.click({ css: '.editNoteTool button.editNoteDelete', wait: true })
        await b.waitUntilFunctionReturnsTrue('.editNoteTool disappears', true, function() {
          return b.find('.editNoteTool', { throwOnNull: false }).then(x => x === null)
        })
        await b.assertNotExists('#viewer .noteLayer section')
      })
    })

    // We haven't tested deleting them from the server.
  })
})
