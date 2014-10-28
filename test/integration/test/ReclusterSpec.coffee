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
        .waitForElementBy(tag: 'a', contains: 'Add view').click()
        .waitForElementBy(tag: 'a', contains: 'Tree').click()
        .waitForElementBy(tag: 'input', name: 'tree_title', visible: true).type('view1')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .sleep(100) # Overview will select the new Job; wait for that to happen
        .waitForElementByCss('#tree-app-tree canvas', 5000) # the Job will become a View

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
        .waitForElementBy(tag: 'a', contains: 'Add view').click()
        .waitForElementBy(tag: 'a', contains: 'Tree').click()
        .waitForElementBy(tag: 'option', contains: 'foo').click()
        .elementBy(tag: 'input', name: 'tree_title', visible: true).type('view2')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .sleep(100) # Overview will select the new Job; wait for that to happen
        .waitForElementByCss('#tree-app-tree canvas', 5000) # the Job will become a View

    shouldBehaveLikeATree
      documents: [
        { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
      ]
      searches: [
        { query: 'document', nResults: 3 }
      ]

  it 'should let us delete a new tree, especially in these tests'
