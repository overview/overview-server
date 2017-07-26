define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class ViewAppClient
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.viewApp, the View app instance' if !options.viewApp
      throw 'Must pass options.globalActions, an Object full of callbacks' if !options.globalActions

      @state = options.state
      @viewApp = options.viewApp
      @globalActions = options.globalActions

      throw 'options.viewApp needs a remove() method which removes all traces of the view' if !@viewApp.remove

      @listenTo(@state, 'change:documentList', @onDocumentListChanged)
      @listenTo(@state.documentSet, 'change', @onDocumentSetChanged)
      @listenTo(@state, 'change:document', @onDocumentChanged)
      @listenTo(@state, 'tag', @onTag)
      @listenTo(@state, 'untag', @onUntag)
      @onMessageCallback = @_onMessage.bind(@)
      window.addEventListener('message', @onMessageCallback, false)

    onDocumentListChanged: (__, value) -> @viewApp.onDocumentListParamsChanged?(value?.params)
    onDocumentSetChanged: (__, value) -> @viewApp.onDocumentSetChanged?(@state.documentSet)
    onTag: (tag, params) -> @viewApp.onTag?(tag, params)
    onUntag: (tag, params) -> @viewApp.onUntag?(tag, params)

    onDocumentChanged: (__, value) ->
      @stopListening(@document) if @document?
      @document = value
      @listenTo(@document, 'change', () => @viewApp.onDocumentChanged?(value)) if @document?
      @viewApp.onDocumentChanged?(value)

    setDocumentListParams: (params) -> @state.setDocumentListParams(params)

    _onMessage: (e) ->
      viewUrl = @viewApp?.view?.attributes?.url || '' # _any_ iframe, e.g. Twitter, can post a message
      viewUrl = "#{window.location.protocol}#{viewUrl}" if viewUrl.substring(0, 2) == '//'
      if e.origin != viewUrl.substring(0, e.origin.length)
        console.log("Dropped message from #{e.origin}: expected #{viewUrl}", e)
        return

      switch e.data.call
        when 'notifyDocumentListParams' then @viewApp.notifyDocumentListParams?(@state.get('documentList')?.params)
        when 'notifyDocumentSet' then @viewApp.notifyDocumentSet?(@state.documentSet)
        when 'notifyDocument' then @viewApp.notifyDocument?(@state.get('document'))
        when 'setDocumentListParams' then @setDocumentListParams(e.data.args...)
        when 'openMetadataSchemaEditor' then @globalActions.openMetadataSchemaEditor()
        when 'patchDocument'
          attrs = e.data.args[0]
          if attrs.id != @document?.id
            console.log("Dropped message concerning document #{attrs.id} because we're on document #{@document?.id} now")
            return
          delete attrs.id
          @document.save(attrs, patch: true)

        else console.log("Invalid message from view: #{e.data.call}", e.data)

    remove: ->
      window.removeEventListener('message', @onMessageCallback, false)
      @viewApp.remove()
      @stopListening()
