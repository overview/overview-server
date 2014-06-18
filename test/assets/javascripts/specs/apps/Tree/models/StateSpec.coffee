define [
  'apps/Tree/models/State'
], (State) ->
  describe 'apps/Tree/models/State', ->
    describe 'resetDocumentListParams', ->
      beforeEach ->
        @params2 = 
          equals: sinon.stub().returns(false)
          taglikeCid: null

        @params1 =
          equals: sinon.stub().returns(false)
          taglikeCid: null
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

      it 'should change taglikeCid to the new value when changing documentListParams to a tag', ->
        state = new State(documentListParams: @params1, taglikeCid: 'foo')
        @params1.equals.returns(false)
        @params2.type = 'tag'
        @params2.tag = { cid: 'bar' }
        state.resetDocumentListParams().bySomething()
        expect(state.get('taglikeCid')).to.eq('bar')

      it 'should not change taglikeCid when changing documentListParams to a node', ->
        state = new State(documentListParams: @params1, taglikeCid: 'foo')
        @params1.equals.returns(false)
        @params2.type = 'node'
        @params2.node = { cid: 'bar' }
        state.resetDocumentListParams().bySomething()
        expect(state.get('taglikeCid')).to.eq('foo')

    describe 'document and oneDocumentSelected', ->
      state = undefined

      beforeEach ->
        state = new State
          document: null
          oneDocumentSelected: false
          documentListParams:
            toJSON: -> { nodes: [ 1 ] }
            reset:
              byDocument: (x) ->
                toJSON: -> [ 'byDocument', x ]

      it 'should give empty selection when document is null and oneDocumentSelected is true', ->
        state.set(document: null, oneDocumentSelected: true)
        expect(state.getSelection().toJSON()).to.deep.eq([ 'byDocument', null ])

      it 'should give document selection when document is set and oneDocumentSelected is true', ->
        state.set(document: 'foo', oneDocumentSelected: true)
        expect(state.getSelection().toJSON()).to.deep.eq([ 'byDocument', 'foo' ])

      it 'should give doclist selection when document is null and oneDocumentSelected is false', ->
        state.set(document: null, oneDocumentSelected: false)
        expect(state.getSelection().toJSON()).to.deep.eq({ nodes: [ 1 ] })

      it 'should give doclist selection when document is set and oneDocumentSelected is false', ->
        state.set(document: 'foo', oneDocumentSelected: false)
        expect(state.getSelection().toJSON()).to.deep.eq({ nodes: [ 1 ] })
