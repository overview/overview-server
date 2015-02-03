asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
testMethods = require('../support/testMethods')

Url =
  index: '/documentsets'

describe 'Search', ->
  asUserWithDocumentSet('Search', 'Search/documents1.csv')

  it 'should highlight all instances of the search term', ->
    @userBrowser
      .goToFirstDocumentSet()
      .elementByCss('#tree-app-search [name=query]').type('word')
      .elementByCss('#tree-app-search button[type=submit]').click()
      .waitForElementByCss('li.document h3').click()
      # The document slides over; wait for the last highlight to be visible
      .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
      .elementsByCss('article em.highlight').at(0).text().should.become('word')
      .elementsByCss('article em.highlight').at(1).text().should.become('word')
      .elementsByCss('article em.highlight').at(2).text().should.become('word')

  it 'should scroll through search terms', ->
    @userBrowser
      .goToFirstDocumentSet()
      .elementByCss('#tree-app-search [name=query]').type('word')
      .elementByCss('#tree-app-search button[type=submit]').click()
      .waitForElementByCss('li.document h3').click()
      .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
      .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight current')
      .elementByCss('article a.next-highlight').click()
      .elementByCss('article div.find').text().should.eventually.contain('Highlighting match 2 of 3')
      .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight')
      .elementBy(tag: 'em', class: 'highlight', index: 2).getAttribute('class').should.become('highlight current')
