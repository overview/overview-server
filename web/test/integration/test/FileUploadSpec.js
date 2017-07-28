'use strict'

const asUser = require('../support/asUser')
const shouldBehaveLikeATree = require('../support/behave/likeATree')

describe('FileUpload', function() {
  asUser.usingTemporaryUser(function() {
    before(function() {
      this.browser.loadShortcuts('documentSets')
      this.browser.loadShortcuts('importFiles')

      this.b = this.browser
      this.documentSets = this.b.shortcuts.documentSets
      this.importFiles = this.b.shortcuts.importFiles
    })

    describe('after creating a file with metadata', function() {
      before(async function() {
        // Add a file, with metadata 'foo': 'bar'
        await this.importFiles.open()
        await this.importFiles.addFiles(['FileUpload/Cat1.docx'])
        await this.b.click({ button: 'Done adding files' })

        await this.b.sleep(500) // wait for dialog to appear
        await this.b.sendKeys('metadata-test', { tag: 'input', name: 'name', wait: true })
        await this.b.click({ link: 'Add new field' })
        await this.b.sendKeys('foo', { tag: 'input', id: 'document-metadata-add-field' })
        await this.b.click('#document-metadata-add-field + button')
        await this.b.sendKeys('bar', { tag: 'input', name: 'foo' })
        await this.b.click({ button: 'Import documents' })
        await this.importFiles.waitUntilRedirectToDocumentSet('metadata-test')
      })

      it('should add metadata to the imported file', async function() {
        await this.b.click({ tag: 'h3', contains: 'Cat1.docx' })

        await this.b.sleep(1000) // wait for document to appear
        await this.b.click({ link: 'Fields', wait: true })

        // wait for metadata to appear
        const value = await this.b.getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value')
        expect(value).to.eq('bar')
      })

      it('should add more metadata in a second import', async function() {
        // Add another file, with metadata 'moo':'mar'
        await this.b.click([ { tag: 'nav' }, { link: 'metadata-test' } ])
        await this.b.click({ link: 'Add Documents' })
        await this.importFiles.waitUntilImportPageLoaded()
        await this.importFiles.addFiles(['FileUpload/Cat2.txt'])
        await this.b.click({ button: 'Done adding files' })
        await this.b.sleep(1000) // wait for dialog to appear
        await this.b.click({ link: 'Add new field', wait: true })
        await this.b.sendKeys('moo', { tag: 'input', id: 'document-metadata-add-field' })
        await this.b.click('#document-metadata-add-field + button')
        // wait for JS to add field (Jenkins found an error once)
        await this.b.sendKeys('mar', { tag: 'input', name: 'moo', wait: true })
        await this.b.click({ button: 'Import documents' })
        await this.importFiles.waitUntilRedirectToDocumentSet('metadata-test')

        // Check the first document still has 'foo':'bar'
        await this.b.click({ tag: 'h3', contains: 'Cat1.docx' })
        await this.b.sleep(1000) // wait for document to animate in
        await this.b.click({ link: 'Fields', wait: true })
        // wait for metadata to appear
        const value1 = await this.b.getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value')
        expect(value1).to.eq('bar')

        const value2 = await this.b.getAttribute({ tag: 'input', name: 'moo', wait: true }, 'value')
        expect(value2).to.eq('')

        // Check the second document has 'moo':'mar'
        // Avoid a race: how do we detect the second document has loaded? By
        // navigating away from the first document, waiting for it to disappear,
        // navigating to the second, and waiting for it to load.
        await this.b.click({ link: 'Back to list' })
        await this.b.sleep(1000) // FIXME debug, figure out why we need this, then remove it
        await this.b.click({ tag: 'h3', contains: 'Cat2.txt', wait: 'fast' })
        await this.b.sleep(1000) // FIXME debug, figure out why we need this, then remove it
        // wait for metadata to appear
        const value3 = await this.b.getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value')
        expect(value3).to.eq('')

        const value4 = await this.b.getAttribute({ tag: 'input', name: 'moo', wait: true }, 'value')
        expect(value4).to.eq('mar')
      })

      after(async function() {
        await this.documentSets.destroy('metadata-test')
      })
    })

    describe('after uploading files', function() {
      before(async function() {
        await this.importFiles.open()
        await this.importFiles.addFiles([
          'FileUpload/Cat0.pdf',
          'FileUpload/Cat1.docx',
          'FileUpload/Cat2.txt',
          'FileUpload/Cat3.rtf',
          'FileUpload/Cat4.html',
          'FileUpload/Jules1.doc',
          'FileUpload/Jules2.pptx',
          'FileUpload/Jules3.xlsx',
        ])
        await this.importFiles.finish({ name: 'FileUpload' })
      })

      after(async function() {
        await this.documentSets.destroy('FileUpload')
      })

      shouldBehaveLikeATree({
        documents: [
          { type: 'pdf', title: 'Cat0.pdf' },
          { type: 'pdf', title: 'Cat1.docx' },
          { type: 'pdf', title: 'Cat2.txt' },
          { type: 'pdf', title: 'Cat3.rtf' },
          { type: 'pdf', title: 'Cat4.html' },
          { type: 'pdf', title: 'Jules1.doc' },
          { type: 'pdf', title: 'Jules2.pptx' },
          { type: 'pdf', title: 'Jules3.xlsx' },
        ],
        searches: [
          { query: 'chase', nResults: 5 }
        ],
      })
    })

    describe('after splitting a file into pages', function() {
      before(async function() {
        await this.importFiles.open()
        await this.importFiles.addFiles([ 'FileUpload/Cat1.docx' ])
        await this.importFiles.finish({ name: 'Split FileUpload', splitByPage: true })
      })

      after(async function() {
        await this.documentSets.destroy('Split FileUpload')
      })

      shouldBehaveLikeATree({
        documents: [
          { type: 'pdf', title: 'Cat1.docx – page 1' },
          { type: 'pdf', title: 'Cat1.docx – page 2' },
          { type: 'pdf', title: 'Cat1.docx – page 3' },
        ],
        searches: [
          { query: 'face', nResults: 3 }
        ],
      })
    })
  })
})
