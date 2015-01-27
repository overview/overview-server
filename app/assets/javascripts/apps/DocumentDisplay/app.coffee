define [
  './models/TextDocument'
  './models/UrlPropertiesExtractor'
  './models/CurrentCapabilities'
  './models/Preferences'
  './views/DocumentView'
  './views/PreferencesView'
  './views/TextView'
], (TextDocument, UrlPropertiesExtractor, CurrentCapabilities, Preferences, DocumentView, PreferencesView, TextView) ->
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

    render: ->
      id = @document?.id
      url = @document?.url

      urlProperties = @urlPropertiesExtractor.urlToProperties(url) if url?

      if !id? || !urlProperties?
        @textDocument = null
        @textView?.remove()
        @documentView.setUrlProperties(null)
        @$el.attr(class: '')
      else
        @currentCapabilities.set
          canShowDocument: urlProperties.type of ViewableDocumentTypes
          canShowSidebar: urlProperties.type == 'documentCloud'
          canWrap: null
        if @currentCapabilities.get('canShowDocument') && !@preferences.get('text')
          @_renderDocument(urlProperties)
        else
          @_renderText()

    _renderDocument: (urlProperties) ->
      @$el.attr(class: 'showing-document')
      @textDocument = null
      @textView?.remove()
      @documentView.setUrlProperties(urlProperties)

    _renderText: ->
      @$el.attr(class: 'showing-text')
      @textView?.remove()
      if @document?
        @textDocument = new TextDocument
          id: @document.id
          documentSetId: DocumentSetId
        @textDocument.fetchText()
        @textDocument.fetchHighlights(@q)
      else
        @textDocument = null
      @textView = new TextView
        model: @textDocument
        currentCapabilities: @currentCapabilities
        preferences: @preferences
      @$textViewEl.append(@textView.el)
