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

      @preferences = options.preferences

      @listenTo(@preferences, 'change:wrap', @_onChangeWrap)
      @listenTo(@model, 'change:text change:isFromOcr change:error change:highlights', @render)
      @listenTo(@model, 'change:highlightsIndex', @_renderHighlightsIndex)

      @render()

    render: ->
      attrs = @model.attributes

      @$el.empty()

      if attrs.error?
        @_renderError()
      else if attrs.text?
        if @preferences.get('text') == false
          @_renderOnlyTextAvailable()
        if @model.get('isFromOcr') == true
          @_renderIsFromOcr()
        @_renderTextWithHighlights(attrs.text, attrs.highlights || [])
      else
        @_renderLoading()

    _renderLoading: ->
      @$el.html($('<div class="loading"></div>').text(t('loading')))
      @

    _renderError: ->
      @$el.html($('<div class="error"></div>').text(t('error')))
      @

    _renderIsFromOcr: ->
      $p = $('<p class="is-from-ocr"></p>').html(t('isFromOcr_html'))
      @$el.append($p)
      @

    _renderOnlyTextAvailable: ->
      $p = $('<p class="only-text-available"></p>').text(t('onlyTextAvailable'))
      @$el.append($p)
      @

    # If there are no highlights, call this with highlights=[]
    _renderTextWithHighlights: (text, highlights) ->
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
      @$el.append($pre)
      @_renderHighlightsIndex()
      @

    # Fits the <em> into its <pre> parent node if it isn't visible
    _scrollToHighlight: (em) ->
      container = em.parentNode

      buffer = 20 # px

      current =
        top: container.scrollTop
        bottom: container.scrollTop + container.clientHeight
        left: container.scrollLeft
        right: container.scrollLeft + container.clientWidth
      wanted =
        top: em.offsetTop
        bottom: em.offsetTop + em.offsetHeight
        left: em.offsetLeft
        right: em.offsetLeft + em.offsetWidth

      if wanted.bottom + buffer > current.bottom
        container.scrollTop = wanted.bottom + buffer - container.clientHeight
      if wanted.right > current.right
        container.scrollLeft = wanted.right - container.clientWidth
      if wanted.left < current.left
        container.scrollLeft = wanted.left
      if wanted.top - buffer < current.top
        container.scrollTop = wanted.top - buffer

    _renderHighlightsIndex: ->
      $ems = @$('em.highlight')
      $ems.removeClass('current')
      current = @model.get('highlightsIndex')
      if current? && ($current = $ems.eq(current)).length
        $current.addClass('current')
        @_scrollToHighlight($current[0])

    _onChangeWrap: ->
      @$('pre').toggleClass('wrap', @preferences.get('wrap') == true)
