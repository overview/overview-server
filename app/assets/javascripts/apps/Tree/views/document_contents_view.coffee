define [ 'jquery' ], ($) ->
  class DocumentContentsView
    constructor: (@div, @cache, @state) ->
      @_create_iframe()

      @state.observe('selection-changed', this._refresh.bind(this))

      @_last_docid = @_get_docid()

    scroll_by_pages: (n) ->
      @iframe.contentWindow.scrollByPages?(n)

    _get_docid: () ->
      if @state.selection.documents.length
        @state.selection.documents[0]
      else
        @state.selection.documents_from_cache(@cache)[0]?.id

    _get_iframe_url: () ->
      docid = @_get_docid()
      docid && @cache.server.router.route_to_path('document_view', docid)

    _create_iframe: (document) ->
      url = this._get_iframe_url()
      if url
        $iframe = $('<iframe frameborder="0" width="1" height="1"></iframe>')
        $iframe.attr('src', url)
        $div = $(@div)
        $div.empty()
        $iframe.width(Math.floor($div.width()))
        $div.append($iframe)
        $iframe[0]

    _withContentWindowSetDocument: (callback) ->
      func = @iframe?.contentWindow?.setDocument
      if func?
        callback.call(this, func) # Be sync, so the test passes
        #setTimeout((=> callback.call(this, func)), 0) # Be consistent: always async
      else
        setTimeout((=> @_withContentWindowSetDocument(callback)), 50) # Wait for page to load
      undefined

    _refresh: () ->
      docid = this._get_docid()
      return if docid == @_last_docid
      @_last_docid = docid

      # We can start with no document, but we can never unset it after
      if docid
        if @iframe
          @_withContentWindowSetDocument (setDocument) ->
            setDocument(docid? && { id: docid } || undefined)
        else
          @iframe = @_create_iframe()
      undefined
