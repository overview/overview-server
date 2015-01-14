define [ 'backbone' ], (Backbone) ->
  class State extends Backbone.Model
    defaults:
      # What we want to show in the doclist and filter tagging with
      documentListParams: null

      # Which document is selected/viewed. `null` means all documents in the doclist
      document: null

      # Whether we want to show a single document, vs all documents
      #
      # If oneDocument is true and documentId is null, that means we expect
      # documentId will be set soon and we shall view it when it is (and the
      # selection is null in the meantime).
      #
      # If oneDocument is false and documentId is set, that means we _were_
      # showing a document, and we may wish to toggle back to that state later,
      # but right now we are selecting the doclist.
      oneDocumentSelected: false

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

      highlightedDocumentListParams = if params.type == 'node'
        # Don't change
        @get('highlightedDocumentListParams')
      else
        params

      @set
        documentListParams: params
        highlightedDocumentListParams: highlightedDocumentListParams
        document: null

    # Return a DocumentList that describes all documents that will be affected
    # by tagging.
    #
    # Returns { documents: [ -1 ] } if documentId is null and
    # oneDocumentSelected is true.
    getSelection: ->
      if @get('oneDocumentSelected')
        document = @get('document')
        @get('documentListParams').reset.byDocument(document)
      else
        @get('documentListParams')

    # _sets up_ a reset.
    #
    # Use it like this:
    #
    #   state.resetDocumentListParams().byDocument(document)
    #
    # It will call `reset.byDocument(document)` on the current document list,
    # to get new DocumentListParams, and then it will call
    # `setDocumentListParams`.
    resetDocumentListParams: ->
      ret = {}
      params = @get('documentListParams')
      builder = params.reset

      scopedBuilder = (key) =>
        (args...) =>
          newParams = builder[key].apply(builder, args)
          @setDocumentListParams(newParams)

      for k, v of builder
        ret[k] = scopedBuilder(k)
      ret

    setView: (view) ->
      reset = =>
        params = @get('documentListParams')
        params = params?.reset.withView(view).all()

        @set
          documentListParams: params
          document: null
          oneDocumentSelected: false
          view: view

      @stopListening(@get('view'))

      if view?.get('type') == 'job'
        @listenToOnce(view, 'change:type', reset)

      reset()
