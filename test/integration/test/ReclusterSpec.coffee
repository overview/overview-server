asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')
shouldBehaveLikeATree = require('../support/behave/likeATree-new')

Url =
  index: '/documentsets'

describe 'Recluster', ->
  asUserWithDocumentSet 'Recluster/documents.csv', ->
    describe 'after a recluster', ->
      before ->
        @browser.shortcuts.documentSet.recluster('view1')

      after ->
        @browser.shortcuts.documentSet.destroyView('view1')

      it 'should rename properly', ->
        @browser
          .shortcuts.documentSet.renameView('view1', 'view3')
          .assertExists(link: 'view3', wait: true)
          .click([ { link: 'view3' }, { class: 'toggle-popover' } ])
          .assertExists(tag: 'dd', class: 'title', contains: 'view3')
          .refresh()
          .shortcuts.documentSet.waitUntilStable()
          .assertExists(link: 'view3') # stays even after page load
          .shortcuts.documentSet.renameView('view3', 'view1')

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 4 }
        ]

    describe 'when reclustering just a tag', ->
      before ->
        @browser
          .shortcuts.documentSets.open('documents.csv')
          .shortcuts.documentSet.recluster('view2', tag: 'foo')

      after ->
        @browser.shortcuts.documentSet.destroyView('view2')

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 4 } # Shows results outside that tag
        ]
