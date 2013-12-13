define [
  'jquery'
  './models/State'
  './models/DocumentFinder'
  './views/Heading'
  './views/Page'
], ($, State, DocumentFinder, Heading, Page) ->
  class App
    # Creates a new DocumentDisplay.
    #
    # Callers should access the "el" property to insert it into
    # the page. Then they can call setDocument() to show a document.
    constructor: (options = undefined) ->
      @finder = new DocumentFinder(documentCloudUrl: window.documentCloudUrl)
      @state = new State()
      @el = options?.el || document.createElement('div')

      heading = new Heading({
        model: @state
      })
      @el.appendChild(heading.el)

      page = new Page({
        model: @state
      })
      @el.appendChild(page.el)

    # Show a new document.
    #
    # The document may be:
    #
    # * A JSON object with "heading" not undefined, plus other
    #   properties as per app/DocumentDisplay/models/Document
    # * A JSON object with a documentcloud_id property
    # * A JSON object with an id property (in our database)
    # * undefined
    setDocument: (json) ->
      if json?
        @finder.findDocumentFromJson(json)
          .done((document) => @state.set('document', document))
          .fail(=> @state.set('document', undefined))
      else
        @state.set('document', undefined)

    # Scroll the view by the specified number of pages.
    #
    # 1 means scroll forward; 0 means scroll backward.
    scrollByPages: (n) ->
      # FIXME not implemented
