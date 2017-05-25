asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')

describe 'TreeTour', ->
  describe 'with tooltips enabled', ->
    asUserWithDocumentSet 'TreeTooltips/documents.csv', ->
      it 'should show a tooltip on first load', ->
        @browser
          .assertExists(class: 'popover', contains: 'Document list')

      it 'should flip through tooltips', ->
        # On first load, the tree hasn't had a chance to get built. That means
        # the "Folders" part of the tour won't be there yet. This is a known
        # bug. Our solution is to reload the page after the tree is built. (The
        # tree is built by the time we reach the start of this test.)
        @browser.loadShortcuts('documentSets')

        @browser
          .shortcuts.documentSets.open('documents.csv') # reload
          .assertExists(class: 'popover', contains: 'Document list')
          .click([ { class: 'popover' }, { link: 'Next' } ])
          .assertExists(class: 'popover', contains: 'Tagging')
          .click([ { class: 'popover' }, { link: 'Next' } ])
          .assertExists(class: 'popover', contains: 'Select')
          .click([ { class: 'popover' }, { link: 'Next' } ])
          .assertExists(class: 'popover', contains: 'Folders')
          .click([ { class: 'popover' }, { link: 'Next' } ])
          .assertExists(class: 'popover', contains: 'Folders') # again
          .click([ { class: 'popover' }, { link: 'Done' } ])
          .assertNotExists(class: 'popover')

  describe 'after reading through all the tooltips', ->
    asUserWithDocumentSet 'TreeTooltips/documents.csv', ->
      it 'should only show the tooltips the first time', ->
        @browser.loadShortcuts('documentSets')

        @browser
          .click([ { class: 'popover' }, { link: '×' } ])
          .shortcuts.documentSets.open('documents.csv')
          .assertNotExists(class: 'popover')
