asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')
ApiBrowser = require('../lib/ApiBrowser')

describe 'Metadata', ->
  asUserWithDocumentSet 'Metadata/basic.csv', ->
    before ->
      @browser
        .loadShortcuts('importCsv')
        .loadShortcuts('api')

    describe 'the Show page', ->
      before ->
        @locator = { css: '.metadata-json input[name=foo]' }
        @locatorWithWait = { css: '.metadata-json input[name=foo]', wait: 'fast' }

        @browser
          .click(tag: 'h3', contains: 'First')
          .click(link: 'Metadata', wait: 'fast')
          .find(@locatorWithWait)

      it 'should show default metadata', ->
        @browser
          .getAttribute(@locator, 'value').then((value) -> expect(value).to.eq('foo0'))

      it 'should show new metadata when browsing to a new document', ->
        @browser
          .click(css: '.document-nav a.next')
          .getAttribute(@locator, 'value').then((value) -> expect(value).to.eq('foo1'))
        # Reset state
        @browser
          .click(css: '.document-nav a.previous')
          .find(@locatorWithWait)

      it 'should modify metadata', ->
        @browser
          .clear(@locator)
          .sendKeys('newFoo', @locator)
          .click(css: '.document-nav a.next')
          .click(css: '.document-nav a.previous')
          .getAttribute(@locatorWithWait, 'value').then((value) -> expect(value).to.eq('newFoo'))

        # Reset state
        @browser
          .clear(@locator)
          .sendKeys('foo0', @locator)
          # Browse away and back, to ensure we've saved the value
          .click(css: '.document-nav a.next')
          .click(css: '.document-nav a.previous')

    describe 'GET /documents', ->
      it 'should return metadata when requested', ->
        @browser.getUrl()
          .then((url) -> /^([^:]+:\/\/[^\/]+)\/documentsets\/(\d+)/.exec(url))
          .then ([ __, host, id ]) =>
            @browser.shortcuts.api.createApiToken(id, 'metadata').then((apiToken) -> [ host, id, apiToken ])
          .then ([ host, id, apiToken ]) ->
            api = new ApiBrowser(baseUrl: "#{host}/api/v1", apiToken: apiToken)
            api.GET("/document-sets/#{id}/documents?fields=metadata")
          .then (r) ->
            # Body items are sorted by title, and titles are alphabetical in basic.csv
            r.body.items.map((i) -> i.metadata).should.deep.eq([
              { foo: 'foo0', bar: 'bar0' }
              { foo: 'foo1', bar: 'bar1' }
              { foo: 'foo2', bar: 'bar2' }
            ])
