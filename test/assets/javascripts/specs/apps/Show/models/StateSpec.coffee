define [
  'backbone'
  'apps/Show/models/State'
  'i18n'
], (Backbone, State, i18n) ->
  describe 'apps/Show/models/State', ->
    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.show.DocumentListParams.all': 'hack'
        'views.DocumentSet.show.DocumentListParams.q': 'hack2'
        'views.DocumentSet.show.DocumentListParams.node': 'hack3'
        'views.DocumentSet.show.DocumentListParams.tag': 'hack4'

      @documentSet =
        id: 123
        tags: new Backbone.Collection
        views: new Backbone.Collection
      @transactionQueue =
        ajax: sinon.spy()

      @buildState = (documentSetOrNull, transactionQueueOrNull) =>
        @state = new State {},
          documentSet: (documentSetOrNull ? @documentSet),
          transactionQueue: (transactionQueueOrNull ? @transactionQueue)

    afterEach ->
      @state?.stopListening()

    it 'should set documentList to all', ->
      @buildState()
      expect(@state.get('documentList')).to.exist
      expect(@state.get('documentList').params.toJSON()).to.deep.eq({})

    it 'should pick a view by default, if there is one', ->
      @documentSet.views.add([ { id: 456, type: 'view' } ])
      @buildState()
      expect(@state.get('view')?.id).to.eq(456)

    describe 'setView', ->
      class DocumentSet

      class View extends Backbone.Model

      beforeEach ->
        @documentSet.views.add([
          new View(id: 'foo', rootNodeId: 1) # see State.coffee for why we need rootNodeId
          new View(id: 'bar', rootNodeId: 2)
        ])

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
        }, documentSet: @documentSet, transactionQueue: @transactionQueue)
        @state.set(documentList: { params: @params })

        @state.setView(@view2)

      it 'should alter view', -> expect(@state.get('view')).to.eq(@view2)
      it 'should unset document', -> expect(@state.get('document')).to.be.null

      it 'should set documentList', ->
        params = @state.get('documentList')?.params
        expect(params.state).to.eq(@state)
        expect(params.view).to.eq(@view2)

    describe 'setDocumentListParams', ->
      beforeEach ->
        @params1 =
          equals: sinon.stub().returns(false)
          params: {}

      it 'should change document to null when changing documentList', ->
        @buildState()
        @state.set(document: 'foo')
        @state.setDocumentListParams().byQ('new list')
        expect(@state.get('document')).to.be.null

      it 'should not change document to null when setting to equivalent documentList', ->
        @buildState()
        @state.setDocumentListParams().byQ('q1')
        @state.set(document: 'foo')
        @state.setDocumentListParams().byQ('q1')
        expect(@state.get('document')).to.eq('foo')

      it 'should change highlightedDocumentListParams to the new value when changing documentList to a tag', ->
        @buildState()
        @state.set(highlightedDocumentListParams: @params1)
        @params1.equals.returns(false)
        @state.setDocumentListParams(tags: [ 1 ])
        expect(@state.get('highlightedDocumentListParams')?.params?.tags).to.deep.eq([1])

      it 'should not change highlightedDocumentListParams when changing documentList to a node', ->
        @buildState()
        @state.set(highlightedDocumentListParams: @params1)
        @params1.equals.returns(false)
        @state.setDocumentListParams(nodes: [ 2 ])
        expect(@state.get('highlightedDocumentListParams')).to.eq(@params1)

      it 'should act as a factory method when called with no arguments', ->
        state = new State({}, documentSet: @documentSet, transactionQueue: 'xxx')
        state.setDocumentListParams().byQ('search')
        expect(state.get('documentList')?.params?.params?.q).to.eq('search')
