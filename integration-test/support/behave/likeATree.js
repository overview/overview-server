'use strict'

module.exports = function likeATree(opts) {
  describe('likeATree', function() {
    beforeEach(async function() {
      this.browser.loadShortcuts('documentSet')
      await this.browser.shortcuts.documentSet.waitUntilStable()
    })

    it('should show a document list title', async function() {
      const title = await this.browser.getText('#document-list-title')
      expect(title).to.match(/Found \d+ documents?/)
    })

    for (let document of (opts.documents || [])) {
      it(`should show a ${document.type} document with title ${document.title}`, async function() {
        const extra = {
          text: async () => {
            await this.browser.assertExists({ tag: 'pre', contains: document.contains, wait: 'pageLoad' })
          },

          pdf: async () => {
            await this.browser.assertExists('.pdf-document-view iframe')
          }
        }[document.type]

        await this.browser.click({ tag: 'h3', contains: document.title })
        await this.browser.sleep(500) // wait for animation
        await this.browser.assertExists({ tag: 'h2', contains: document.title, wait: true })

        await extra()
      })
    }

    for (const search of (opts.searches || [])) {
      it(`should search for ${search.query}`, async function() {
        await this.browser.sendKeys(search.query, '#document-list-params .search input[name=query]')
        await this.browser.click('#document-list-params .search button')
        await this.browser.assertExists({ tag: 'h3', contains: `${search.nResults} document`, wait: 'pageLoad' })
      })
    }

    for (const word of (opts.ignoredWords || [])) {
      it(`should show ignored word ${word}`, async function() {
        await this.browser.click('ul.view-tabs li.active .toggle-popover')
        await this.browser.assertExists({ tag: 'dd', contains: word })
        await this.browser.click('ul.view-tabs li.active .toggle-popover')
      })
    }

    for (const word of (opts.importantWords || [])) {
      it(`should show important word ${word}`, async function() {
        await this.browser.click('ul.view-tabs li.active .toggle-popover')
        await this.browser.assertExists({ tag: 'dd', contains: word })
        await this.browser.click('ul.view-tabs li.active .toggle-popover')
      })
    }
  })
}
