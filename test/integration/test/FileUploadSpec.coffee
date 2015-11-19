asUser = require('../support/asUser-new')
shouldBehaveLikeATree = require('../support/behave/likeATree-new')

describe 'FileUpload', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser
        .loadShortcuts('documentSets')
        .loadShortcuts('importFiles')

    describe 'after creating a file with metadata', ->
      before ->
        # Add a file, with metadata 'foo': 'bar'
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles(['FileUpload/Cat1.docx'])
          .click(button: 'Done adding files')
          .sendKeys('metadata-test', tag: 'input', name: 'name', wait: true) # wait for dialog to appear
          .click(link: 'Add new field')
          .sendKeys('foo', tag: 'input', id: 'document-metadata-add-field')
          .click(css: '#document-metadata-add-field + button')
          .sendKeys('bar', tag: 'input', name: 'foo')
          .click(button: 'Import documents')
          .shortcuts.importFiles.waitUntilRedirectToDocumentSet('metadata-test')

      it 'should add metadata to the imported file', ->
        @browser
          .click(tag: 'h3', contains: 'Cat1.docx')
          .click(link: 'Fields', wait: true) # wait for document to appear
          .getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value') # wait for metadata to appear
            .then((value) -> expect(value).to.eq('bar'))

      it 'should add more metadata in a second import', ->
        # Add another file, with metadata 'moo':'mar'
        @browser
          .click(link: 'metadata-test')
          .click(link: 'Add documents')
          .shortcuts.importFiles.waitUntilImportPageLoaded()
          .shortcuts.importFiles.addFiles(['FileUpload/Cat2.txt'])
          .click(button: 'Done adding files')
          .click(link: 'Add new field', wait: true) # wait for dialog to appear
          .sendKeys('moo', tag: 'input', id: 'document-metadata-add-field')
          .click(css: '#document-metadata-add-field + button')
          .sendKeys('mar', tag: 'input', name: 'moo')
          .click(button: 'Import documents')
          .shortcuts.importFiles.waitUntilRedirectToDocumentSet('metadata-test')

        # Check the first document still has 'foo':'bar'
        @browser
          .click(tag: 'h3', contains: 'Cat1.docx')
          .click(link: 'Fields', wait: true) # wait for document to appear
          .getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value') # wait for metadata to appear
            .then((value) -> expect(value).to.eq('bar'))
        @browser
          .getAttribute({ tag: 'input', name: 'moo' }, 'value')
            .then((value) -> expect(value).to.eq(''))

        # Check the second document has 'moo':'mar'
        # Avoid a race: how do we detect the second document has loaded? By
        # navigating away from the first document, waiting for it to disappear,
        # navigating to the second, and waiting for it to load.
        @browser
          .click(link: 'Back to list')
          .click(tag: 'h3', contains: 'Cat2.txt', wait: 'fast')
          .getAttribute({ tag: 'input', name: 'foo', wait: true }, 'value') # wait for metadata to appear
            .then((value) -> expect(value).to.eq(''))
        @browser
          .getAttribute({ tag: 'input', name: 'moo' }, 'value')
            .then((value) -> expect(value).to.eq('mar'))

      after ->
        @browser
          .shortcuts.documentSets.destroy('metadata-test')

    describe 'after uploading files', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles([
            'FileUpload/Cat1.docx'
            'FileUpload/Cat2.txt'
            'FileUpload/Cat3.rtf'
            'FileUpload/Cat4.html'
            'FileUpload/Jules1.doc'
            #'FileUpload/Jules2.pptx' # XXX this triggers a Selenium bug: the upload fails
            'FileUpload/Jules3.xlsx'
          ])
          .shortcuts.importFiles.finish(name: 'FileUpload')

      after ->
        @browser
          .shortcuts.documentSets.destroy('FileUpload')

      shouldBehaveLikeATree
        documents: [
          { type: 'pdf', title: 'Cat1.docx' }
          { type: 'pdf', title: 'Cat2.txt' }
          { type: 'pdf', title: 'Cat3.rtf' }
          { type: 'pdf', title: 'Cat4.html' }
          { type: 'pdf', title: 'Jules1.doc' }
          #{ type: 'pdf', title: 'Jules2.pptx' }
          { type: 'pdf', title: 'Jules3.xlsx' }
        ]
        searches: [
          { query: 'chase', nResults: 4 }
        ]

    describe 'after splitting a file into pages', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles([ 'FileUpload/Cat1.docx' ])
          .shortcuts.importFiles.finish(name: 'Split FileUpload', splitByPage: true)

      after ->
        @browser
          .shortcuts.documentSets.destroy('Split FileUpload')

      shouldBehaveLikeATree
        documents: [
            { type: 'pdf', title: 'Cat1.docx – page 1' }
            { type: 'pdf', title: 'Cat1.docx – page 2' }
            { type: 'pdf', title: 'Cat1.docx – page 3' }
        ]
        searches: [
          { query: 'face', nResults: 3 }
        ]
