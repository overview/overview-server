define [
  'underscore'
  'jquery'
  'rsvp'
  './models/UrlPropertiesExtractor'
  './models/CurrentCapabilities'
  './models/Preferences'
  './views/DocumentView'
  './views/PreferencesView'
  './views/TextView'
], (_, $, RSVP, UrlPropertiesExtractor, CurrentCapabilities, Preferences, DocumentView, PreferencesView, TextView) ->
  alreadyLoaded = false

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
      @textView = new TextView(preferences: @preferences, currentCapabilities: @currentCapabilities)

      @preferencesView.render()
      @documentView.render()
      @textView.render()

      @$el.append(@preferencesView.el)
      @$el.append(@documentView.el)
      @$el.append(@textView.el)

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
      @render()

    _getTextPromise: ->
      if !@document?
        RSVP.resolve(null)
      else
        RSVP.resolve($.ajax(
          dataType: 'text'
          url: "/documents/#{@document.id}.txt"
        ))

    _getDocumentSetId: ->
      parts = window.location.pathname.split('/')
      parts[parts.length - 1]

    _getHighlightsPromise: ->
      if !@q? || !@document?
        RSVP.resolve(null)
      else
        RSVP.resolve($.ajax(
          url: "/documentsets/#{@_getDocumentSetId()}/documents/#{@document.id}/highlights?q=" + encodeURIComponent(@q)
        ))

    render: ->
      id = @document?.id
      url = @document?.url

      urlProperties = @urlPropertiesExtractor.urlToProperties(url) if url?

      if !id? || !urlProperties?
        @documentView.setUrlProperties(null)
        @textView.setTextPromise(null)
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
      @textView.setTextPromise(null)
      @documentView.setUrlProperties(urlProperties)
      @$el.attr(class: 'showing-document')

    _renderText: ->
      @documentView.setUrlProperties(null)
      @textView.setTextPromise(@_getTextPromise())
      @textView.setHighlightsPromise(@_getHighlightsPromise())
      @$el.attr(class: 'showing-text')
