define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentListParams'
  'i18n'
], (_, Backbone, DocumentListParams, i18n) ->
  describe 'apps/Show/models/DocumentListParams', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentListParams',
        all: 'all'
        node: 'node,{0}'
        tag: 'tag,{0}'
        untagged: 'untagged'
        q: 'q,{0}'

      class MockTag extends Backbone.Model
      @tag = new MockTag(id: 1, name: 'tag 1')

      @node = { id: 2, description: 'node 2' }
      _.extend(@node, Backbone.Events)

      @state =
        documentSetId: 12
        tags:
          get: (id) => if id == 1 then @tag else undefined
      @view =
        id: 13
        addScopeToQueryParams: (params) -> params
        onDemandTree:
          getNode: (id) => if id == 2 then @node else undefined
      @builder = new DocumentListParams(@state, @view).reset

    describe 'reset()', ->
      beforeEach -> @reset = @builder.all().reset

      it 'should set a node title from the view', -> expect(@reset(nodes: [ @node.id ]).title).to.eq('node,node 2')
      it 'should set a tag title from the docset', -> expect(@reset(tags: [ @tag.id ]).title).to.eq('tag,tag 1')
      it 'should set a query title', -> expect(@reset(q: 'foo').title).to.eq('q,foo')
      it 'should set an untagged title', -> expect(@reset(tagged: false).title).to.eq('untagged')
      it 'should set an all title', -> expect(@reset().title).to.eq('all')
      it 'should not override a title', -> expect(@reset(title: 'blah').title).to.eq('blah')

    describe 'all', ->
      beforeEach -> @params = @builder.all()

      it 'should have no params', -> expect(@params.params).to.deep.eq({})
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams()')
      it 'should equals() another', -> expect(@params.equals(@builder.all())).to.be.true
      it 'should not equals() something else', -> expect(@params.equals(@builder.byUntagged())).to.be.false
      it 'should give empty query params', -> expect(@params.toQueryParams()).to.deep.eq({})
      it 'should have correct i18n', -> expect(@params.title).to.eq('all')
      it 'should have a state', -> expect(@params.state).to.eq(@state)
      it 'should have a view', -> expect(@params.view).to.eq(@view)

      it 'should reset to add objects', ->
        params2 = @params.reset(objects: [ 1, 2, 3 ], title: 'blah')
        expect(params2.params).to.deep.eq(objects: [ 1, 2, 3 ])
        expect(params2.title).to.eq('blah')

    describe 'byNode', ->
      beforeEach ->
        @params = @builder.byNode(@node)

      it 'should have one param', -> expect(@params.params).to.deep.eq(nodes: [ 2 ])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(nodes=2)')
      it 'should give query params', -> expect(@params.toQueryParams()).to.deep.eq(nodes: '2')
      it 'should equals() another', -> expect(@params.equals(@params.reset.byNode(@node))).to.be.true
      it 'should not equals() something else', -> expect(@params.equals(@params.reset.byNode(id: 3))).to.be.false
      it 'should have correct title', -> expect(@params.title).to.eq('node,node 2')

    describe 'byTag', ->
      beforeEach ->
        @params = @builder.byTag(@tag)

      it 'should have one param', -> expect(@params.params).to.deep.eq(tags: [1])
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(tags=1)')
      it 'should give query params', -> expect(@params.toQueryParams()).to.deep.eq(tags: '1')
      it 'should have correct title', -> expect(@params.title).to.eq('tag,tag 1')

      it 'should use view.addScopeToQueryParams() in toQueryParams()', ->
        @view.addScopeToQueryParams = (apiParams) -> _.extend({ foo: 'bar' }, apiParams)
        expect(@params.toQueryParams()).to.deep.eq(tags: '1', foo: 'bar')

      it 'should not use view.addScopeToQueryParams() in toApiParams() if view is null', ->
        params = @params.withView(null).reset.byTag(@tag)
        expect(params.toQueryParams()).to.deep.eq(tags: '1')

      it 'should reset', ->
        params2 = @params.reset.byNode(id: 3, description: 'foo')
        expect(params2.params).to.deep.eq(nodes: [ 3 ])
        expect(params2.title).to.eq('node,foo')
        expect(params2.state).to.eq(@state)
        expect(params2.view).to.eq(@view)

      it 'should reset to a different view', ->
        view2 = 'view2'
        params2 = @params.withView(view2).reset.all()
        expect(params2.view).to.eq(view2)

    describe 'untagged', ->
      beforeEach -> @params = @builder.byUntagged()

      it 'should have no params', -> expect(@params.params).to.deep.eq(tagged: false)
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(tagged=false)')
      it 'should have a query param', -> expect(@params.toQueryParams()).to.deep.eq(tagged: 'false')
      it 'should have correct title', -> expect(@params.title).to.eq('untagged')

    describe 'byQ', ->
      beforeEach ->
        @params = @builder.byQ('foo')

      it 'should have one param', -> expect(@params.params).to.deep.eq(q: 'foo')
      it 'should have toString', -> expect(@params.toString()).to.eq('DocumentListParams(q=foo)')
      it 'should have a query param', -> expect(@params.toQueryParams()).to.deep.eq(q: 'foo')
      it 'should have correct title', -> expect(@params.title).to.deep.eq('q,foo')
