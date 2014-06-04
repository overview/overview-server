define [
  'apps/Tree/models/state'
], (State) ->
  describe 'apps/Tree/models/State', ->
    describe 'setDocumentListParams', ->
      it 'should do nothing when setting to equivalent documentListParams', ->
        params1 = {}
        params2 = {}
        params1.equals = params2.equals = (rhs) -> rhs == params1 || rhs == params2
        state = new State(documentListParams: params1, documentId: 5)
        state.on('all', spy = sinon.spy())
        state.setDocumentListParams(params2)
        expect(spy).not.to.have.been.called

      it 'should change documentId to null when changing documentListParams', ->
        params1 = { foo: '1', equals: (rhs) -> rhs == params1 }
        params2 = { foo: '2', equals: (rhs) -> rhs == params2 }
        state = new State(documentListParams: params1, documentId: 5)
        state.setDocumentListParams(params2)
        expect(state.get('documentId')).to.be.null

      it 'should change taglike to the new value when changing documentListParams', ->
        state = new State()
        state.setDocumentListParams(type: 'tag', tagId: 4, equals: -> false)
        expect(state.get('taglike')).to.deep.eq({ tagId: 4 })

      it 'should not change taglike when changing documentListParams to something that is not taglike', ->
        state = new State(taglike: { tagId: 4 })
        state.setDocumentListParams(type: 'node', nodeId: 1, equals: -> false)
        expect(state.get('taglike')).to.deep.eq({ tagId: 4 })

    describe 'documentId and oneDocumentSelected', ->
      state = undefined

      beforeEach ->
        state = new State(
          documentId: null,
          oneDocumentSelected: false,
          documentListParams:
            toJSON: -> { nodes: [ 1 ] }
        )

      it 'should give empty selection when documentId is null and oneDocumentSelected is true', ->
        state.set(documentId: null, oneDocumentSelected: true)
        expect(state.getSelection().toJSON()).to.deep.eq({ documents: [-1] })

      it 'should give document selection when documentId is set and oneDocumentSelected is true', ->
        state.set(documentId: 5, oneDocumentSelected: true)
        expect(state.getSelection().toJSON()).to.deep.eq({ documents: [5] })

      it 'should give doclist selection when documentId is null and oneDocumentSelected is false', ->
        state.set(documentId: null, oneDocumentSelected: false)
        expect(state.getSelection().toJSON()).to.deep.eq({ nodes: [ 1 ] })

      it 'should give doclist selection when documentId is set and oneDocumentSelected is false', ->
        state.set(documentId: 5, oneDocumentSelected: false)
        expect(state.getSelection().toJSON()).to.deep.eq({ nodes: [ 1 ] })
