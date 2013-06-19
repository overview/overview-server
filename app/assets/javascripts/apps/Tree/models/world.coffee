define [ './cache', './state' ], (Cache, State) ->
  class World
    constructor: () ->
      @cache = new Cache()
      @state = new State()

      this._handle_tag_id_change()
      this._handle_search_result_id_change()

    _handle_tag_id_change: () ->
      @cache.tag_store.observe('id-changed', this.rewrite_tag_id.bind(this))

    _handle_search_result_id_change: () ->
      @cache.search_result_store.observe('id-changed', this.rewrite_search_result_id.bind(this))

    rewrite_tag_id: (old_tagid, tag) ->
      @cache.document_store.rewrite_tag_id(old_tagid, tag.id)
      @cache.on_demand_tree.rewrite_tag_id(old_tagid, tag.id)

      index = @state.selection.tags.indexOf(old_tagid)
      if index != -1
        tags = @state.selection.tags.slice(0)
        tags.splice(index, 1, tag.id)
        selection = @state.selection.replace({ tags: tags })
        @state.set('selection', selection)

    rewrite_search_result_id: (oldId, searchResult) ->
      # The backend guarantees a search result can only have documents if it
      # has an ID. So we know we don't need to rewrite any IDs in @cache.
      index = @state.selection.searchResults.indexOf(oldId)
      if index != -1
        searchResults = @state.selection.searchResults.slice(0)
        searchResults.splice(index, 1, searchResult.id)
        selection = @state.selection.replace({ searchResults: searchResults })
        @state.set('selection', selection)
