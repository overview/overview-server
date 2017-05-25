asUser = require('../support/asUser-new')

# In order to let users analyze document test
# We need to read text that comes from images
describe 'Ocr', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser
        .loadShortcuts('documentSets')
        .loadShortcuts('importFiles')
        .execute -> localStorage.setItem('apps/DocumentDisplay/models/Preferences/text', 'true')

    describe 'when the user uploads with OCR', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles(['Ocr/image.pdf'])
          .click(button: 'Done adding files')
          .sendKeys('with ocr', tag: 'input', name: 'name', wait: true) # wait for dialog to appear
          .click(tag: 'label', contains: 'OCR')
          .click(button: 'Import documents')
          .shortcuts.importFiles.waitUntilRedirectToDocumentSet('with ocr')

      after ->
        @browser.shortcuts.documentSets.destroy('with ocr')

      it 'should have text', ->
        @browser
          .click(tag: 'h3', contains: 'image.pdf')
          .assertExists(tag: 'pre', contains: 'This is an image of text', wait: true) # wait for load+animate

    describe 'when the user uploads without OCR', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles(['Ocr/image.pdf'])
          .click(button: 'Done adding files')
          .sendKeys('without ocr', tag: 'input', name: 'name', wait: true) # wait for dialog to appear
          .click(tag: 'label', contains: 'Assume documents are already text')
          .click(button: 'Import documents')
          .shortcuts.importFiles.waitUntilRedirectToDocumentSet('without ocr')

      after ->
        @browser.shortcuts.documentSets.destroy('without ocr')

      it 'should have no text', ->
        @browser
          .click(tag: 'h3', contains: 'image.pdf')
          .assertExists(css: 'article pre', wait: true) # wait for load+animate
          .assertNotExists(tag: 'pre', contains: 'This is an image of text')
