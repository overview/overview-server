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

      # Which tag-like is under view: 'untagged' for untagged, or a Tag or
      # SearchResult's CID.
      #
      # We use this to highlight the last-selected tag while navigating nodes.
      # We set it in setDocumentListParams. Since we only ever check for
      # equality, we don't need to store the actual object. (We wouldn't
      # benefit from storing the actual object, since 'untagged' is not like
      # the others.)
      taglikeCid: null

    # Sets new documentListParams and unsets documentId.
    #
    # Without knowledge of what is in the new document list, this is the only
    # safe way to change document lists. Otherwise, you may try to show a
    # document that isn't in the document list, leading to undefined behavior.
    setDocumentListParams: (params) ->
      params1 = @get('documentListParams')
      return if params1?.equals(params)

      taglikeCid = if params.type == 'tag'
        params.tag.cid
      else if params.type == 'searchResult'
        params.searchResult.cid
      else
        @get('taglikeCid')

      @set
        documentListParams: params
        document: null
        taglikeCid: taglikeCid

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

    setViz: (viz) ->
      newParams = @get('documentListParams')?.reset.withViz(viz).all()

      # FIXME terrible hack!
      #
      # With a Tree viz, DocumentListParams.toApiParams() will need a node ID.
      # But the viz doesn't come with rootNodeId preloaded. So if we try to
      # show the DocumentList (which happens whenever documentListParams
      # changes), it won't be filtered by Viz. The solution is to _wait_ for
      # the rootNodeId attribute and _then_ set the documentListParams.
      #
      # State has no idea when rootNodeId will appear. (It gets set in
      # OnDemandTree.) So this whole thing is pretty ugly.
      if !viz? || viz.get('rootNodeId')?
        # Switching to an already-loaded viz. This is the code we want
        @set
          documentListParams: newParams
          document: null
          oneDocumentSelected: false
          viz: viz
      else
        # The viz isn't loaded. This is the ugly thing.
        @stopListening(@_listeningToViz) if @_listeningToViz?
        @_listeningToViz = viz
        @listenTo viz, 'change:rootNodeId', (viz, rootNodeId) =>
          return if !rootNodeId?
          @set
            documentListParams: newParams
            document: null
            oneDocumentSelected: false
            viz: viz
        @set(viz: viz) # kick everything off. Leave documentListParams alone.
