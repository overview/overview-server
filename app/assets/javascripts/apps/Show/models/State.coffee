define [
  'backbone'
  '../collections/Tags'
  '../collections/Views'
  './DocumentListParams'
], (Backbone, Tags, Views, DocumentListParams) ->

  # Calls the given callback only after the model exists.
  #
  # This is synchronous if the model exists, async otherwise.
  whenExists = (model, callback) ->
    if !model.isNew()
      callback()
    else
      model.once('sync', -> whenExists(model, callback))

  # Tracks global state.
  #
  # * Provides `documentSetId` and `transactionQueue`: constants.
  # * Loads `tags`, `views` and `nDocuments`: constants, once set. (Set them
  #   via init() and wait for `sync`.)
  # * Gives access to `view`, `documentListParams`, `document` and
  #   `highlightedDocumentListParams`: global state as Backbone.Model
  #   attributes.
  #
  # Usage:
  #
  #     transactionQueue = new TransactionQueue()
  #     state = new State({}, documentSetId: '123', transactionQueue: transactionQueue)
  #     state.init()
  #     state.once('sync', -> renderEverything())
  #
  # Methods that change stuff on the server
  # ---------------------------------------
  #
  # You may call tag() and untag() using a Tag. If the tag hasn't been saved to
  # the server yet, the actual tagging operation will be postponed until it
  # has.
  #
  # tag: (tag, documentListParams): tells the server to tag a set of documents.
  # untag: (tag, documentListParams): tells the server to untag documents.
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

    initialize: (attributes, options={}) ->
      super()

      throw 'Must pass options.documentSetId, a String' if !options.documentSetId
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue

      @documentSetId = options.documentSetId
      @transactionQueue = options.transactionQueue

    # Loads `tags`, `views` and `nDocuments` from the server.
    init: ->
      @transactionQueue.ajax
        debugInfo: 'State.init'
        url: "/documentsets/#{@documentSetId}.json"
        success: (json) =>
          @tags = new Tags(json.tags, url: "/documentsets/#{@documentSetId}/tags")
          @views = new Views(json.views, url: "/documentsets/#{@documentSetId}/views")
          @nDocuments = json.nDocuments

          @setView(@views.at(0))
          @setDocumentListParams(new DocumentListParams(@, @views.at(0)))

          @trigger('sync')

    # Sets new documentListParams and unsets document.
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

    # Switches to a new View.
    #
    # This is the correct way of calling .set('view', ...). The reason: we
    # need to update documentListParams to point to the new view.
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

    tag: (tag, queryParams) ->
      call = =>
        @transactionQueue.ajax
          type: 'POST'
          url: "/documentsets/#{@documentSetId}/tags/#{tag.id}/add"
          data: queryParams
          debugInfo: 'DocumentSet.tag'

      whenExists(tag, call, @)

      @trigger('tag', tag, queryParams)

    untag: (tag, queryParams) ->
      call = =>
        @transactionQueue.ajax
          type: 'POST'
          url: "/documentsets/#{@documentSetId}/tags/#{tag.id}/remove"
          data: queryParams
          debugInfo: 'DocumentSet.untag'

      whenExists(tag, call, @)

      @trigger('untag', tag, queryParams)
