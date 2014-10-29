asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')

Url =
  index: '/documentsets'

describe 'Recluster', ->
  asUserWithDocumentSet('Recluster', 'Recluster/documents.csv')
  doDelete = (browser, title) ->
    browser
      .goToFirstDocumentSet()
      .waitForElementBy(tag: 'a', contains: title, visible: true)
      .elementByCss('>', '.toggle-popover').click()
      .listenForJqueryAjaxComplete()
      .acceptingNextAlert()
      .waitForElementByCss('.popover.in button.delete').click()
      .waitForJqueryAjaxComplete()

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

    it 'should delete properly', ->
      doDelete(@userBrowser, 'view1')
        .waitForElementByCss('#tree-app-tree canvas').should.eventually.exist # it selects the next tree
        .elementByCss('.view-tabs li.tree').text().should.not.eventually.contain('view1') # it deletes the tab
        .goToFirstDocumentSet()
        .waitForElementByCss('.view-tabs li.tree').text().should.not.eventually.contain('view1') # it stays deleted

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

    after -> doDelete(@userBrowser, 'view2')

    shouldBehaveLikeATree
      documents: [
        { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
      ]
      searches: [
        { query: 'document', nResults: 3 }
      ]
