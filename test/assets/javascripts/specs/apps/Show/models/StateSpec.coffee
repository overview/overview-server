define [
  'backbone'
  'apps/Show/models/State'
], (Backbone, State) ->
  describe 'apps/Show/models/State', ->
    describe 'resetDocumentListParams', ->
      beforeEach ->
        @params2 = 
          equals: sinon.stub().returns(false)
          params: {}

        @params1 =
          equals: sinon.stub().returns(false)
          params: {}
          reset:
            bySomething: sinon.stub().returns(@params2)

      it 'should do nothing when setting to equivalent documentListParams', ->
        state = new State(documentListParams: @params1, document: 'foo')
        @params1.equals.returns(true)
        state.on('all', spy = sinon.spy())
        state.resetDocumentListParams().bySomething()
        expect(spy).not.to.have.been.called
        expect(state.get('document')).to.eq('foo')

      it 'should change document to null when changing documentListParams', ->
        state = new State(documentListParams: @params1, document: 'foo')
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('document')).to.be.null

      it 'should change highlightedDocumentListParams to the new value when changing documentListParams to a tag', ->
        state = new State(documentListParams: @params1)
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('highlightedDocumentListParams')).to.eq(@params2)

      it 'should not change highlightedDocumentListParams when changing documentListParams to a node', ->
        state = new State(documentListParams: @params1, highlightedDocumentListParams: @params1)
        @params1.reset.bySomething.returns(params: { nodes: [ 2 ] })
        @params1.equals.returns(false)
        state.resetDocumentListParams().bySomething()
        expect(state.get('highlightedDocumentListParams')).to.eq(@params1)

    describe 'with documentListParams', ->
      state = undefined

      beforeEach ->
        state = new State
          document: null
          documentListParams:
            params: { nodes: [ 1 ] }
            toQueryParams: -> { nodes: '1' }

      it 'should give document selection when document is set', ->
        state.set(document: { id: 10 })
        expect(state.getSelectionQueryParams()).to.deep.eq(documents: '10')

      it 'should give doclist selection when document is null', ->
        state.set(document: null)
        expect(state.getSelectionQueryParams()).to.deep.eq(nodes: '1')

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

        @state = new State
          view: @view1
          documentListParams: @params
          document: 'document'
        @state.setView(@view2)

      it 'should alter view', -> expect(@state.get('view')).to.eq(@view2)
      it 'should unset document', -> expect(@state.get('document')).to.be.null

      it 'should alter documentListParams', ->
        params = @state.get('documentListParams')
        expect(params.documentSet).to.eq(@documentSet)
        expect(params.view).to.eq(@view2)
