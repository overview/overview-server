define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentListParams'
], (_, Backbone, DocumentListParams) ->
  class MockDocument extends Backbone.Model
    defaults:
      tagids: []
      nodeids: []

  class MockTag extends Backbone.Model
    defaults:
      name: ''

  class MockSearchResult extends Backbone.Model
    defaults:
      query: ''

  describe 'apps/Show/models/DocumentListParams', ->
    beforeEach ->
      @documentSet =
        id: 12
        url: "/documentsets/12"
      @viz =
        id: 13
        scopeApiParams: (params) -> params
      @builder = new DocumentListParams(@documentSet, @viz)

    describe 'all', ->
      beforeEach -> @params = @builder.all()

      it 'should have type all', -> expect(@params.type).to.eq('all')
      it 'should have no params', -> expect(@params.params).to.deep.eq([])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(all)')
      it 'should set JSON empty', -> expect(@params.toJSON()).to.deep.eq({})
      it 'should equals() another', -> expect(@params.equals(@builder.all())).to.be.true
      it 'should not equals() something else', -> expect(@params.equals(@builder.untagged())).to.be.false
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'all' ])
      it 'should have a documentSet', -> expect(@params.documentSet).to.eq(@documentSet)
      it 'should have a viz', -> expect(@params.viz).to.eq(@viz)

      it 'should find all documents from cache, sorted', ->
        list = [
          new MockDocument(id: 1)
          new MockDocument(id: 2)
          new MockDocument(id: 3)
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([ 1, 2, 3 ])

    describe 'byNode', ->
      beforeEach ->
        @node = { id: 2, description: 'foo' }
        @params = @builder.byNode(@node)

      it 'should have type node', -> expect(@params.type).to.eq('node')
      it 'should have one param', -> expect(@params.params).to.deep.eq([@node])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(node:2)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ nodes: [2] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(nodes: '2')
      it 'should equals() another', -> expect(@params.equals(@params.reset.byNode(@node))).to.be.true
      it 'should not equals() something else', -> expect(@params.equals(@params.reset.byNode(id: 3))).to.be.false
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'node', 'foo' ])

      it 'should find relevant documents from a list', ->
        list = [
          new MockDocument(id: 1, nodeids: [ 1, 2 ])
          new MockDocument(id: 2, nodeids: [ 1, 3, 4 ])
          new MockDocument(id: 3, nodeids: [ 1, 2 ])
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([ 1, 3 ])

    describe 'byTag', ->
      beforeEach ->
        @tag = new MockTag(id: 1, name: 'tag 1')
        @params = @builder.byTag(@tag)

      it 'should have type tag', -> expect(@params.type).to.eq('tag')
      it 'should have one param', -> expect(@params.params).to.deep.eq([@tag])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(tag:1)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ tags: [1] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq({ tags: '1' })
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'tag', 'tag 1' ])

      it 'should use viz.scopeApiParams() in toApiParams()', ->
        @viz.scopeApiParams = (apiParams) -> _.extend({ foo: 'bar' }, apiParams)
        expect(@params.toApiParams()).to.deep.eq(tags: '1', foo: 'bar')

      it 'should not use viz.scopeApiParams() in toApiParams() if viz is null', ->
        params = @builder.withViz(null).byTag(@tag)
        expect(params.toApiParams()).to.deep.eq(tags: '1')

      it 'should reset', ->
        params2 = @params.reset.byNode(id: 3)
        expect(params2.params).to.deep.eq([ id: 3 ])
        expect(params2.documentSet).to.eq(@documentSet)
        expect(params2.viz).to.eq(@viz)

      it 'should reset to a different viz', ->
        viz2 = 'viz2'
        params2 = @params.reset.withViz(viz2).all()
        expect(params2.viz).to.eq(viz2)

      it 'should find relevant documents from a list', ->
        list = [
          new MockDocument(id: 1, tagids: [ 1, 2 ])
          new MockDocument(id: 2, tagids: [ 3, 4 ])
          new MockDocument(id: 3, tagids: [ 1, 4 ])
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([ 1, 3 ])

    describe 'byDocument', ->
      beforeEach ->
        @document = new MockDocument(id: 1)
        @params = @builder.byDocument(@document)

      it 'should have type document', -> expect(@params.type).to.eq('document')
      it 'should have one param', -> expect(@params.params).to.deep.eq([@document])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(document:1)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ documents: [1] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(documents: '1')

      it 'should find the relevant document from a list', ->
        list = [
          new MockDocument(id: 2)
          @document
          new MockDocument(id: 3)
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([ 1 ])

      it 'should not find the document if it is not in the list', ->
        list = [
          new MockDocument(id: 2)
          new MockDocument(id: 3)
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([])

    describe 'untagged', ->
      beforeEach -> @params = @builder.untagged()

      it 'should have type untagged', -> expect(@params.type).to.eq('untagged')
      it 'should have no params', -> expect(@params.params).to.deep.eq([])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(untagged)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ tags: [0] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(tags: '0')
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'untagged' ])

      it 'should find all untagged documents from a list', ->
        list = [
          new MockDocument(id: 1, tagids: [ 1, 2 ])
          new MockDocument(id: 2, tagids: [])
          new MockDocument(id: 3, tagids: [ 4 ])
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([ 2 ])

    describe 'bySearchResult', ->
      beforeEach ->
        @searchResult = new MockSearchResult(id: 3, query: 'a query')
        @params = @builder.bySearchResult(@searchResult)

      it 'should have type searchResult', -> expect(@params.type).to.eq('searchResult')
      it 'should have one param', -> expect(@params.params).to.deep.eq([@searchResult])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(searchResult:3)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ searchResults: [3] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(searchResults: '3')
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'searchResult', 'a query' ])

      it 'should find no documents from a list', ->
        list = [
          new MockDocument(id: 2)
          new MockDocument(id: 3)
        ]
        result = @params.findDocumentsInList(list)
        expect(x.id for x in result).to.deep.eq([])
