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
    # * A JSON object with an id and url property (in our database)
    # * A JSON object with an id property (in our database)
    # * null
    # * undefined
    setDocument: (json) ->
      return if json?.id == @document?.id && json?.url == @document?.url

      @document = json
      @render()

    _getTextPromise: (id) ->
      RSVP.resolve($.ajax(dataType: 'text', url: "/documents/#{id}.txt"))

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
          @_renderText(id)

    _renderDocument: (urlProperties) ->
      @textView.setTextPromise(null)
      @documentView.setUrlProperties(urlProperties)
      @$el.attr(class: 'showing-document')

    _renderText: (id) ->
      @documentView.setUrlProperties(null)
      @textView.setTextPromise(@_getTextPromise(id))
      @$el.attr(class: 'showing-text')
