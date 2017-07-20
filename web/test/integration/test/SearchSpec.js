'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('Search', function() {
  asUserWithDocumentSet('Search/documents1.csv', function() {
    before(async function() {
      await this.browser.sendKeys('word', '#document-list-params .search input[name=query]')
      await this.browser.click('#document-list-params .search button')
    })

    it('should highlight all instances of the search term', async function() {
      await this.browser.click({ css: 'li.document h3', wait: 'pageLoad' })

      // The document slides over; wait for all highlights to be visible
      await this.browser.assertExists({ tag: 'em', class: 'highlight', index: 1, wait: 'fast' })
      await this.browser.assertExists({ tag: 'em', class: 'highlight', index: 2, wait: 'fast' })
      await this.browser.assertExists({ tag: 'em', class: 'highlight', index: 3, wait: 'fast' })

      const text1 = await this.browser.getText({ tag: 'em', class: 'highlight', index: 1, wait: 'fast' })
      expect(text1).to.eq('word')
      const text2 = await this.browser.getText({ tag: 'em', class: 'highlight', index: 2, wait: 'fast' })
      expect(text2).to.eq('word')
      const text3 = await this.browser.getText({ tag: 'em', class: 'highlight', index: 3, wait: 'fast' })
      expect(text3).to.eq('word')

      await this.browser.click('a.back-to-list')
    })

    // it 'should scroll through search terms in text mode', ->
    //   @userBrowser
    //     .waitForElementByCss('li.document h3').click()
    //     .waitForElementBy(tag: 'em', class: 'highlight', index: 1, visible: true).should.eventually.exist
    //     .waitForElementBy(tag: 'em', class: 'highlight', index: 2, visible: true).should.eventually.exist
    //     .waitForElementBy(tag: 'em', class: 'highlight', index: 3, visible: true).should.eventually.exist
    //     .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight current')
    //     .elementByCss('article a.next-highlight').click()
    //     .elementByCss('article div.find').text().should.eventually.contain('Highlighting match 2 of 3')
    //     .elementBy(tag: 'em', class: 'highlight', index: 1).getAttribute('class').should.become('highlight')
    //     .elementBy(tag: 'em', class: 'highlight', index: 2).getAttribute('class').should.become('highlight current')
    //     .elementByCss('a.back-to-list').click()

    // TODO test in PDF mode. Would be easier if our framework let us start off
    // with a PDF document set
  })

  asUserWithDocumentSet('Search/manyTermsWithSamePrefix.csv', function() {
    it('should warn when a prefix search has a truncated term list', async function() {
      await this.browser.sendKeys('phrase OR (phrase foo*)', '#document-list-params .search input[name=query]')
      await this.browser.click('#document-list-params .search button')

      const text = await this.browser.getText({ css: '#document-list ul.warnings li', wait: 'pageLoad' })
      expect(text).to.eq('This list may be incomplete. “text:foo*” matched too many words from your document set; we limited our search to 1,000 words.')
    })
  })
})
