define [
  'underscore'
  'jquery'
  'backbone'
], (_, $, Backbone) ->
  class ViewApp extends Backbone.View
    templates:
      viewApp: _.template("""
        <div class="view-app">
          <iframe id="view-app-iframe" width="1" height="1" src="<%- url %>/show?<%- params %>"></iframe>
        </div>
      """)

      rightPane: _.template("""
        <iframe id="view-app-right-pane-iframe" width="1" height="1" src="<%- url %>"></iframe>
      """)

    initialize: (options) ->
      throw 'Must pass options.view, a View' if !options.view
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId
      throw 'Must pass options.main, an HTMLElement' if !options.main

      @view = options.view
      @main = options.main
      @documentSetId = options.documentSetId

      @rightPane = @main.querySelector('#tree-app-right-pane')
      throw 'options.main must have a #tree-app-right-pane child' if !@rightPane

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

      html = @templates.viewApp
        url: @view.get('url')
        params: params
      @$el.html(html)

      @iframe = @$('iframe').get(0)
      @rightPane.innerHTML = ''
      @rightPaneIframe = null
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
      if @rightPaneIframe
        @rightPaneIframe.contentWindow.postMessage(message, targetOrigin)

    setRightPane: (options) ->
      throw 'Must pass options.url, a fully-qualified HTTP(S) URL' if 'url' not of options

      if options.url
        @rightPane.innerHTML = @templates.rightPane({ url: options.url })
        @main.classList.add('has-right-pane')
      else
        @rightPane.innerHTML = ''
        @main.classList.remove('has-right-pane')

      @rightPaneIframe = @rightPane.querySelector('iframe')
