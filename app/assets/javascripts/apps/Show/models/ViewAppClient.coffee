define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class ViewAppClient
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.viewApp, the View app instance' if !options.viewApp

      @state = options.state
      @viewApp = options.viewApp

      throw 'options.viewApp needs a remove() method which removes all traces of the view' if !@viewApp.remove

      @listenTo(@state, 'change:documentList', @onDocumentListChanged)
      @listenTo(@state, 'change:document', @onDocumentChanged)
      @listenTo(@state, 'change:highlightedDocumentListParams', @onHighlightedDocumentListParamsChanged)
      @listenTo(@state, 'tag', @onTag)
      @listenTo(@state, 'untag', @onUntag)
      @onMessageCallback = @_onMessage.bind(@)
      window.addEventListener('message', @onMessageCallback, false)

    onDocumentListChanged: (__, value) -> @viewApp.onDocumentListParamsChanged?(value?.params)
    onDocumentChanged: (__, value) -> @viewApp.onDocumentChanged?(value)
    onHighlightedDocumentListParamsChanged: (__, value) -> @viewApp.onHighlightedDocumentListParamsChanged?(value)
    onTag: (tag, params) -> @viewApp.onTag?(tag, params)
    onUntag: (tag, params) -> @viewApp.onUntag?(tag, params)

    setDocumentListParams: (params) -> @state.setDocumentListParams(params)

    _onMessage: (e) ->
      viewUrl = @viewApp?.view?.attributes?.url || '' # _any_ iframe, e.g. Twitter, can post a message
      viewUrl = "#{window.location.protocol}#{viewUrl}" if viewUrl.substring(0, 2) == '//'
      if e.origin != viewUrl.substring(0, e.origin.length)
        console.log("Dropped message from #{e.origin}: expected #{viewUrl}", e)
        return

      switch e.data.call
        when 'notifyDocumentListParams' then @viewApp.notifyDocumentListParams?(@state.get('documentList')?.params)
        when 'setDocumentListParams' then @setDocumentListParams(e.data.args...)
        else console.log("Invalid message from view: #{e.data.call}", e.data)

    remove: ->
      window.removeEventListener('message', @onMessageCallback, false)
      @viewApp.remove()
      @stopListening()
