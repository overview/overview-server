define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class ViewAppClient
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet
      throw 'Must pass options.viewApp, the View app instance' if !options.viewApp

      @state = options.state
      @documentSet = options.documentSet
      @viewApp = options.viewApp

      throw 'options.viewApp needs a remove() method which removes all traces of the view' if !@viewApp.remove

      @listenTo(@state, 'change:documentListParams', @onDocumentListParamsChanged)
      @listenTo(@state, 'change:document', @onDocumentChanged)
      @listenTo(@state, 'change:taglikeCid', @onTaglikeCidChanged)
      @listenTo(@documentSet, 'tag', @onTag)
      @listenTo(@documentSet, 'untag', @onUntag)
      @listener = window.addEventListener('message', @_onMessage.bind(@), false)

    onDocumentListParamsChanged: (__, value) -> @viewApp.onDocumentListParamsChanged?(value)
    onDocumentChanged: (__, value) -> @viewApp.onDocumentChanged?(value)
    onTaglikeCidChanged: (__, value) -> @viewApp.onTaglikeCidChanged?(value)
    onTag: (tag, params) -> @viewApp.onTag?(tag, params)
    onUntag: (tag, params) -> @viewApp.onUntag?(tag, params)

    setDocumentListParams: (params) ->
      @state.resetDocumentListParams().byJson(params)

    _onMessage: (e) ->
      viewUrl = @viewApp.view.attributes.url
      viewUrl = "#{window.location.protocol}#{viewUrl}" if viewUrl.substring(0, 2) == '//'
      if e.origin != viewUrl.substring(0, e.origin.length)
        console.log("Dropped message from #{e.origin}: expected #{viewUrl}", e)
        return

      switch e.data.call
        when 'setDocumentListParams' then @setDocumentListParams(e.data.args...)
        else console.log("Invalid message from view: #{e.data.call}", e.data)

    remove: ->
      @viewApp.remove()
      @stopListening()
