asUser = require('../support/asUser-new')
shouldBehaveLikeATree = require('../support/behave/likeATree-new')

Url =
  index: '/documentsets'
  show: /\/documentsets\/(\d+)/
  csvUpload: '/imports/csv'
  publicDocumentSets: '/public-document-sets'

userToTrXPath = (email) -> "//tr[contains(td[@class='email'], '#{email}')]"

describe 'ExampleDocumentSets', ->
  asUser.usingTemporaryUser ->
    asUser.usingAdminBrowser ->
      before ->
        @adminBrowser
          .loadShortcuts('importCsv')
          .loadShortcuts('documentSet')
          .loadShortcuts('documentSets')

      describe 'after being set as an example', ->
        before ->
          @adminBrowser
            .shortcuts.importCsv.startUpload('CsvUpload/basic.csv')
            .shortcuts.importCsv.waitUntilRedirectToDocumentSet('basic.csv')
            .shortcuts.documentSet.waitUntilStable()
            .shortcuts.documentSet.setPublic(true)
            .then =>
              @browser.shortcuts.documentSets.clone('basic.csv')

        after ->
          Q.all([
            @browser.shortcuts.documentSets.destroy('basic.csv')
            @adminBrowser.shortcuts.documentSets.destroy('basic.csv')
          ])

        it 'should be cloneable',  ->
          @browser
            .get(Url.index)
            .assertExists(tag: 'h3', contains: 'basic.csv', wait: 'pageLoad')

        it 'should be removed from the example list when unset as an example', ->
          @adminBrowser.shortcuts.documentSet.setPublic(false)
            .then =>
              @browser
                .get(Url.publicDocumentSets)
                .assertExists(tag: 'p', contains: 'There are currently no example document sets.', wait: 'pageLoad')
            .then =>
              @adminBrowser.shortcuts.documentSet.setPublic(true)

        describe 'the cloned example', ->
          before ->
            @browser
              .shortcuts.documentSets.open('basic.csv')

          shouldBehaveLikeATree
            documents: [
              { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
            ]
            searches: [
              { query: 'document', nResults: 4 }
            ]

      it 'should keep clone after original is deleted', ->
        @adminBrowser
          .shortcuts.importCsv.startUpload('CsvUpload/basic.csv')
          .shortcuts.importCsv.waitUntilRedirectToDocumentSet('basic.csv')
          .shortcuts.documentSet.waitUntilStable()
          .shortcuts.documentSet.setPublic(true)
          .then =>
            @browser.shortcuts.documentSets.clone('basic.csv')
          .then =>
            @adminBrowser.shortcuts.documentSets.destroy('basic.csv')
          .then =>
            @browser
              .get(Url.index)
              .assertExists(tag: 'a', contains: 'basic.csv', wait: 'pageLoad')
          .then =>
            @browser.shortcuts.documentSets.destroy('basic.csv')
