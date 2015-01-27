define [
  './models/TextDocument'
  './models/UrlPropertiesExtractor'
  './models/CurrentCapabilities'
  './models/Preferences'
  './views/DocumentView'
  './views/PreferencesView'
  './views/TextView'
  './views/FindView'
], (TextDocument, UrlPropertiesExtractor, CurrentCapabilities, Preferences, DocumentView, PreferencesView, TextView, FindView) ->
  DocumentSetId = (->
    parts = window.location.pathname.split('/')
    parts[parts.length - 1]
  )()

  ViewableDocumentTypes =
    documentCloud: null
    https: null
    pdf: null
    twitter: null

  class App extends Backbone.View
    # Creates a new DocumentDisplay.
    #
    # Callers should access the "el" property to insert it into
    # the page. Then they can call setDocument() to show a document.
    initialize: (options) ->
      @urlPropertiesExtractor = new UrlPropertiesExtractor(documentCloudUrl: window.documentCloudUrl)
      @preferences = new Preferences()
      @currentCapabilities = new CurrentCapabilities()

      @listenTo(@preferences, 'change:text', @render)

      @_initialRender()

      @q = null
      @setDocument(null)

    _initialRender: ->
      @preferencesView = new PreferencesView(preferences: @preferences, currentCapabilities: @currentCapabilities)
      @documentView = new DocumentView(preferences: @preferences)
      @$textViewEl = Backbone.$('<div class="text-view"></div>')

      @preferencesView.render()
      @documentView.render()

      @$el.append(@preferencesView.el)
      @$el.append(@documentView.el)
      @$el.append(@$textViewEl)

    # Show a new document.
    #
    # The document may be:
    #
    # * A JSON object with id and url properties (in our database)
    # * A JSON object with an id property (in our database)
    # * null
    setDocument: (json) ->
      return if json?.id == @document?.id
      @document = json
      @render()

    # Highlight a new search phrase
    #
    # The search phrase may be a String or <tt>null</tt>.
    setSearch: (q) ->
      return if @q == q
      @q = q
      @textDocument?.fetchHighlights(q)
      @$el.toggleClass('highlighting', @q?)

    render: ->
      id = @document?.id
      url = @document?.url

      urlProperties = @urlPropertiesExtractor.urlToProperties(url) if url?

      if !id?
        @_removeTextViews()
        @textDocument = null
        @documentView.setUrlProperties(null)
        @$el.attr(class: '')
      else
        @currentCapabilities.set
          canShowDocument: urlProperties?.type of ViewableDocumentTypes
          canShowSidebar: urlProperties?.type == 'documentCloud'
          canWrap: null
        if @currentCapabilities.get('canShowDocument') && !@preferences.get('text')
          @_renderDocument(urlProperties)
        else
          @_renderText()

    _removeTextViews: ->
      @textView?.remove()
      @findView?.remove()
      @textView = null
      @findView = null

    _renderDocument: (urlProperties) ->
      @$el.attr(class: 'showing-document')
      @_removeTextViews()
      @textDocument = null
      @documentView.setUrlProperties(urlProperties)

    _renderText: ->
      @$el.attr(class: 'showing-text')
      @$el.toggleClass('highlighting', @q?)
      @_removeTextViews()
      if @document?
        @textDocument = new TextDocument
          id: @document.id
          documentSetId: DocumentSetId
        @textDocument.fetchText()
        @textDocument.fetchHighlights(@q)
        @textView = new TextView
          model: @textDocument
          currentCapabilities: @currentCapabilities
          preferences: @preferences
        @findView = new FindView(model: @textDocument)
        @$textViewEl.append(@textView.el)
        @$textViewEl.append(@findView.el)
      else
        @textDocument = null
