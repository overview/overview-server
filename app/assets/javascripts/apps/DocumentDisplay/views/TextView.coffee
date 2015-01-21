define [
  'jquery'
  'backbone'
  'i18n'
], ($, Backbone, i18n) ->
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

      $(window).on('resize.DocumentDisplay-TextView', => @_onWindowResize())

    remove: ->
      $(window).off('.DocumentDisplay-TextView')
      super

    setTextPromise: (@textPromise) ->
      @render()

    render: ->
      return @$el.empty() if !@textPromise

      textPromise = @textPromise

      @_renderLoading()
      textPromise
        .then (text) =>
          try
            @_renderText(text || '') if textPromise == @textPromise
          catch e
            console.warn(e)
        .catch (error) =>
          @_renderError(error) if textPromise == @textPromise
      @

    _renderLoading: ->
      @currentCapabilities.set(canWrap: null)
      @$el.html($('<div class="loading"></div>').text(t('loading')))

    _renderError: ->
      @$el.html($('<div class="error"></div>').text(t('error')))

    _renderText: (text) ->
      @_setCanWrapFromText(text)
      $pre = $('<pre></pre>')
        .toggleClass('wrap', @preferences.get('wrap') == true)
        .text(text)
      @$el.empty().append($pre)

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

    _onWindowResize: ->
      @_cachedWidth = false
      text = @$('pre').text() || ''
      @_setCanWrapFromText(text)
