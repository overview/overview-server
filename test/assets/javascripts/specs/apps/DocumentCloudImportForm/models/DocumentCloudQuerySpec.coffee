PATH = 'apps/DocumentCloudImportForm/models/DocumentCloudQuery'

require [ 'jquery', 'backbone', PATH ], ($, Backbone, Query) ->
  describe PATH, ->
    describe 'url()', ->
      it 'should URL-encode the ID', ->
        model = new Query({ id: 'normal search' })
        expect(model.url()).toMatch(/.*normal%20search&/)

      it 'should treat projectid:... specially', ->
        model = new Query({ id: 'projectid:1-my-test-project' })
        expect(model.url()).toMatch(///^https://www.documentcloud.org/api/projects/1-my-test-project.json///)

      it 'should treat projectid: ... specially (with the space)', ->
        model = new Query({ id: 'projectid: 1-my-test-project' })
        expect(model.url()).toMatch(///^https://www.documentcloud.org/api/projects/1-my-test-project.json///)

    describe 'parse()', ->
      model = new Query({ id: 'projectid: 1-who-cares' })

      it 'should parse a BUG-57 + BUG-25 projectid search result', ->
        ret = model.parse({ projects: [
          { id: 2 },
          { id: 1, title: 'Who Cares', description: 'description', document_ids: [ '1-document' ] }
        ]})
        expect(ret.title).toEqual('Who Cares')
        expect(ret.description).toEqual('description')
        expect(ret.document_count).toEqual(1)

      it 'should parse a projectid search result', ->
        ret = model.parse({ project: {
          id: 1
          title: 'Who Cares'
          description: 'description'
          document_count: 10
        }})
        expect(ret.title).toEqual('Who Cares')
        expect(ret.description).toEqual('description')
        expect(ret.document_count).toEqual(10)

      it 'should parse an arbitrary search result', ->
        ret = model.parse({ total: 19582, page: 1, per_page: 0, q: 'obama', documents: [] })
        expect(ret.title).toEqual('obama')
        expect(ret.description).toEqual('')
        expect(ret.document_count).toEqual(19582)
