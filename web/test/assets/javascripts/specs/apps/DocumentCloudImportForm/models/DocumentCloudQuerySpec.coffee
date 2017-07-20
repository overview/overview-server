PATH = 'apps/DocumentCloudImportForm/models/DocumentCloudQuery'

define [ 'jquery', 'backbone', PATH ], ($, Backbone, Query) ->
  describe PATH, ->
    describe 'url()', ->
      it 'should URL-encode the ID', ->
        model = new Query({ id: 'normal search' }, { documentCloudUrl: 'https://www.documentcloud.org' })
        expect(model.url()).to.match(/.*normal%20search&/)

      it 'should treat projectid:... specially', ->
        model = new Query({ id: 'projectid:1-my-test-project' }, { documentCloudUrl: 'https://www.documentcloud.org' })
        expect(model.url()).to.match(///^https://www.documentcloud.org/api/projects/1-my-test-project.json///)

      it 'should treat projectid: ... specially (with the space)', ->
        model = new Query({ id: 'projectid: 1-my-test-project' }, { documentCloudUrl: 'https://www.documentcloud.org' })
        expect(model.url()).to.match(///^https://www.documentcloud.org/api/projects/1-my-test-project.json///)

      it 'should work with a custom DocumentCloud installation', ->
        query = new Query({ id: 'projectid:1' }, { documentCloudUrl: 'https://foo.bar' })
        expect(query.url()).to.eq('https://foo.bar/api/projects/1.json?include_document_ids=false')

    describe 'parse()', ->
      model = new Query({ id: 'projectid: 1-who-cares' }, { documentCloudUrl: 'https://www.documentcloud.org' })

      it 'should parse a BUG-57 + BUG-25 projectid search result', ->
        ret = model.parse({ projects: [
          { id: 2 },
          { id: 1, title: 'Who Cares', description: 'description', document_ids: [ '1-document' ] }
        ]})
        expect(ret.title).to.eq('Who Cares')
        expect(ret.description).to.eq('description')
        expect(ret.document_count).to.eq(1)

      it 'should parse a projectid search result', ->
        ret = model.parse({ project: {
          id: 1
          title: 'Who Cares'
          description: 'description'
          document_count: 10
        }})
        expect(ret.title).to.eq('Who Cares')
        expect(ret.description).to.eq('description')
        expect(ret.document_count).to.eq(10)

      it 'should parse an arbitrary search result', ->
        ret = model.parse({ total: 19582, page: 1, per_page: 0, q: 'obama', documents: [] })
        expect(ret.title).to.eq('obama')
        expect(ret.description).to.eq('')
        expect(ret.document_count).to.eq(19582)
