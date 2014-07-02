define [ '../views/DocumentContents' ], (DocumentContentsView) ->
  document_contents_controller = (options) ->
    view = new DocumentContentsView(options)

    {
      el: view.el
      page_up: -> view.scroll_by_pages(-1)
      page_down: -> view.scroll_by_pages(1)
    }
