asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')

describe 'Search', ->
  asUserWithDocumentSet 'Search/documents1.csv', ->
    before ->
      @browser
        .sendKeys('word', css: '#document-list-params .search input[name=query]')
        .click(css: '#document-list-params .search button')

    it 'should highlight all instances of the search term', ->
      @browser
        .click(css: 'li.document h3', wait: 'pageLoad')
        # The document slides over; wait for all highlights to be visible
        .assertExists(tag: 'em', class: 'highlight', index: 1, wait: 'fast')
        .assertExists(tag: 'em', class: 'highlight', index: 2, wait: 'fast')
        .assertExists(tag: 'em', class: 'highlight', index: 3, wait: 'fast')
        .then => @browser.getText(tag: 'em', class: 'highlight', index: 1).should.eventually.eq('word')
        .then => @browser.getText(tag: 'em', class: 'highlight', index: 2).should.eventually.eq('word')
        .then => @browser.getText(tag: 'em', class: 'highlight', index: 3).should.eventually.eq('word')
        .then => @browser.click(css: 'a.back-to-list')

    # it 'should scroll through search terms in text mode', ->
    #   @userBrowser
    #     .waitForElementByCss('li.document h3').click()
    #     .waitForElementBy(tag: 'em', class: 'highlight', index: 1, visible: true).should.eventually.exist
    #     .waitForElementBy(tag: 'em', class: 'highlight', index: 2, visible: true).should.eventually.exist
    #     .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
    #     .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight current')
    #     .elementByCss('article a.next-highlight').click()
    #     .elementByCss('article div.find').text().should.eventually.contain('Highlighting match 2 of 3')
    #     .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight')
    #     .elementBy(tag: 'em', class: 'highlight', index: 2).getAttribute('class').should.become('highlight current')
    #     .elementByCss('a.back-to-list').click()

    # TODO test in PDF mode. Would be easier if our framework let us start off
    # with a PDF document set
