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

    onDocumentListParamsChanged: (__, value) -> @vizApp.onDocumentListParamsChanged?(value)
    onDocumentChanged: (__, value) -> @vizApp.onDocumentChanged?(value)
    onTaglikeCidChanged: (__, value) -> @vizApp.onTaglikeCidChanged?(value)
    onTag: (tag, params) -> @vizApp.onTag?(tag, params)
    onUntag: (tag, params) -> @vizApp.onUntag?(tag, params)

    remove: ->
      @vizApp.remove()
      @stopListening()
