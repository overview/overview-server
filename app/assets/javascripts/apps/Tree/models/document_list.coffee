define [ 'jquery', './observable' ], ($, observable) ->
  # Stores a possibly-incomplete list of selected documents
  #
  # When you create a DocumentList (from a Cache and DocumentListParams), its
  # @documents is empty and @n is undefined. Call .slice() to get a Deferred
  # that will (or has been) resolved to the Documents. When documents have been
  # found, @documents will be populated with this (possibly-incomplete) list,
  # and @n will be the total number of documents.
  #
  # Always destroy() a DocumentList. Otherwise, the documents will leak.
  #
  # DocumentList is almost immutable. In weird circumstances its @n may change,
  # and its @documents will grow until it reaches @n elements and every element
  # is defined. But that's it.
  class DocumentList
    observable(this)

    constructor: (@cache, @params) ->
      @documents = []
      @deferreds = {}
      @n = undefined

    # Returns a Deferred which, when resolved, will be a slice of this.documents
    #
    # It expects you to use a constant page size: start / (end-start)
    slice: (start, end) ->
      deferred_key = "#{start}..#{end}"

      if @deferreds[deferred_key]?
        return @deferreds[deferred_key]

      deferred = if end < @documents.length
        new $.Deferred().resolve(@documents.slice(start, end))
      else
        pageSize = end - start
        throw 'cannot have page size <= 0' if pageSize <= 0
        page = Math.round(start / pageSize) + 1
        throw 'not starting at the start of a page' if Math.round(pageSize * page) != start + pageSize

        searchResultId = @params.type == 'searchResult' && @params.searchResultId || null

        params = @params.toApiParams()
        params.pageSize = pageSize
        params.page = page

        @cache.resolve_deferred('selection_documents_slice', params).done((ret) =>
          @n = ret.total_items
          for document, i in ret.documents
            @documents[start+i] = document
          @cache.document_store.reset(@documents.filter((d) -> d?))
          this._notify()
        ).pipe((ret) -> ret.documents)

      @deferreds[deferred_key] = deferred

    # Returns all you need for i18n-ized names.
    #
    # For instance:
    #
    # * [ 'all' ]
    # * [ 'node', 'node description' ]
    # * [ 'tag', 'tag name' ]
    # * [ 'untagged' ]
    # * [ 'searchResult', 'query' ]
    describeParameters: ->
      type = @params.type
      ret = [ type ]

      if type == 'node'
        ret.push(@cache.on_demand_tree.nodes[@params.nodeId]?.description || '')
      else if type == 'searchResult'
        ret.push(@cache.search_result_store.find_by_id(@params.searchResultId)?.query || '')
      else if type == 'tag'
        ret.push(@cache.tag_store.find_by_id(@params.tagId)?.name || '')

      ret

    destroy: () ->
      @cache.document_store.reset([])
