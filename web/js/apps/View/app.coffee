define [
  'underscore'
  'jquery'
  'backbone'
  'bootstrap-modal'
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

      modalDialog: _.template("""
        <div id="view-app-modal-dialog" class="modal show" tabindex="-1" role="dialog">
          <div class="modal-dialog" role="document">
            <div class="modal-content">
              <div class="modal-body">
                <iframe id="view-app-modal-dialog-iframe" src="<%- url %>" width="1" height="1"></iframe>
              </div>
            </div>
          </div>
        </div>
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
      @modalDialog = null

      @render()

    remove: ->
      @setModalDialog(null)
      Backbone.View.prototype.remove.call(@)

    getServerUrl: ->
      serverUrl = @view.get('serverUrlFromPlugin')
      if !serverUrl?
        loc = window.location
        serverUrl = "#{loc.protocol}//#{loc.host}"
      serverUrl

    setDocumentDetailLink: (link) ->
      @view.save({ documentDetailLink: link }, { patch: true })

    setViewFilter: (viewFilter) ->
      @view.save({ filter: viewFilter }, { patch: true })

    setTitle: (title) ->
      @view.save({ title: title }, { patch: true })

    setViewFilterChoices: (choices) ->
      existingViewFilter = @view.get('filter')
      if existingViewFilter
        @view.save(
          { filter: Object.assign({}, existingViewFilter, { choices: choices }) },
          { patch: true }
        )

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
    onDocumentListChanged: (params) -> @notifyDocumentList(params)
    onDocumentSetChanged: (documentSet) -> @notifyDocumentSet(documentSet)
    onDocumentChanged: (document) -> @notifyDocument(document)

    notifyDocumentListParams: (params) ->
      @_postMessage
        event: 'notify:documentListParams'
        args: [ params ]

    notifyDocumentList: (params) ->
      @_postMessage
        event: 'notify:documentList'
        args: [ params ]

    notifyDocumentSet: (documentSet) ->
      @_postMessage
        event: 'notify:documentSet'
        args: [ documentSet.toJSON() ]

    notifyDocument: (document) ->
      @_postMessage
        event: 'notify:document'
        args: [ document ]

    postMessageToPluginIframes: (message) ->
      @_postMessage(message)

    _postMessage: (message) ->
      targetOrigin = @view.get('url').split('/')[0...3].join('/')
      @iframe.contentWindow.postMessage(message, targetOrigin)
      if @rightPaneIframe
        @rightPaneIframe.contentWindow.postMessage(message, targetOrigin)
      if @modalDialog
        @modalDialog.querySelector('iframe').contentWindow.postMessage(message, targetOrigin)

    setRightPane: (options) ->
      throw 'Must pass options.url, a fully-qualified HTTP(S) URL' if 'url' not of options

      if options.url
        @rightPane.innerHTML = @templates.rightPane({ url: options.url })
        @main.classList.add('has-right-pane')
      else
        @rightPane.innerHTML = ''
        @main.classList.remove('has-right-pane')

      @rightPaneIframe = @rightPane.querySelector('iframe')

    setModalDialog: (options) ->
      throw 'Must pass options.url, a fully-qualified HTTP(S) URL' if options? && 'url' not of options

      if @modalDialog?
        Backbone.$(@modalDialog)
          .modal('hide')
          .remove()
        @modalDialog = null

      if options?.url
        @modalDialog = $(@templates.modalDialog({ url: options.url }))[0]
        document.body.appendChild(@modalDialog)
        _this = this
        Backbone.$(@modalDialog)
          .modal()
          .on 'hidden.bs.modal', ->
            Backbone.$(this).remove()
            if this == _this.modalDialog
              _this.modalDialog = null
