asUserWithDocumentSet = require('../support/asUserWithDocumentSet-new')
ApiBrowser = require('../lib/ApiBrowser')

describe 'Metadata', ->
  asUserWithDocumentSet 'Metadata/basic.csv', ->
    before ->
      @browser
        .loadShortcuts('importCsv')
        .loadShortcuts('api')

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
