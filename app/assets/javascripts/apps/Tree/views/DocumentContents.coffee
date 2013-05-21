define [ 'underscore', 'backbone' ], (_, Backbone) ->
  Backbone.View.extend
    id: 'document'

    template: _.template("""
      <iframe frameborder="0" width="1" height="1" src="<%= url %>"></iframe>
    """)

    initialize: ->
      throw 'must set options.cache, a Cache' if !@options.cache
      throw 'must set options.state, a State' if !@options.state

      @state = @options.state
      @cache = @options.cache

      @_onStateSelectionChanged = => @render()
      @state.observe('selection-changed', @_onStateSelectionChanged)

      @render()

    stopListening: ->
      @state.unobserve('selection-changed', @_onStateSelectionChanged)
      Backbone.View.prototype.stopListening.call(this)

    _getDocid: ->
      if @state.selection.documents.length
        @state.selection.documents[0]
      else
        @state.selection.documents_from_cache(@cache)[0]?.id

    scroll_by_pages: (n) ->
      @iframe.contentWindow.scrollByPages?(n)

    _getIframeUrl: () ->
      docid = @_getDocid()
      docid && @cache.server.router.route_to_path('document_view', docid)

    _createIframe: ->
      url = this._getIframeUrl()
      if url
        @$el.html(@template({ url: url }))
        $iframe = @$('iframe')
        $iframe.width(Math.floor(@$el.width() || 100))
        @iframe = $iframe[0]

    _withContentWindowSetDocument: (callback) ->
      func = @iframe?.contentWindow?.setDocument
      if func?
        callback.call(this, func) # Be sync, so the test passes
        #setTimeout((=> callback.call(this, func)), 0) # Be consistent: always async
      else
        setTimeout((=> @_withContentWindowSetDocument(callback)), 50) # Wait for page to load
      undefined

    render: ->
      docid = this._getDocid()
      return if docid == @_lastDocid
      @_lastDocid = docid

      # We can start with no document, but we can never unset it after
      if docid?
        if @iframe?
          @_withContentWindowSetDocument (setDocument) ->
            setDocument(docid? && { id: docid } || undefined)
        else
          @iframe = @_createIframe()

      this
