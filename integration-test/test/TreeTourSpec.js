'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('TreeTour', function() {
  describe('with tooltips enabled', function() {
    asUserWithDocumentSet('TreeTooltips/documents.csv', { dismissTour: false }, function() {
      it('should show a tooltip on first load', async function() {
        await this.browser.assertExists({ class: 'popover', contains: 'Document list' })
      })

      it('should flip through tooltips', async function() {
        // On first load, the tree hasn't had a chance to get built. That means
        // the "Folders" part of the tour won't be there yet. This is a known
        // bug. Our solution is to reload the page after the tree is built. (The
        // tree is built by the time we reach the start of this test.)
        this.browser.loadShortcuts('documentSets')

        const documentSets = this.browser.shortcuts.documentSets

        await documentSets.open('documents.csv') // reload

        // Flip through all but the last tooltip
        for (const title of [ 'Document list', 'Tagging' ]) {
          await this.browser.assertExists({ class: 'popover', contains: title })
          await this.browser.click([ { class: 'popover' }, { link: 'Next' } ])
        }
        // The last tooltip
        await this.browser.assertExists({ class: 'popover', contains: 'Select' })

        // click Done -> the tooltips should go away
        await this.browser.click([ { class: 'popover' }, { link: 'Done' } ])
        await this.browser.assertNotExists({ class: 'popover' })
      })
    })
  })

  describe('after reading through all the tooltips', function() {
    asUserWithDocumentSet('TreeTooltips/documents.csv', function() {
      it('should only show the tooltips the first time', async function() {
        this.browser.loadShortcuts('documentSets')

        await this.browser.click([ { class: 'popover' }, { link: '×' } ])
        await this.browser.shortcuts.documentSets.open('documents.csv')
        await this.browser.assertNotExists({ class: 'popover' })
      })
    })
  })
})
