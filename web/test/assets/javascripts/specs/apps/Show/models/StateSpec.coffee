define [
  'backbone'
  'apps/Show/models/State'
], (Backbone, State) ->
  describe 'apps/Show/models/State', ->
    beforeEach ->
      @documentSet =
        id: 123
        tags: new Backbone.Collection
        views: new Backbone.Collection
        url: '/documentsets/123.json'
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
      class View extends Backbone.Model

      beforeEach ->
        @documentSet.views.add([
          new View(id: 'foo', rootNodeId: 1) # see State.coffee for why we need rootNodeId
          new View(id: 'bar', rootNodeId: 2)
        ])

        @params =
          documentSet: @documentSet
          view: @view1
          params: {}
          withView: (view) =>
            documentSet: @documentSet
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
        expect(params.documentSet).to.eq(@documentSet)
        expect(params.view).to.eq(@view2)

    describe 'setDocumentListParams', ->
      beforeEach ->
        @buildState()

      it 'should change document to null when changing documentList', ->
        @state.set(document: 'foo')
        @state.setDocumentListParams(q: 'foo')
        expect(@state.get('document')).to.be.null

      it 'should not change document to null when not changing documentList', ->
        @state.setDocumentListParams(q: 'foo')
        @state.set(document: 'foo')
        @state.setDocumentListParams(q: 'foo')
        expect(@state.get('document')).to.eq('foo')

      it 'should set reverse=false on a documentList by default', ->
        @state.setDocumentListParams(q: 'foo')
        expect(@state.get('documentList').reverse).to.be.false

      it 'should set reverse=true on a documentList', ->
        @state.setDocumentListParams({ q: 'foo' }, true)
        expect(@state.get('documentList').reverse).to.be.true

    describe 'refineDocumentListParams', ->
      beforeEach ->
        @buildState()

      it 'should change document to null when changing documentList', ->
        @state.setDocumentListParams(tags: { ids: [ 1 ] })
        @state.set(document: 'foo')
        @state.refineDocumentListParams(q: 'foo')
        expect(@state.get('document')).to.be.null

      it 'should not change document to null when not changing documentList', ->
        @state.setDocumentListParams(tags: { ids: [ 1 ] })
        @state.set(document: 'foo')
        @state.refineDocumentListParams(tags: { ids: [ 1 ] })
        expect(@state.get('document')).to.eq('foo')

      it 'should set reverse=true', ->
        @state.setDocumentListParams(tags: { ids: [ 1 ] })
        @state.refineDocumentListParams(tags: { ids: [ 1 ] }, reverse: true)
        expect(@state.get('documentList').reverse).to.be.true

      it 'should set reverse=false', ->
        @state.setDocumentListParams({ tags: { ids: [ 1 ] } }, true)
        @state.refineDocumentListParams(reverse: false)
        expect(@state.get('documentList').reverse).to.be.false
