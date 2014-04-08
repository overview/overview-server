asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
wd = require('wd')

describe 'TreeTour', ->
  describe 'with tooltips enabled', ->
    asUserWithDocumentSet('TreeTour with tooltips enabled', 'TreeTooltips/documents.csv')

    it 'should show a tooltip on first load', ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy({ class: 'popover', contains: 'folders' }, 10000).should.eventually.exist

    it 'should show a second tooltip after clicking the first one', ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy({ class: 'popover', contains: 'folders' }, 10000).elementByCss('>', 'a.next').click()
        .waitForElementBy({ class: 'popover', contains: 'folders' }, 10000).elementByCss('>', 'a.next').click()
        .waitForElementBy({ class: 'popover', contains: 'document list' }, 10000).should.eventually.exist

  describe 'after reading through all the tooltips', ->
    asUserWithDocumentSet('TreeTour after reading through all the tooltips', 'TreeTooltips/documents.csv')

    it 'should only show the tooltips the first time', ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy({ class: 'popover', contains: 'folders' }, 10000).elementByCss('>', 'a.skip').click()
        .elementByOrNull(class: 'popover', contains: 'folders').should.not.eventually.exist
        .goToFirstDocumentSet()
        .elementByOrNull(class: 'popover', contains: 'folders').should.not.eventually.exist
