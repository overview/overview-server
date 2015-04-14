define [
  'backbone'
  'apps/Show/models/State'
  'i18n'
], (Backbone, State, i18n) ->
  describe 'apps/Show/models/State', ->
    afterEach ->
      @state?.stopListening()

    describe 'setDocumentListParams', ->
      beforeEach ->
        i18n.reset_messages
          'views.DocumentSet.show.DocumentListParams.all': 'hack'
          'views.DocumentSet.show.DocumentListParams.q': 'hack2'
          'views.DocumentSet.show.DocumentListParams.node': 'hack3'
          'views.DocumentSet.show.DocumentListParams.tag': 'hack4'

        @params1 =
          equals: sinon.stub().returns(false)
          params: {}

      it 'should do nothing when setting to equivalent documentList', ->
        state = new State({}, documentSetId: 'xxx', transactionQueue: 'xxx')
        state.setDocumentListParams(@params1)
        state.set(document: 'foo')
        @params1.equals.returns(true)
        state.on('all', spy = sinon.spy())
        state.setDocumentListParams(@params1)
        expect(spy).not.to.have.been.called
        expect(state.get('document')).to.eq('foo')

      it 'should change document to null when changing documentList', ->
        state = new State({ document: 'foo' }, documentSetId: 'xxx', transactionQueue: 'xxx')
        state.setDocumentListParams(@params1)
        @params1.equals.returns(false)
        state.setDocumentListParams(@params1)
        expect(state.get('document')).to.be.null

      it 'should change highlightedDocumentListParams to the new value when changing documentList to a tag', ->
        state = new State({ highlightedDocumentListParams: @params1 }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.equals.returns(false)
        state.setDocumentListParams(tags: [ 1 ])
        expect(state.get('highlightedDocumentListParams')?.params?.tags).to.deep.eq([1])

      it 'should not change highlightedDocumentListParams when changing documentList to a node', ->
        state = new State({ highlightedDocumentListParams: @params1 }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.equals.returns(false)
        state.setDocumentListParams(nodes: [ 2 ])
        expect(state.get('highlightedDocumentListParams')).to.eq(@params1)

      it 'should act as a factory method when called with no arguments', ->
        state = new State({}, documentSetId: 'xxx', transactionQueue: 'xxx')
        state.setDocumentListParams().byQ('search')
        expect(state.get('documentList')?.params?.params?.q).to.eq('search')

    describe 'with documentList', ->
      beforeEach ->
        @state = new State({
          document: null
          documentList:
            params:
              params: { nodes: [ 1 ] }
              toQueryParams: -> { nodes: '1' }
        }, documentSetId: 'xxx', transactionQueue: 'xxx')

      it 'should give document selection when document is set', ->
        @state.set(document: { id: 10 })
        expect(@state.getSelectionQueryParams()).to.deep.eq(documents: '10')

      it 'should give doclist selection when document is null', ->
        @state.set(document: null)
        expect(@state.getSelectionQueryParams()).to.deep.eq(nodes: '1')

    describe 'init', ->
      beforeEach ->
        @transactionQueue =
          ajax: sinon.spy()
        @state = new State({}, documentSetId: '123', transactionQueue: @transactionQueue)

      it 'should request document set information', ->
        @state.init()
        expect(@transactionQueue.ajax).to.have.been.called
        args = @transactionQueue.ajax.args[0][0]
        expect(args).to.have.property('url', '/documentsets/123.json')
        expect(args).to.have.property('success')

      it 'should trigger sync on success', ->
        @state.init()
        @state.once('sync', spy = sinon.spy())
        @transactionQueue.ajax.args[0][0].success
          tags: [ { id: 1, name: 'foo', color: '#abc123' }]
          views: []
          nDocuments: 12
        expect(spy).to.have.been.called

      it 'should set tags, views and nDocuments on success', ->
        @state.init()
        @transactionQueue.ajax.args[0][0].success
          tags: [ { id: 345, name: 'foo', color: '#abc123' }]
          views: [ { id: 456, type: 'view' } ]
          nDocuments: 12
        expect(@state.tags).to.have.length(1)
        expect(_.result(@state.tags, 'url')).to.eq('/documentsets/123/tags')
        expect(@state.views).to.have.length(1)
        expect(_.result(@state.views, 'url')).to.eq('/documentsets/123/views')
        expect(@state.nDocuments).to.eq(12)

      it 'should set documentList to all on success', ->
        @state.init()
        expect(@state.get('documentList')).not.to.exist
        @transactionQueue.ajax.args[0][0].success
          tags: []
          views: [ { id: 456, type: 'view' } ]
          nDocuments: 12
        expect(@state.get('documentList')).to.exist
        expect(@state.get('documentList').params.toJSON()).to.deep.eq({})

      it 'should set the view on success, if there is one', ->
        @state.init()
        @transactionQueue.ajax.args[0][0].success
          tags: []
          views: [ { id: 456, type: 'view' } ]
          nDocuments: 12
        expect(@state.get('view')?.id).to.eq('view-456')

    describe 'setView', ->
      class DocumentSet

      class View extends Backbone.Model

      beforeEach ->
        @documentSet = new DocumentSet()
        @view1 = new View(id: 'foo', rootNodeId: 1) # see State.coffee for why we need rootNodeId
        @view2 = new View(id: 'bar', rootNodeId: 2)

        @params =
          state: @state
          view: @view1
          params: {}
          withView: (view) =>
            state: @state
            view: view
            params: {}

        @state = new State({
          view: @view1
          document: 'document'
        }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @state.set(documentList: { params: @params })

        @state.setView(@view2)

      it 'should alter view', -> expect(@state.get('view')).to.eq(@view2)
      it 'should unset document', -> expect(@state.get('document')).to.be.null

      it 'should set documentList', ->
        params = @state.get('documentList')?.params
        expect(params.state).to.eq(@state)
        expect(params.view).to.eq(@view2)
