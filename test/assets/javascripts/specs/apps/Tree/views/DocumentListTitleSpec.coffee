define [
  'underscore'
  'apps/Tree/views/DocumentListTitle'
  'apps/Tree/models/observable'
  'i18n'
], (_, DocumentListTitle, observable, i18n) ->
  class TagStore
    observable(this)

    find_by_id: (id) -> { id: id, name: "Tag #{id}" }

  class SearchResultStore
    observable(this)

    find_by_id: (id) -> { id: id, query: "Search #{id}" }

  class IdTree
    observable(this)

  class OnDemandTree
    constructor: ->
      @nodes =
        '1': { id: 1, description: 'Node 1' }
        '2': { id: 2, description: 'Node 2' }

      @id_tree = new IdTree()

  cache =
    tag_store: new TagStore()
    on_demand_tree: new OnDemandTree()
    search_result_store: new SearchResultStore()

  class DocumentList
    observable(this)

    constructor: (@params, @n) ->
      @cache = cache

  describe 'apps/Tree/views/DocumentListTitleView', ->
    documentList = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.DocumentListTitle.num_documents': 'num_documents,{0}'
        'views.Tree.show.DocumentListTitle.loading': 'loading'
        'views.Tree.show.DocumentListTitle.searching.title_html': 'searching.title_html,{0}'
        'views.Tree.show.DocumentListTitle.searchError.title_html': 'searchError.title_html,{0}'
        'views.Tree.show.DocumentListTitle.all.title_html': 'all.title_html,{0}'
        'views.Tree.show.DocumentListTitle.tag.title_html': 'tag.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.tag.edit': 'tag.edit'
        'views.Tree.show.DocumentListTitle.untagged.title_html': 'untagged.title_html,{0}'
        'views.Tree.show.DocumentListTitle.node.title_html': 'node.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.node.edit': 'node.edit'
        'views.Tree.show.DocumentListTitle.searchResult.title_html': 'searchResult.title_html,{0},{1}'
        'views.Tree.show.DocumentListTitle.searchResult.edit': 'searchResult.edit'

    afterEach ->
      view?.off()
      view?.remove()

    init = (docListParams...) ->
      documentList = new DocumentList(docListParams...)
      view = new DocumentListTitle(documentList: documentList, cache: cache)

    it 'should render nothing with an undefined list', ->
      init(undefined)
      expect(view.$el.html()).toEqual('')

    it 'should render loading message with an unloaded list', ->
      init(new DocumentList({ type: 'all' }, undefined))
      expect(view.$el.text()).toMatch(/loading/)

    describe 'with a Tag', ->
      beforeEach ->
        init({ type: 'tag', tagId: 1 }, 4)

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,4,Tag 1')

      it 'should trigger edit-tag', ->
        args = []
        view.on('edit-tag', -> args = _.toArray(arguments))
        view.$('a.tag-edit').click()
        expect(args).toEqual([ 1 ])

      it 'should trigger edit-tag correctly after id-changed', ->
        args = []
        view.on('edit-tag', -> args = _.toArray(arguments))
        cache.tag_store._notify('id-changed', 1, { id: 2, name: 'Tag 1' })
        view.$('a.tag-edit').click()
        expect(args).toEqual([ 2 ])

      it 'should adjust title after changed', ->
        # Hack-ish test setup
        cache.tag_store.find_by_id = (id) -> { id: id, name: "Tag 2" }
        cache.tag_store._notify('changed', { id: 1, name: 'Tag 2' })
        # But this line is nice
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,4,Tag 2')

      it 'should render when calling setDocumentList()', ->
        documentList2 = new DocumentList({ type: 'tag', tagId: 2 }, 8)
        view.setDocumentList(documentList2)
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,8,Tag 2')

    it 'should render the title when selecting untagged', ->
      init({ type: 'untagged' }, 4)
      expect(view.$('h4').text()).toEqual('untagged.title_html,num_documents,4')

    it 'should render the title when all is selected', ->
      init({ type: 'all' }, 4)
      expect(view.$('h4').text()).toEqual('all.title_html,num_documents,4')

    describe 'with a Node', ->
      beforeEach ->
        init({ type: 'node', nodeId: 1 }, 4)

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('node.title_html,num_documents,4,Node 1')

      it 'should trigger edit-tag', ->
        args = []
        view.on('edit-node', -> args = _.toArray(arguments))
        view.$('a.node-edit').click()
        expect(args).toEqual([ 1 ])

      it 'should adjust title after changed', ->
        cache.on_demand_tree.nodes[1].description = 'Edited'
        cache.on_demand_tree.id_tree._notify('change')
        expect(view.$('h4').text()).toEqual('node.title_html,num_documents,4,Edited')

    describe 'with a SearchResult', ->
      searchResult = undefined

      searchInit = (nDocuments, searchResultState) ->
        searchResult =
          id: 1
          query: 'Search 1'
          state: searchResultState
        spyOn(cache.search_result_store, 'find_by_id').andReturn(searchResult)
        init({ type: 'searchResult', searchResultId: 1 }, nDocuments)

      it 'should render search message when searching', ->
        searchInit(undefined, 'Searching')
        expect(view.$el.text()).toMatch(/searching/)

      it 'should have class=search-pending when searching', ->
        searchInit(undefined, 'Searching')
        expect(view.$el.attr('class')).toEqual('search-pending')

      it 'should render search message when search is halfway done', ->
        searchInit(4, 'Searching')
        expect(view.$el.text()).toMatch(/searching/)

      it 'should render the title', ->
        searchInit(4, 'Complete')
        expect(view.$('h4').text()).toEqual('searchResult.title_html,num_documents,4,Search 1')

      it 'should render an error', ->
        searchInit(undefined, 'Error')
        expect(view.$('h4').text()).toEqual('searchError.title_html,Search 1')

      it 'should have class=search-error on search error', ->
        searchInit(undefined, 'Error')
        expect(view.$el.attr('class')).toEqual('search-error')

      it 'should render a new search result ID', ->
        searchInit(4, 'Searching')
        cache.search_result_store._notify('id-changed', 1, { id: 2, query: 'Search 1', state: 'Searching' })
        expect(view.$('.search-result')).toHaveAttr('data-id', '2')

      it 'should render completion once the search result changes', ->
        searchInit(4, 'Searching')
        searchResult.state = 'Error'
        cache.search_result_store._notify('changed', { id: 1, query: 'Search 1', state: 'Error' })
        expect(view.$el.attr('class')).toEqual('search-error')
