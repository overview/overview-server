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
      @view =
        id: 13
        scopeApiParams: (params) -> params
      @builder = new DocumentListParams(@documentSet, @view)

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
      it 'should have a view', -> expect(@params.view).to.eq(@view)

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

      it 'should use view.scopeApiParams() in toApiParams()', ->
        @view.scopeApiParams = (apiParams) -> _.extend({ foo: 'bar' }, apiParams)
        expect(@params.toApiParams()).to.deep.eq(tags: '1', foo: 'bar')

      it 'should not use view.scopeApiParams() in toApiParams() if view is null', ->
        params = @builder.withView(null).byTag(@tag)
        expect(params.toApiParams()).to.deep.eq(tags: '1')

      it 'should reset', ->
        params2 = @params.reset.byNode(id: 3)
        expect(params2.params).to.deep.eq([ id: 3 ])
        expect(params2.documentSet).to.eq(@documentSet)
        expect(params2.view).to.eq(@view)

      it 'should reset to a different view', ->
        view2 = 'view2'
        params2 = @params.reset.withView(view2).all()
        expect(params2.view).to.eq(view2)

    describe 'byDocument', ->
      beforeEach ->
        @document = new MockDocument(id: 1)
        @params = @builder.byDocument(@document)

      it 'should have type document', -> expect(@params.type).to.eq('document')
      it 'should have one param', -> expect(@params.params).to.deep.eq([@document])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(document:1)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ documents: [1] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(documents: '1')

      it 'should never, EVER return undefined in its API params', ->
        @document.id = undefined
        expect(@params.toApiParams()).to.deep.eq(documents: '0')

    describe 'untagged', ->
      beforeEach -> @params = @builder.untagged()

      it 'should have type untagged', -> expect(@params.type).to.eq('untagged')
      it 'should have no params', -> expect(@params.params).to.deep.eq([])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(untagged)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq({ tags: [0] })
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(tags: '0')
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'untagged' ])

    describe 'bySearch', ->
      beforeEach ->
        @params = @builder.bySearch('foo')

      it 'should have type search', -> expect(@params.type).to.eq('search')
      it 'should have one param', -> expect(@params.params).to.deep.eq(['foo'])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(search:foo)')
      it 'should have a JSON param', -> expect(@params.toJSON()).to.deep.eq(q: 'foo')
      it 'should have an API param', -> expect(@params.toApiParams()).to.deep.eq(q: 'foo')
      it 'should have correct toI18n()', -> expect(@params.toI18n()).to.deep.eq([ 'search', 'foo' ])
