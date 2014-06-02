asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')

Url =
  index: '/documentsets'

describe 'Recluster', ->
  asUserWithDocumentSet('Recluster', 'Recluster/documents.csv')

  describe 'after a recluster', ->
    before ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy(tag: 'a', contains: 'New tree').click()
        .waitForElementBy(tag: 'input', name: 'tree_title', visible: true).type('New viz')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForUrl(Url.index, 10000)
        .sleep(5000) # async requests can time out; this won't
        .waitForJobsToComplete()

    describe 'in the new tree', ->
      before ->
        @userBrowser
          .get(Url.index)
          .waitForElementBy(tag: 'a', contains: 'New viz', visible: true).click()

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 4 }
        ]

  describe 'when reclustering just a tag', ->
    before ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy(tag: 'a', contains: 'New tree').click()
        .waitForElementBy(tag: 'option', contains: 'foo').click()
        .elementBy(tag: 'input', name: 'tree_title', visible: true).type('New tree')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForUrl(Url.index, 10000)
        .sleep(5000) # async requests can time out; this won't
        .waitForJobsToComplete()

    describe 'in the new tree', ->
      before ->
        @userBrowser
          .get(Url.index)
          .waitForElementBy(tag: 'a', contains: 'New tree', visible: true).click()

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 3 }
        ]

  it 'should let us delete a new tree, especially in these tests'
