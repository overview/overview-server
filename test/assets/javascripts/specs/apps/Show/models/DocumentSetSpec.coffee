define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentSet'
], (_, Backbone, DocumentSet) ->
  describe 'apps/Show/models/DocumentSet', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub(Backbone, 'ajax')

      @documentSet = new DocumentSet(id: 12)

    afterEach ->
      @sandbox.restore()

    it 'should use the proper URL', ->
      expect(_.result(@documentSet, 'url')).to.eq('/documentsets/12.json')

    it 'should give tags the proper URL', ->
      expect(_.result(@documentSet.tags, 'url')).to.eq('/documentsets/12/tags')

    it 'should give views the proper URL', ->
      expect(_.result(@documentSet.views, 'url')).to.eq('/documentsets/12/views')

    describe 'fetch', ->
      beforeEach ->
        @sampleResponse =
          metadataSchema: { version: 1, fields: [] }
          tags: []
          views: []
          nDocuments: 1

        @withAjaxResponse = (data, cb) ->
          @documentSet.fetch()
          @documentSet.once('sync', cb) # attach handler before event happens
          expect(Backbone.ajax).to.have.been.called
          Backbone.ajax.args[0][0].success(data)

      it 'should fetch tags from the server', (done) ->
        response = _.extend(@sampleResponse, tags: [
          { id: 1, name: 'aaa' }
          { id: 2, name: 'bbb' }
          { id: 3, name: 'ccc' }
        ])
        @withAjaxResponse response, =>
          expect(@documentSet.tags.pluck('name')).to.deep.eq([ 'aaa', 'bbb', 'ccc' ])
          done()

      it 'should fetch views from the server', (done) ->
        response = _.extend(@sampleResponse, views: [ { id: 456, type: 'view' } ])
        @withAjaxResponse response, =>
          expect(@documentSet.views.pluck('type')).to.deep.eq([ 'view' ])
          done()

      it 'should fetch nDocuments from the server', (done) ->
        response = _.extend(@sampleResponse, nDocuments: 321)
        @withAjaxResponse response, =>
          expect(@documentSet.nDocuments).to.eq(321)
          done()

      it 'should parse metadataFields from the server', (done) ->
        response = _.extend(@sampleResponse, metadataSchema:
          version: 1
          fields: [
            { name: 'foo', type: 'String' }
            { name: 'bar', type: 'String' }
          ]
        )
        @withAjaxResponse response, =>
          expect(@documentSet.get('metadataFields')).to.deep.eq([ 'foo', 'bar' ])
          done()
