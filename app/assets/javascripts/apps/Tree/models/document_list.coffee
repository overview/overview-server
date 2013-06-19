define [ 'jquery', './observable' ], ($, observable) ->
  # Stores a possibly-incomplete list of selected documents
  #
  # When you create a DocumentList (from a Cache and a Selection), its @documents
  # property is empty and @n is undefined. Call get_placeholder_documents() to
  # get some documents we know to exist that match the selection; call .slice()
  # to get a Deferred that will (or has been) resolved to the Documents. When
  # documents have been found, @documents will be populated with this
  # (possibly-incomplete) list, and @n will be the total number of documents.
  #
  # Always destroy() a DocumentList. Otherwise, the documents will leak.
  #
  # DocumentList is almost immutable. In weird circumstances its @n may change,
  # and its @documents will grow until it reaches @n elements and every element
  # is defined. But that's it.
  class DocumentList
    observable(this)

    constructor: (@cache, @selection) ->
      @documents = []
      @deferreds = {}
      @n = undefined

    get_placeholder_documents: () ->
      @selection.documents_from_cache(@cache)

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

        searchResultIds = if !@selection.nodes.length && !@selection.tags.length && @selection.searchResults.length == 1
          [ @selection.searchResults[0] ]
        else
          []

        @cache.resolve_deferred('selection_documents_slice', { selection: @selection, pageSize: pageSize, page: page }).done((ret) =>
          document_store_input = {
            doclist: { docids: [] },
            documents: {},
          }

          for document, i in ret.documents
            document.searchResultIds = searchResultIds # so when we tag a search result, the change appears
            document_store_input.doclist.docids.push(document.id)
            document_store_input.documents[document.id] = document
          @n = ret.total_items
          @cache.document_store.add_doclist(
            document_store_input.doclist,
            document_store_input.documents
          )
          for document, i in ret.documents
            # FIXME make document_list a bunch of IDs, not actual documents
            docid = document.id
            real_document = @cache.document_store.documents[docid]
            @documents[start+i] = real_document
          this._notify()
        ).pipe((ret) -> ret.documents)

      @deferreds[deferred_key] = deferred

    destroy: () ->
      docids = (document.id for document in @documents when document?)
      @cache.document_store.remove_doclist({ docids: docids })
