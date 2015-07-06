asUser = require('../support/asUser-new')
shouldBehaveLikeATree = require('../support/behave/likeATree-new')

describe 'CsvUpload', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser
        .loadShortcuts('importCsv')
        .loadShortcuts('documentSet')
        .loadShortcuts('documentSets')

    describe 'finding a character set', ->
      it 'should load UTF-8', ->
        @browser
          .shortcuts.importCsv.openAndChooseFile('CsvUpload/basic-utf8.csv')
          .getText(css: '.preview table').then((text) -> expect(text).to.contain('achète avec des €'))

      it 'should load Windows-1252', ->
        @browser
          .shortcuts.importCsv.openAndChooseFile('CsvUpload/basic-windows-1252.csv')
          .assertExists(css: '.requirements li.text.bad')
          .getText(css: '.preview table').then((text) -> expect(text).to.contain('ach�te avec des �'))

        @browser
          .click(css: 'select[name=charset] option[value=windows-1252]')
          .getText(css: '.preview table').then((text) -> expect(text).to.contain('achète avec des €'))

      it 'should reset the form, including encoding', ->
        @browser
          .shortcuts.importCsv.openAndChooseFile('CsvUpload/basic-windows-1252.csv')
          .click(css: 'select[name=charset] option[value=windows-1252]')
          .click(button: 'Reset')
          .shortcuts.importCsv.chooseFile('CsvUpload/basic-utf8.csv')
          .getText(css: '.preview table').then((text) -> expect(text).to.contain('achète avec des €'))

      it 'should show an error when there is no text column', ->
        @browser
          .shortcuts.importCsv.openAndChooseFile('CsvUpload/basic-no-text.csv')
          .assertExists(css: '.requirements li.header.bad')

    describe 'after uploading a document set', ->
      before ->
        @browser
          .shortcuts.importCsv.startUpload('CsvUpload/basic.csv')
          .shortcuts.importCsv.waitUntilRedirectToDocumentSet('CsvUpload/basic.csv')
          .shortcuts.documentSet.waitUntilStable()

      after ->
        @browser.shortcuts.documentSets.destroy('basic.csv')

      it 'should show the document set', ->
        @browser.assertExists(tag: 'h2', contains: 'basic.csv')

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', text: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 4 }
        ]
