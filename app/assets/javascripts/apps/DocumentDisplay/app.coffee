define [
  'underscore'
  'jquery'
  './models/Document'
  './models/State'
  './models/UrlPropertiesExtractor'
  './views/Heading'
  './views/Page'
], (_, $, Document, State, UrlPropertiesExtractor, Heading, Page) ->
  alreadyLoaded = false

  class App
    # Creates a new DocumentDisplay.
    #
    # Callers should access the "el" property to insert it into
    # the page. Then they can call setDocument() to show a document.
    constructor: (options = undefined) ->
      @state = new State()
      @urlPropertiesExtractor = new UrlPropertiesExtractor(documentCloudUrl: window.documentCloudUrl)
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
      oldDocument = @state.get('document')

      if !json? || !json.id?
        return if !oldDocument? # null => null
        @state.set(document: null)
      else
        urlProperties = @urlPropertiesExtractor.urlToProperties(json.url)
        json = _.extend({ urlProperties: urlProperties }, json)
        newDocument = new Document(json)
        return if oldDocument?.equals(newDocument)
        @state.set(document: newDocument)

    # Scroll the view by the specified number of pages.
    #
    # 1 means scroll forward; 0 means scroll backward.
    scrollByPages: (n) ->
      # FIXME not implemented
