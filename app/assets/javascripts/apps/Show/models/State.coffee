define [
  'backbone'
  '../collections/Tags'
  '../collections/Views'
  './DocumentList'
  './DocumentListParams'
], (Backbone, Tags, Views, DocumentList, DocumentListParams) ->
  # Tracks global state.
  #
  # * Provides `documentSet` and `transactionQueue`: constants.
  # * Gives access to `view`, `documentList`, `document`: global state as
  #   Backbone.Model attributes.
  #
  # Usage:
  #
  #     transactionQueue = new TransactionQueue()
  #     documentSet = new DocumentSet(...)
  #     state = new State({}, documentSet: documentSet, transactionQueue: transactionQueue)
  #     state.once('sync', -> renderEverything())
  #     state.init()
  class State extends Backbone.Model
    defaults:
      # The currently displayed View.
      view: null

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

    initialize: (attributes, options={}) ->
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue

      @documentSet = options.documentSet
      @documentSetId = @documentSet.id
      @transactionQueue = options.transactionQueue

      view = attributes.view || @documentSet.views.at(0)

      @set
        view: view
        document: null
        documentList: new DocumentList({}, documentSet: @documentSet, params: new DocumentListParams())

    # Sets new documentList params and unsets document.
    #
    # This is the only safe way to change document lists.
    #
    # You may pass a DocumentListParams instance, or you may pass a plain JSON
    # object that will be passed to the DocumentListParams constructor.
    setDocumentListParams: (options) ->
      oldParams = @get('documentList')?.params

      params = if options instanceof DocumentListParams
        options
      else
        new DocumentListParams(options)

      return if oldParams?.equals(params)

      @set
        document: null
        documentList: new DocumentList({}, documentSet: @documentSet, params: params)

    # Sets new documentList params relative to the current ones.
    #
    # Example:
    #
    #     state.setDocumentListParams(tags: { ids: [ 1, 2 ] })
    #     state.refineDocumentListParams(q: 'foo')
    #     state.refineDocumentListParams(q: null)
    refineDocumentListParams: (options) ->
      oldParams = @get('documentList')?.params || new DocumentListParams()

      @setDocumentListParams
        q: if _.isUndefined(options.q) then oldParams.q else options.q
        tags: if _.isUndefined(options.tags) then oldParams.tags else options.tags
        objects: if _.isUndefined(options.objects) then oldParams.objects else options.objects

    # Switches to a new View.
    #
    # This is the correct way of calling .set('view', ...). The reason: we
    # need to update documentList to point to the new view.
    setView: (view) ->
      reset = => @set(view: view)

      @stopListening(@get('view'))

      if view?.get('type') == 'job'
        @listenToOnce(view, 'change:type', reset)

      reset()

    # Returns the thing Tagging operations should apply to.
    #
    # If there is a document, it's that. Othewise, it's the documentList.
    #
    # Usage:
    #
    #   state.getCurrentTaggable()?.tag(tag)
    getCurrentTaggable: ->
      @get('document') || @get('documentList') || null
