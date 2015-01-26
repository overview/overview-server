define [
  'jquery'
  'backbone'
  'rsvp'
  'i18n'
], ($, Backbone, RSVP, i18n) ->
  t = i18n.namespaced('views.Document.show.TextView')

  WrapString = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm"

  class TextView extends Backbone.View
    className: 'text'

    initialize: (options) ->
      throw 'Must pass options.preferences, a Preferences' if !options.preferences
      throw 'Must pass options.currentCapabilities, a CurrentCapabilities' if !options.currentCapabilities
      @preferences = options.preferences
      @currentCapabilities = options.currentCapabilities
      @listenTo(@preferences, 'change:wrap', @_onChangeWrap)
      @textPromise = null
      @model = new Backbone.Model(text: null, highlights: null, error: null) # null means "loading"

      @listenTo(@model, 'change', @render)

      $(window).on('resize.DocumentDisplay-TextView', => @_onWindowResize())

      @render()

    remove: ->
      $(window).off('.DocumentDisplay-TextView')
      super

    # Returns a Promise that is completed when setting is done
    setTextPromise: (@textPromise) ->
      @model.set(error: null, text: null)

      if @textPromise
        textPromise = @textPromise
        textPromise
          .then (text) =>
            try
              @model.set(text: text) if textPromise == @textPromise
            catch e
              console.warn(e)
              throw e
          .catch (error) =>
            @model.set(error: error) if textPromise == @textPromise
      else
        RSVP.resolve(null)

    # Returns a Promise that is completed when setting is done
    setHighlightsPromise: (@highlightsPromise) ->
      @model.set(highlights: null)

      if @highlightsPromise
        highlightsPromise = @highlightsPromise
        highlightsPromise
          .then (highlights) =>
            try
              @model.set(highlights: highlights) if highlightsPromise == @highlightsPromise
            catch e
              console.warn(e)
              throw e
          .catch (error) => console.warn(error)
      else
        RSVP.resolve(null)

    render: ->
      attrs = @model.attributes

      if attrs.error?
        @_renderError(error)
      else if attrs.text?
        @_renderTextWithHighlights(attrs.text, attrs.highlights || [])
      else
        @_renderLoading()

    _renderLoading: ->
      @$el.html($('<div class="loading"></div>').text(t('loading')))
      @currentCapabilities.set(canWrap: null)
      @

    _renderError: ->
      @$el.html($('<div class="error"></div>').text(t('error')))
      @

    # If there are no highlights, call this with highlights=[]
    _renderTextWithHighlights: (text, highlights) ->
      @_setCanWrapFromText(text)

      highlighting = false
      position = 0

      nodes = []

      for [ begin, end ] in highlights
        text1 = text.substring(position, begin)
        text2 = text.substring(begin, end)

        if text1.length
          nodes.push(document.createTextNode(text1))

        node2 = document.createElement('em')
        node2.className = 'highlight'
        node2.appendChild(document.createTextNode(text2))
        nodes.push(node2)

        position = end

      finalText = text.substring(position)
      if finalText.length
        nodes.push(document.createTextNode(finalText))

      $pre = $('<pre></pre>')
        .toggleClass('wrap', @preferences.get('wrap') == true)
        .append(nodes)
      @$el.empty().append($pre)
      @

    # Returns the maximum line number of chars that fit in a <pre>
    _calculateMaxLineLength: ->
      return @_cachedMaxLineLength if @$el.width() == @_cachedWidth

      @_cachedWidth = @$el.width()
      $pre = $('<pre></pre>').appendTo(@el)
      $span = $('<span></span>').appendTo($pre)

      max = WrapString.length
      min = 5

      preWidth = $pre.width()

      while max > min
        mid = Math.floor((max + min) / 2)
        mid += 1 if mid == min
        s = WrapString.slice(0, mid)
        $span.text(s)

        if $span.width() < preWidth
          min = mid
        else
          max = mid - 1

      $pre.remove()

      @_cachedMaxLineLength = min

    # Returns true iff there is a line of text longer than maxLength chars
    _textMaxLineLengthExceeds: (text, maxLength) ->
      index = 0
      while true
        newlineIndex = text.indexOf('\n', index)
        if newlineIndex - index > maxLength
          return true
        else if newlineIndex == -1
          return text.length - index > maxLength
        else
          index = newlineIndex + 1

    _setCanWrapFromText: (text) ->
      maxLineLength = @_calculateMaxLineLength()
      canWrap = @_textMaxLineLengthExceeds(text, maxLineLength)
      @currentCapabilities.set(canWrap: canWrap)

    _onChangeWrap: ->
      @$('pre').toggleClass('wrap', @preferences.get('wrap') == true)

    _onWindowResize: (e) ->
      if @currentCapabilities.get('canWrap')?
        text = @$('pre').text() || null
        @_setCanWrapFromText(text)
