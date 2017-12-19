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

      @listenTo(@state.documentSet, 'change', @onDocumentSetChanged)
      @listenTo(@state, 'change:documentList', @onDocumentListChanged)
      @listenTo(@state, 'change:document', @onDocumentChanged)
      @listenTo(@state, 'tag', @onTag)
      @listenTo(@state, 'untag', @onUntag)
      @onMessageCallback = @_onMessage.bind(@)
      window.addEventListener('message', @onMessageCallback, false)

      @documentList = @document = null
      @_setDocumentList(@state.get('documentList'))
      @_setDocument(@state.get('document'))

    _setDocumentList: (documentList) ->
      if @documentList?
        @stopListening(@documentList)

      @documentList = documentList

      if @documentList?
        @listenTo(@documentList, 'change', @onDocumentListAttributesChanged)

    _setDocument: (document) ->
      if @document?
        @stopListening(@document)

      @document = document

      if @document?
        @listenTo(@document, 'change', @onDocumentAttributesChanged)

    onDocumentListChanged: (__, value) ->
      @_setDocumentList(value)
      @viewApp.onDocumentListParamsChanged?(@documentList?.params ? null)
      @viewApp.onDocumentListChanged?(length: @documentList?.get?('length') ? null)

    onDocumentListAttributesChanged: (documentList) ->
      object = {
        length: documentList?.get?('length') ? null
      }
      @viewApp.onDocumentListChanged?(object)

    onDocumentSetChanged: (__, value) -> @viewApp.onDocumentSetChanged?(@state.documentSet)
    onTag: (tag, params) -> @viewApp.onTag?(tag, params)
    onUntag: (tag, params) -> @viewApp.onUntag?(tag, params)

    _currentDocumentJson: () ->
      return null if !@document?

      indexInDocumentList = null
      if @documentList?
        idx = @documentList.documents.findIndex((d) => d.id == @document.id)
        indexInDocumentList = idx if idx != -1

      {
        id: @document.id,
        title: @document.get('title') || '',
        snippet: @document.get('snippet') || '',
        pageNumber: @document.get('pageNumber') || null,
        url: @document.get('url') || null,
        metadata: @document.get('metadata') || {},
        indexInDocumentList: indexInDocumentList,
      }

    onDocumentChanged: (__, value) ->
      @_setDocument(value)
      @viewApp.onDocumentChanged?(@_currentDocumentJson())

    onDocumentAttributesChanged: () ->
      @viewApp.onDocumentChanged?(@_currentDocumentJson())

    setDocumentListParams: (params) -> @state.setDocumentListParams(params)
    refineDocumentListParams: (params) -> @state.refineDocumentListParams(params)

    setViewFilterSelection: (selection) ->
      viewId = @viewApp?.view?.id
      viewId = viewId.replace(/^\w+-/, '') # "view-1234" => "1234" -- "view-1234" is ugly, but it happens 2011-11-27
      filters = {}
      filters[viewId] = selection
      @state.refineDocumentListParams({ filters: filters })

    _onMessage: (e) ->
      viewUrl = @viewApp?.view?.attributes?.url || '' # _any_ iframe, e.g. Twitter, can post a message
      viewUrl = "#{window.location.protocol}#{viewUrl}" if viewUrl.substring(0, 2) == '//'
      if e.origin != viewUrl.substring(0, e.origin.length)
        console.log("Dropped message from #{e.origin}: expected #{viewUrl}", e)
        return

      switch e.data.call
        when 'notifyDocumentListParams' then @viewApp.notifyDocumentListParams?(@documentList?.params)
        when 'notifyDocumentList' then @viewApp.notifyDocumentList?(length: @documentList?.get?('length') ? null)
        when 'notifyDocumentSet' then @viewApp.notifyDocumentSet?(@state.documentSet)
        when 'notifyDocument' then @viewApp.notifyDocument?(@_currentDocumentJson())
        when 'postMessageToPluginIframes' then @viewApp.postMessageToPluginIframes?(e.data.message || null)
        when 'setDocumentListParams' then @setDocumentListParams(e.data.args...)
        when 'refineDocumentListParams' then @refineDocumentListParams(e.data.args...)
        when 'setRightPane' then @viewApp.setRightPane?(e.data.args...)
        when 'setModalDialog' then @viewApp.setModalDialog?(e.data.args...)
        when 'setDocumentDetailLink' then @viewApp.setDocumentDetailLink?(e.data.args...)
        when 'setViewFilter' then @viewApp.setViewFilter?(e.data.args...)
        when 'setViewFilterChoices' then @viewApp.setViewFilterChoices?(e.data.args...)
        when 'setViewFilterSelection' then @setViewFilterSelection(e.data.args...)
        when 'setViewTitle' then @viewApp.setTitle?(e.data.args[0]?.title)
        when 'openMetadataSchemaEditor' then @globalActions.openMetadataSchemaEditor()
        when 'goToNextDocument' then @globalActions.goToNextDocument()
        when 'goToPreviousDocument' then @globalActions.goToPreviousDocument()
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
