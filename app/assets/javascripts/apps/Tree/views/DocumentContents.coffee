define [
  'backbone'
  '../../DocumentDisplay/app'
], (Backbone, DocumentDisplayApp) ->
  # A view for a Document.
  #
  # Usage:
  #
  #   view = new DocumentContents
  #     cache: cache # for finding document JSON
  #     state: state # for watching which document we should be viewing
  #     documentDisplayApp: app # OPTIONAL - defaults to `new DocumentDisplayApp`
  #
  #   view.scroll_by_pages(1) # calls documentDisplayApp.scrollByPages(1)
  #
  # That does the following:
  #
  # * Adds documentDisplayApp.el to view.el
  # * Calls documentDisplayApp.setDocument(json) for the document in state
  # * Watches for changes to the state, and adjusts documentdisplayApp to match
  #
  # When the document in state is null, nothing happens.
  # documentDisplayApp.setDocument(null) is _never_ called: it is assumed that
  # this view will be hidden, so there's no reason to clear it; instead, we
  # gain responsiveness when the user closes and then opens the same document.
  class DocumentContents extends Backbone.View
    id: 'document'

    initialize: ->
      throw 'must set options.cache, a Cache' if !@options.cache
      throw 'must set options.state, a State' if !@options.state

      @state = @options.state
      @cache = @options.cache

      @app = @options.documentDisplayApp ? new DocumentDisplayApp()

      @listenTo(@state, 'change:documentId change:oneDocumentSelected', => @render())

      @el.appendChild(@app.el)
      @render()

    # Returns a JSON POD document object, or undefined.
    #
    # The value will be non-null if:
    #
    # * There is one selected document; and
    # * The document is in the cache.
    _getDocument: ->
      documentId = @state.get('oneDocumentSelected') && @state.get('documentId') || null
      @cache.document_store.documents[documentId]

    scroll_by_pages: (n) ->
      @app.scrollByPages(n)

    render: ->
      document = @_getDocument()
      @app.setDocument(document) if document?

      this
