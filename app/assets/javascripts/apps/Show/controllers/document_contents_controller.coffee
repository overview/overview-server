define [ '../views/DocumentContents' ], (DocumentContentsView) ->
  document_contents_controller = (options) ->
    view = new DocumentContentsView(options)
    keyboardController = options.keyboardController

    keyboardController.register
      PageUp: -> view.scroll_by_pages(-1)
      PageDown: -> view.scroll_by_pages(1)
