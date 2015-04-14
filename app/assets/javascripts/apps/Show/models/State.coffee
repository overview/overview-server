define [
  'backbone'
  '../collections/Tags'
  '../collections/Views'
  './DocumentList'
  './DocumentListParams'
], (Backbone, Tags, Views, DocumentList, DocumentListParams) ->
  # Tracks global state.
  #
  # * Provides `documentSetId` and `transactionQueue`: constants.
  # * Loads `tags`, `views` and `nDocuments`: constants, once set. (Set them
  #   via init() and wait for `sync`.)
  # * Gives access to `view`, `documentList`, `document` and
  #   `highlightedDocumentListParams`: global state as Backbone.Model
  #   attributes.
  #
  # Usage:
  #
  #     transactionQueue = new TransactionQueue()
  #     state = new State({}, documentSetId: '123', transactionQueue: transactionQueue)
  #     state.once('sync', -> renderEverything())
  #     state.init()
  #
  # Methods that change stuff on the server
  # ---------------------------------------
  #
  # You may call tag() and untag() using a Tag. If the tag hasn't been saved to
  # the server yet, the actual tagging operation will be postponed until it
  # has.
  #
  # tag: (tag, documentListQueryString): tells the server to tag a set of documents.
  # untag: (tag, documentListQueryString): tells the server to untag documents.
  class State extends Backbone.Model
    defaults:
      # The currently displayed list of documents.
      #
      # This is a partially-loaded list. Callers can read its `.params`,
      # `.length`, `.documents`; they can .tag() and .untag() it.
      #
      # Alter this list through `.setDocumentListParams()`.
      documentList: null

      # The currently-displayed document.
      #
      # This is *always* a member of `documentList`.
      #
      # When document is set, tagging/untagging applies to this document. When
      # document is null, tagging/untagging applies to documentList.
      document: null

      # Which document list is under view as far as the Tree is concerned.
      #
      # This gets set in setDocumentListParams, except when searching by Node.
      # That's so you can:
      #
      # 1. Select a tag (sets highlightedDocumentListParams)
      # 2. See the tree highlight the tag
      # 3. Click one of the nodes (_doesn't_ set highlightedDocumentListParams)
      # 4. Still see the tag highlighted
      highlightedDocumentListParams: null

    initialize: (attributes, options={}) ->
      super()

      throw 'Must pass options.documentSetId, a String' if !options.documentSetId
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue

      @documentSetId = options.documentSetId
      @transactionQueue = options.transactionQueue
      @tags = new Tags([], url: "/documentsets/#{@documentSetId}/tags")
      @views = new Views([], url: "/documentsets/#{@documentSetId}/views")

    # Loads `tags`, `views` and `nDocuments` from the server.
    init: ->
      @transactionQueue.ajax
        debugInfo: 'State.init'
        url: "/documentsets/#{@documentSetId}.json"
        success: (json) =>
          @tags.reset(json.tags)
          @views.reset(json.views)
          @nDocuments = json.nDocuments

          view = @views.at(0)
          attributes = @_createSetDocumentListParamsOptions(new DocumentListParams(@, view))
          attributes.view = view
          @set(attributes)

          @trigger('sync')

    # Sets new documentList params and unsets document.
    #
    # This is the only safe way to change document lists.
    #
    # Calling convention
    # ------------------
    #
    # There are several ways to call this method:
    #
    # 1. call `setDocumentListParams(params)`, where `params` is a
    #    DocumentListParams.
    # 2. call `setDocumentListParams({ foo: 'bar' }), and the options will be
    #    passed to `DocumentListParams.Builder`. For instance,
    #    `{ tags: [ 1 ] }` will select Tag 1.
    # 3. call `setDocumentListParams().all()`, `.byUntagged()`,
    #    `.byQ('search')`, etc. See `DocumentListParams.Builder` for the full
    #    list of methods.
    setDocumentListParams: (args...) ->
      return @_startResetDocumentListParams() if !args.length

      oldParams = @get('documentList')?.params

      params = if args[0] instanceof DocumentListParams
        args[0]
      else
        DocumentListParams.Builder(@, @get('view'))(args...)

      return if oldParams?.equals(params)

      options = @_createSetDocumentListParamsOptions(params)
      @set(options)

    _createSetDocumentListParamsOptions: (params) ->
      ret =
        document: null
        documentList: new DocumentList {},
          state: @
          params: params
          url: "/documentsets/#{@documentSetId}/documents"

      if 'nodes' not of params.params
        ret.highlightedDocumentListParams = params

      ret

    # Return JSON that describes all documents that will be affected by, say,
    # tagging.
    #
    # You can use this JSON to build a query string. For instance, it might
    # be `{ nodes: '2,3', tags: '1'}`.
    getSelectionQueryParams: ->
      if (documentId = @get('document')?.id)
        documents: String(documentId)
      else if (params = @get('documentList')?.params)?
        params.toQueryParams()
      else
        documents: '-1' # avoid tagging the entire docset by mistake

    # _sets up_ a reset.
    #
    # Use it like this:
    #
    #   state._startResetDocumentListParams().byDocument(document)
    #
    # It will call `reset.byDocument(document)` on the documentListParams
    # to get new DocumentListParams, and then it will call
    # `setDocumentListParams`.
    #
    # The special
    #
    #   state._startResetDocumentListParams().byJSON(nodes: [ 3 ])
    #
    # ... will call `reset(nodes: [3])` on the current documentListParams.
    _startResetDocumentListParams: ->
      build = new DocumentListParams.Builder(this, @get('view'))
      ret = {}

      prepare = (func) =>
        (args...) => @setDocumentListParams(func(args...))

      for k, v of build
        ret[k] = prepare(build[k])

      ret.byJSON = (args...) =>
        @setDocumentListParams(build(args...))

      ret

    # Switches to a new View.
    #
    # This is the correct way of calling .set('view', ...). The reason: we
    # need to update documentList to point to the new view.
    setView: (view) ->
      reset = =>
        params = @get('documentList')?.params
        params = params?.withView(view)

        options = @_createSetDocumentListParamsOptions(params)
        options.view = view
        @set(options)

      @stopListening(@get('view'))

      if view?.get('type') == 'job'
        @listenToOnce(view, 'change:type', reset)

      reset()

    tag: (tag, queryParams) ->
      tag.addToDocumentsOnServer(queryParams)
      @trigger('tag', tag, queryParams)

    untag: (tag, queryParams) ->
      tag.removeFromDocumentsOnServer(queryParams)
      @trigger('untag', tag, queryParams)
