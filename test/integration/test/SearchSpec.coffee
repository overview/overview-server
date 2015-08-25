asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
testMethods = require('../support/testMethods')

describe 'Search', ->
  asUserWithDocumentSet('Search', 'Search/documents1.csv')

  before ->
    @userBrowser
      .elementByCss('#document-list-params .search input[name=query]').type('word')
      .elementByCss('#document-list-params .search button').click()

  it 'should highlight all instances of the search term', ->
    @userBrowser
      .waitForElementByCss('li.document h3').click()
      # The document slides over; wait for all highlights to be visible
      .waitForElementBy(tag: 'em', class: 'highlight', index: 1, visible: true).should.eventually.exist
      .waitForElementBy(tag: 'em', class: 'highlight', index: 2, visible: true).should.eventually.exist
      .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
      .elementsByCss('article em.highlight').at(0).text().should.become('word')
      .elementsByCss('article em.highlight').at(1).text().should.become('word')
      .elementsByCss('article em.highlight').at(2).text().should.become('word')
      .elementByCss('a.back-to-list').click()

  it 'should scroll through search terms in text mode', ->
    @userBrowser
      .waitForElementByCss('li.document h3').click()
      .waitForElementBy(tag: 'em', class: 'highlight', index: 1, visible: true).should.eventually.exist
      .waitForElementBy(tag: 'em', class: 'highlight', index: 2, visible: true).should.eventually.exist
      .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
      .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight current')
      .elementByCss('article a.next-highlight').click()
      .elementByCss('article div.find').text().should.eventually.contain('Highlighting match 2 of 3')
      .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight')
      .elementBy(tag: 'em', class: 'highlight', index: 2).getAttribute('class').should.become('highlight current')
      .elementByCss('a.back-to-list').click()

  # TODO test in PDF mode. Would be easier if our framework let us start off
  # with a PDF document set
