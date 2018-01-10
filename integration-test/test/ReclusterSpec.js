'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
const shouldBehaveLikeATree = require('../support/behave/likeATree')

const Url = {
  index: '/documentsets',
}

describe('Recluster', function() {
  asUserWithDocumentSet('Recluster/documents.csv', function() {
    beforeEach(function() {
      this.browser.loadShortcuts('documentSet')
      this.browser.loadShortcuts('documentSets')
      this.documentSet = this.browser.shortcuts.documentSet
      this.documentSets = this.browser.shortcuts.documentSets
    })

    describe('after a recluster', function() {
      beforeEach(async function() {
        await this.documentSet.recluster('view1')
      })

      it('should rename properly', async function() {
        await this.documentSet.renameView('view1', 'view3')
        await this.browser.assertExists({ link: 'view3', wait: true })
        await this.browser.click([ { link: 'view3' }, { class: 'toggle-popover' } ])
        await this.browser.assertExists({ tag: 'dd', class: 'title', contains: 'view3', wait: true })

        // persists even after page load
        await this.browser.refresh()
        await this.documentSet.waitUntilStable()
        await this.browser.assertExists({ link: 'view3' })
      })

      shouldBehaveLikeATree({
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ],
        searches: [
          { query: 'document', nResults: 4 }
        ],
      })
    })

    describe('when reclustering just a tag', function() {
      beforeEach(async function() {
        await this.documentSets.open('documents.csv')
        await this.documentSet.recluster('view2', { tag: 'foo' })
      })

      shouldBehaveLikeATree({
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ],
        searches: [
          { query: 'document', nResults: 4 } // Shows results outside that tag
        ],
      })
    })
  })
})
