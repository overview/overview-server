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
        .waitForElementBy(tag: 'a', contains: 'New tree…').click()
        .waitForElementBy(tag: 'input', name: 'tree_title', visible: true).type('viz1')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForElementBy(tag: 'li', class: 'viz', contains: 'viz1', visible: true)
        .elementBy(tag: 'a', contains: 'viz1').click()

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
        .waitForElementBy(tag: 'a', contains: 'New tree…').click()
        .waitForElementBy(tag: 'option', contains: 'foo').click()
        .elementBy(tag: 'input', name: 'tree_title', visible: true).type('viz2')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForElementBy(tag: 'li', class: 'viz', contains: 'viz2', visible: true)
        .elementBy(tag: 'a', contains: 'viz2').click()
        .waitForElementByCss('canvas')

    shouldBehaveLikeATree
      documents: [
        { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
      ]
      searches: [
        { query: 'document', nResults: 3 }
      ]

  it 'should let us delete a new tree, especially in these tests'
