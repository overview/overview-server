define [ 'jquery', 'apps/DocumentDisplay/app' ], ($, DocumentDisplay) ->
  SIDEBAR_KEY = 'views.Document.show.sidebar'

  display = $.Deferred()
  $ ->
    display.resolve(new DocumentDisplay())

  {
    el: display.pipe((app) -> app.el)
    setDocument: (document) ->
      display.pipe((app) -> app.setDocument(document))
    scrollByPages: (n) ->
      display.pipe((app) -> app.scrollByPages(n))
  }
