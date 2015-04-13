define [ 'backbone' ], (Backbone) ->
  class State extends Backbone.Model
    defaults:
      # What we want to show in the doclist and filter tagging with
      documentListParams: null

      # Which document is selected/viewed. `null` means all documents in the doclist
      #
      # When document is set, tagging/untagging applies to this document. When
      # document is null, tagging/untagging applies to documentList.
      document: null

      # Which document list is under view as far as the Tree is concerned.
      #
      # This gets set in documentListParams, except when searching by Node.
      # That's so you can:
      #
      # 1. Select a tag (sets highlightedDocumentListParams)
      # 2. See the tree highlight the tag
      # 3. Click one of the nodes (_doesn't_ set highlightedDocumentListParams)
      # 4. Still see the tag highlighted
      highlightedDocumentListParams: null

    # Sets new documentListParams and unsets documentId.
    #
    # Without knowledge of what is in the new document list, this is the only
    # safe way to change document lists. Otherwise, you may try to show a
    # document that isn't in the document list, leading to undefined behavior.
    setDocumentListParams: (params) ->
      params1 = @get('documentListParams')
      return if params1?.equals(params)

      highlightedDocumentListParams = if 'nodes' of params.params
        # Don't change
        @get('highlightedDocumentListParams')
      else
        params

      @set
        documentListParams: params
        highlightedDocumentListParams: highlightedDocumentListParams
        document: null

    # Return JSON that describes all documents that will be affected by, say,
    # tagging.
    #
    # You can use this JSON to build a query string. For instance, it might
    # be `{ nodes: '2,3', tags: '1'}`.
    getSelectionQueryParams: ->
      if (documentId = @get('document')?.id)
        documents: String(documentId)
      else
        @get('documentListParams').toQueryParams()

    # _sets up_ a reset.
    #
    # Use it like this:
    #
    #   state.resetDocumentListParams().byDocument(document)
    #
    # It will call `reset.byDocument(document)` on the documentListParams
    # to get new DocumentListParams, and then it will call
    # `setDocumentListParams`.
    #
    # The special
    #
    #   state.resetDocumentListParams().byJSON(nodes: [ 3 ])
    #
    # ... will call `reset(nodes: [3])` on the current documentListParams.
    resetDocumentListParams: ->
      params = @get('documentListParams')
      reset = params.reset

      ret = {}

      scopedBuilder = (key) =>
        (args...) =>
          newParams = reset[key].apply(params, args)
          @setDocumentListParams(newParams)

      for k, v of params.reset
        ret[k] = scopedBuilder(k)

      ret.byJSON = (args...) =>
        newParams = reset.apply(params, args)
        @setDocumentListParams(newParams)

      ret

    setView: (view) ->
      reset = =>
        params = @get('documentListParams')
        params = params?.withView(view)

        @set
          documentListParams: params
          document: null
          view: view

      @stopListening(@get('view'))

      if view?.get('type') == 'job'
        @listenToOnce(view, 'change:type', reset)

      reset()
