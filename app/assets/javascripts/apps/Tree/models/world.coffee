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

      if (params = @state.get('documentListParams'))?
        if params.type == 'tag' && params.tagId == old_tagid
          # No need to fire Backbone events: rewrite it quietly
          params.params[0] = tag.id
          params.tagId = tag.id

      if (taglike = @state.get('taglike'))?
        if taglike.tagId? && taglike.tagId == old_tagid
          taglike.tagId = tag.id

    rewrite_search_result_id: (oldId, searchResult) ->
      # The backend guarantees a search result can only have documents if it
      # has an ID. So we know we don't need to rewrite any IDs in @cache.
      if (params = @state.get('documentListParams'))?
        if params.type == 'searchResult' && params.searchResultId == oldId
          # No need to fire Backbone events: rewrite it quietly
          params.params[0] = searchResult.id
          params.searchResultId = searchResult.id

      if (taglike = @state.get('taglike'))?
        if taglike.searchResultId? && taglike.searchResultId == oldId
          taglike.searchResultId = searchResult.id
