define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class VizAppClient
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet
      throw 'Must pass options.vizApp, the Viz app instance' if !options.vizApp

      @state = options.state
      @documentSet = options.documentSet
      @vizApp = options.vizApp

      throw 'options.vizApp needs a remove() method which removes all traces of the viz' if !@vizApp.remove

      @listenTo(@state, 'change:documentListParams', @onDocumentListParamsChanged)
      @listenTo(@state, 'change:document', @onDocumentChanged)
      @listenTo(@state, 'change:taglikeCid', @onTaglikeCidChanged)
      @listenTo(@documentSet, 'tag', @onTag)
      @listenTo(@documentSet, 'untag', @onUntag)
      @listener = window.addEventListener('message', @_onMessage.bind(@), false)

    onDocumentListParamsChanged: (__, value) -> @vizApp.onDocumentListParamsChanged?(value)
    onDocumentChanged: (__, value) -> @vizApp.onDocumentChanged?(value)
    onTaglikeCidChanged: (__, value) -> @vizApp.onTaglikeCidChanged?(value)
    onTag: (tag, params) -> @vizApp.onTag?(tag, params)
    onUntag: (tag, params) -> @vizApp.onUntag?(tag, params)

    setDocumentListParams: (params) ->
      @state.resetDocumentListParams().byJson(params)

    _onMessage: (e) ->
      vizUrl = @vizApp.viz.attributes.url
      vizUrl = "#{window.location.protocol}#{vizUrl}" if vizUrl.substring(0, 2) == '//'
      if e.origin != vizUrl.substring(0, e.origin.length)
        console.log("Dropped message from #{e.origin}: expected #{vizUrl}", e)
        return

      switch e.data.call
        when 'setDocumentListParams' then @setDocumentListParams(e.data.args...)
        else console.log("Invalid message from viz: #{e.data.call}", e.data)

    remove: ->
      @vizApp.remove()
      @stopListening()
