define [
  'backbone'
  'apps/Tree/models/State'
], (Backbone, State) ->
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

    describe 'setViz', ->
      class DocumentSet

      class Viz extends Backbone.Model

      beforeEach ->
        @documentSet = new DocumentSet()
        @viz1 = new Viz(id: 'foo', rootNodeId: 1) # see State.coffee for why we need rootNodeId
        @viz2 = new Viz(id: 'bar', rootNodeId: 2)

        @params =
          documentSet: @documentSet
          viz: @viz1
          reset:
            withViz: (viz) =>
              all: =>
                documentSet: @documentSet
                viz: viz

        @state = new State
          viz: @viz1
          documentListParams: @params
          document: 'document'
          oneDocumentSelected: true
        @state.setViz(@viz2)

      it 'should alter viz', -> expect(@state.get('viz')).to.eq(@viz2)
      it 'should unset document', -> expect(@state.get('document')).to.be.null
      it 'should unset oneDocumentSelected', -> expect(@state.get('oneDocumentSelected')).to.be.false

      it 'should alter documentListParams', ->
        params = @state.get('documentListParams')
        expect(params.documentSet).to.eq(@documentSet)
        expect(params.viz).to.eq(@viz2)
