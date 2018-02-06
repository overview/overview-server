'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('Search', function() {
  async function search(browser, query) {
    // CSS transitions to wait for are described inline
    //
    // We need to wait for transitions so we can wait for _other_ transitions later
    // (e.g., openDocumentFromList's transitions)

    await browser.awaitingCssTransitions(5, async () => {
      // focus input: transition border-color-* (4), box-shadow (1)
      await browser.clear('#document-list-params .search input[name=query]')
      await browser.sendKeys(query, '#document-list-params .search input[name=query]')
    })

    // [adamhooper, 2018-01-05] We have to wait for the _focus_ animation to
    // finish before we can wait for the _blur_ animation, otherwise we get a
    // race because the one animation ends where it starts.
    await browser.awaitingCssTransitions(5, async () => {
      await browser.click('#document-list-params .search button') // this removes focus
    })
  }

  asUserWithDocumentSet('Search/documents1.csv', function() {
    it('should highlight all instances of the search term', async function() {
      await search(this.browser, 'word')
      await this.browser.assertExists({ css: 'li.document h3', wait: 'pageLoad' })

      await this.browser.shortcuts.documentSet.openDocumentFromList('First')

      // Wait for three highlights of "word" to load
      await this.browser.assertExists({ xpath: '//pre/em[text()="word"][position()=3]', wait: 'pageLoad' })
    })

    it('should regex search', async function() {
      await search(this.browser, 'text:/( .çi)/ AND title:Third')
      await this.browser.assertExists({ tag: 'h3', contains: 'Third', wait: 'fast' })
    })

    it('should warn on invalid regex', async function() {
      await search(this.browser, 'text:/(foo/')
      const text = await this.browser.getText({ css: '#document-list ul.warnings li', wait: 'pageLoad' })
      expect(text).to.match(/Overview ignored your regular expression, “\(foo”, because of a syntax error: missing closing \)\./)
    })

    it('should warn when nesting a regex', async function() {
      await search(this.browser, '"and" OR /\\bth.*/')
      const text = await this.browser.getText({ css: '#document-list ul.warnings li', wait: 'pageLoad' })
      expect(text).to.match(/Overview assumed all documents match your regular expression, “\\bth\.\*”, because the surrounding search is too complex\. Rewrite your search so the regular expression is outside any OR or NOT\(AND\(\.\.\.\)\) clauses\./)
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
      await search(this.browser, 'phrase OR (phrase foo*)')

      const text = await this.browser.getText({ css: '#document-list ul.warnings li', wait: 'pageLoad' })
      expect(text).to.eq('This list may be incomplete. “text:foo*” matched too many words from your document set; we limited our search to 1,000 words.')
    })
  })
})
