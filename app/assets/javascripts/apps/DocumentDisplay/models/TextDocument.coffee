define [
  'backbone'
], (Backbone) ->
  getDocumentSetId = ->
    parts = window.location.pathname.split('/')
    parts[parts.length - 1]

  # A text document.
  #
  # Initialize the text document with an `id: id`.
  #
  # A text document is immutable but deferred: we initialize it and call
  # fetchText() to make its text appear.
  #
  # You can highlight text, too. Just call fetchHighlights(q) to kick off the
  # highlighting task. If there was a previous highlight query, this will clear
  # it. Highlights are an Array of [begin,end) pairs of string offsets.
  class TextDocument extends Backbone.Model
    defaults:
      # the document's original URL (may be null)
      url: null
      # the document text, or null if loading
      text: null
      # true iff the text came from Tesseract
      isFromOcr: false
      # set iff we could not load the text
      error: null
      # search query we're highlighting
      highlightsQuery: null
      # highlight Arrays, null if loading or not highlighting
      highlights: null
      # set iff we could not load the highlights
      highlightsError: null
      # indexes into the highlights array
      highlightsIndex: null

    initialize: (attrs, options) ->
      throw 'Must set id attribute' if !attrs.id?
      throw 'Must set documentSetId attribute' if !attrs.documentSetId?

    fetchHighlights: (query) ->
      return if @get('highlightsQuery') == query
      @_highlightsFetch.abort() if @_highlightsFetch?
      @set(highlightsQuery: query, highlights: null, highlightsError: null, highlightsIndex: null)
      if query
        highlightsFetch = @_highlightsFetch = Backbone.ajax
          url: "/documentsets/#{@get('documentSetId')}/documents/#{@id}/highlights?q=#{encodeURIComponent(query)}"

          success: (arrays) =>
            return if highlightsFetch != @_highlightsFetch
            @set(highlights: arrays)
            @set(highlightsIndex: 0) if arrays.length
            @_highlightsFetch = null

          error: (jqXHR, textStatus, errorThrown) =>
            return if highlightsFetch != @_highlightsFetch
            console.warn("Highlighting failed", jqXHR, textStatus, errorThrown)
            @set(highlightsError: errorThrown)
            @_highlightsFetch = null
      @

    fetchText: ->
      return if @_textFetch?
      @set(text: null, error: null)
      @_textFetch = Backbone.ajax
        url: "/documents/#{@id}.txt"
        success: (text, __, xhr) => @set(text: text, isFromOcr: xhr.getResponseHeader('Generated-By') == 'tesseract')
        error: (jqXHR, textStatus, errorThrown) =>
          console.warn("Text fetch failed", jqXHR, textStatus, errorThrown)
          @set(error: errorThrown)
      @
