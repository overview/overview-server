define [
  'underscore'
  'jquery'
  'backbone'
], (_, $, Backbone) ->
  class ViewApp extends Backbone.View
    template: _.template("""
      <div class="view-app">
        <iframe id="view-app-iframe" width="1" height="1" src="<%- url %>/show?<%- params %>"></iframe>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.view, a View' if !options.view
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId

      @view = options.view
      @documentSetId = options.documentSetId

      @render()

    getServerUrl: ->
      serverUrl = @view.get('serverUrlFromPlugin')
      if !serverUrl?
        loc = window.location
        serverUrl = "#{loc.protocol}//#{loc.host}"
      serverUrl

    render: ->
      params = $.param([
        { name: 'server', value: @getServerUrl() }
        { name: 'documentSetId', value: @documentSetId }
        { name: 'apiToken', value: @view.get('apiToken') }
      ])

      html = @template
        url: @view.get('url')
        params: params
      @$el.html(html)

      @iframe = @$('iframe').get(0)
      @

    onDocumentListParamsChanged: (params) -> @notifyDocumentListParams(params)
    onDocumentSetChanged: (documentSet) -> @notifyDocumentSet(documentSet)
    onDocumentChanged: (document) -> @notifyDocument(document)

    notifyDocumentListParams: (params) ->
      @_postMessage
        event: 'notify:documentListParams'
        args: [ params ]

    notifyDocumentSet: (documentSet) ->
      @_postMessage
        event: 'notify:documentSet'
        args: [ documentSet.toJSON() ]

    notifyDocument: (document) ->
      @_postMessage
        event: 'notify:document'
        args: [ document && document.toJSON() ]

    _postMessage: (message) ->
      targetOrigin = @view.get('url').split('/')[0...3].join('/')
      @iframe.contentWindow.postMessage(message, targetOrigin)
