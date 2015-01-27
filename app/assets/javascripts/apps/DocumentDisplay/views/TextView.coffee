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
      throw 'Must pass options.model, a TextDocument' if !@model
      throw 'Must pass options.preferences, a Preferences' if !options.preferences
      throw 'Must pass options.currentCapabilities, a CurrentCapabilities' if !options.currentCapabilities

      @preferences = options.preferences
      @currentCapabilities = options.currentCapabilities

      @listenTo(@preferences, 'change:wrap', @_onChangeWrap)
      @listenTo(@model, 'change:text change:error change:highlights', @render)
      @listenTo(@model, 'change:highlightsIndex', @_renderHighlightsIndex)

      $(window).on('resize.DocumentDisplay-TextView', => @_onWindowResize())

      @render()

    render: ->
      attrs = @model.attributes

      if attrs.error?
        @_renderError()
      else if attrs.text?
        @_renderTextWithHighlights(attrs.text, attrs.highlights || [])
      else
        @_renderLoading()

    remove: ->
      $(window).off('resize.DocumentDisplay-TextView')
      super()

    _renderLoading: ->
      @$el.html($('<div class="loading"></div>').text(t('loading')))
      @currentCapabilities.set(canWrap: null)
      @

    _renderError: ->
      @$el.html($('<div class="error"></div>').text(t('error')))
      @currentCapabilities.set(canWrap: null)
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
      @_renderHighlightsIndex()
      @

    # Fits the <em> into its <pre> parent node if it isn't visible
    _scrollToHighlight: (em) ->
      pre = em.parentNode

      buffer = 20 # px

      current =
        top: pre.scrollTop
        bottom: pre.scrollTop + pre.clientHeight
        left: pre.scrollLeft
        right: pre.scrollLeft + pre.clientWidth
      wanted =
        top: em.offsetTop
        bottom: em.offsetTop + em.offsetHeight
        left: em.offsetLeft
        right: em.offsetLeft + em.offsetWidth

      if wanted.bottom + buffer > current.bottom
        pre.scrollTop = wanted.bottom + buffer - pre.clientHeight
      if wanted.right > current.right
        pre.scrollLeft = wanted.right - pre.clientWidth
      if wanted.left < current.left
        pre.scrollLeft = wanted.left
      if wanted.top - buffer < current.top
        pre.scrollTop = wanted.top - buffer

    _renderHighlightsIndex: ->
      $ems = @$('em.highlight')
      $ems.removeClass('current')
      current = @model.get('highlightsIndex')
      if current? && ($current = $ems.eq(current)).length
        $current.addClass('current')
        @_scrollToHighlight($current[0])

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
