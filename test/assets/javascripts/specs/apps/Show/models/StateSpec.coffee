define [
  'backbone'
  'apps/Show/models/State'
  'i18n'
], (Backbone, State, i18n) ->
  describe 'apps/Show/models/State', ->
    describe 'resetDocumentListParams', ->
      beforeEach ->
        i18n.reset_messages
          'views.DocumentSet.show.DocumentListParams.all': 'hack'

        @params2 = 
          equals: sinon.stub().returns(false)
          params: {}

        @params1 =
          equals: sinon.stub().returns(false)
          params: {}
          reset:
            bySomething: sinon.stub().returns(@params2)

      it 'should do nothing when setting to equivalent documentListParams', ->
        state = new State({ documentListParams: @params1, document: 'foo' }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.equals.returns(true)
        state.on('all', spy = sinon.spy())
        state.resetDocumentListParams().bySomething()
        expect(spy).not.to.have.been.called
        expect(state.get('document')).to.eq('foo')

      it 'should change document to null when changing documentListParams', ->
        state = new State({ documentListParams: @params1, document: 'foo' }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('document')).to.be.null

      it 'should change highlightedDocumentListParams to the new value when changing documentListParams to a tag', ->
        state = new State({ documentListParams: @params1 }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('highlightedDocumentListParams')).to.eq(@params2)

      it 'should not change highlightedDocumentListParams when changing documentListParams to a node', ->
        state = new State({ documentListParams: @params1, highlightedDocumentListParams: @params1 }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @params1.reset.bySomething.returns(params: { nodes: [ 2 ] })
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('highlightedDocumentListParams')).to.eq(@params1)

    describe 'with documentListParams', ->
      beforeEach ->
        @state = new State({
          document: null
          documentListParams:
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

      it 'should set documentListParams to all on success', ->
        @state.init()
        expect(@state.get('documentListParams')).to.be.null
        @transactionQueue.ajax.args[0][0].success
          tags: []
          views: [ { id: 456, type: 'view' } ]
          nDocuments: 12
        expect(@state.get('documentListParams')?.toJSON()).to.deep.eq({})

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
          documentSet: @documentSet
          view: @view1
          withView: (view) =>
            documentSet: @documentSet
            view: view

        @state = new State({
          view: @view1
          documentListParams: @params
          document: 'document'
        }, documentSetId: 'xxx', transactionQueue: 'xxx')
        @state.setView(@view2)

      it 'should alter view', -> expect(@state.get('view')).to.eq(@view2)
      it 'should unset document', -> expect(@state.get('document')).to.be.null

      it 'should alter documentListParams', ->
        params = @state.get('documentListParams')
        expect(params.documentSet).to.eq(@documentSet)
        expect(params.view).to.eq(@view2)
