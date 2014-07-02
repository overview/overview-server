define [
  'backbone'
  '../../DocumentDisplay/app'
], (Backbone, DocumentDisplayApp) ->
  # A view for a Document.
  #
  # Usage:
  #
  #   view = new DocumentContents
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
    initialize: ->
      throw 'must set options.state, a State' if !@options.state

      @state = @options.state
      @app = @options.documentDisplayApp ? new DocumentDisplayApp()

      @listenTo(@state, 'change:document change:oneDocumentSelected', @render)

      @el.appendChild(@app.el)
      @render()

    scroll_by_pages: (n) ->
      @app.scrollByPages(n)

    render: ->
      document = @state.get('oneDocumentSelected') && @state.get('document') || null
      @app.setDocument(document.toJSON()) if document?

      this
