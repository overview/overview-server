define [
  'underscore'
  'jquery'
  'backbone'
  './models/DocumentSet'
  './models/TransactionQueue'
  './models/State'
  './controllers/KeyboardController'
  './controllers/ViewsController'
  './controllers/tag_list_controller'
  './controllers/document_list_controller'
  './controllers/ViewAppController'
  './controllers/TourController'
  './views/TransactionQueueErrorMonitor'
  './views/ExportDialog'
  './views/Mode'
  './views/VerticalSplit'
  './views/DocumentListTitle'
  '../Tree/app'
  '../View/app'
  '../MetadataSchemaEditor/app'
  '../DocumentListParamsSelector/App'
], (_, $, Backbone, \
    DocumentSet, TransactionQueue, State, \
    KeyboardController, \
    ViewsController, tag_list_controller, document_list_controller, \
    ViewAppController, \
    TourController, \
    TransactionQueueErrorMonitor, \
    ExportDialog, \
    ModeView, VerticalSplitView, \
    DocumentListTitleView, \
    TreeApp, ViewApp, MetadataSchemaEditorApp, DocumentListParamsSelectorApp) ->

  class App
    constructor: (options) ->
      throw 'need options.mainEl' if !options.mainEl

      @el = options.mainEl

      @tourEnabled = @el.getAttribute('data-tooltips-enabled') == 'true'

      @transactionQueue = @_initializeTransactionQueue()
      documentSetId = window.location.pathname.split('/')[2]
      @documentSet = new DocumentSet(id: documentSetId)
      @documentSet.fetch()
      @documentSet.once 'sync', =>
        @documentSet.views.pollUntilStable()
        @state = new State({}, documentSet: @documentSet, transactionQueue: @transactionQueue)
        @_initializeUi()

    _listenForRefocus: ->
      refocus = ->
        # Pull focus out of the iframe.
        #
        # We can't listen for events on the document iframe; so if it's present,
        # it breaks keyboard shortcuts. We need to re-grab focus whenever we can
        # without disturbing the user.
        #
        # For instance, if the user is logging in to DocumentCloud in the iframe,
        # we don't want to steal focus; so a timer is bad, and a mousemove handler
        # is bad. But if we register a click, it's worth using that to steal focus.
        window.focus() if document.activeElement?.tagName == 'IFRAME'

      refocus_body_on_leave_window = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/321
        hidden = undefined

        callback = (e) ->
          if !document[hidden]
            refocus()

        if document[hidden]?
          document.addEventListener("visibilitychange", callback)
        else if document[hidden = "mozHidden"]?
          document.addEventListener("mozvisibilitychange", callback)
        else if document[hidden = "webkitHidden"]?
          document.addEventListener("webkitvisibilitychange", callback)
        else if document[hidden = "msHidden"]?
          document.addEventListener("msvisibilitychange", callback)
        else
          hidden = undefined

      refocus_body_on_event = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/362
        $('body').on 'click', (e) ->
          refocus()

      refocus_body_on_leave_window()
      refocus_body_on_event()
      undefined

    _listenForResize: (documentEl) ->
      $documentEl = $(documentEl)

      refreshWidth = ->
        # Round the iframe's parent's width, because it needs an integer number of px
        $documentEl.find('iframe')
          .width(1)
          .width($documentEl.width())

      throttledRefreshWidth = _.throttle(refreshWidth, 100)

      $(window).resize(throttledRefreshWidth)

      refreshWidth()

    _buildHtml: ->
      html = """
        <div id="tree-app-left"
          ><div id="document-list-params"></div
          ><div id="tree-app-views"></div
          ><div id="tree-app-view"></div
        ></div
        ><div id="tree-app-vertical-split"></div
        ><div id="tree-app-right"
          ><div id="document-list-container"
            ><div class="header"
              ><div id="document-list-title"></div
              ><div id="tree-app-tag-this"></div
            ></div
            ><div id="document-list"></div
          ></div
          ><div id="document-current"></div
        ></div
        ><div id="transaction-queue-error-monitor"></div
        ><div id="metadata-schema-editor-app"></div
      """

      $(@el).html(html)

      el = (id) -> document.getElementById(id)

      main: @el
      views: el('tree-app-views')
      view: el('tree-app-view')
      tags: el('tree-app-tags')
      search: el('tree-app-search')
      documentListParams: el('document-list-params')
      documentList: el('document-list')
      documentListTitle: el('document-list-title')
      tagThis: el('tree-app-tag-this')
      documentCursor: el('document-current')
      document: el('tree-app-document')
      transactionQueueErrorMonitor: el('transaction-queue-error-monitor')
      metadataSchemaEditorApp: el('metadata-schema-editor-app')

    _initializeTransactionQueue: ->
      transactionQueue = new TransactionQueue()

      # Override Backbone.ajax so all Backbone operations use transactionQueue
      #
      # XXX This means collection.fetch()`.done()` and `.fail()` will not work:
      # we use real Promise objects, not jQuery ones. You can use `success:`
      # and `error:` callbacks, or use the two-argument `.then()`.
      Backbone.ajax = (args...) -> transactionQueue.ajax(args...)

      transactionQueue

    _initializeUi: ->
      @_attachNavbar()

      # Choose the view that's in the URL
      if (m = /\/documentsets\/\d+\/([-_a-zA-Z0-9]+)/.exec(window.location.pathname))?
        if (view = @documentSet.views.get(m[1]))?
          @state.setView(view)

      els = @_buildHtml()
      keyboardController = new KeyboardController(document)

      @state.on 'change:view', (__, view) =>
        return if !view?
        # Change URL so a page refresh brings us to this view
        url = "/documentsets/#{@state.documentSetId}/#{view.id}"
        window.history?.replaceState(url, '', url)

      controller = new ViewsController(@documentSet.views, @state)
      els.views.appendChild(controller.el)

      new ModeView(el: @el, state: @state)
      new VerticalSplitView(el: @el, storage: window.localStorage, storageKey: 'ui.vertical-split.w1')

      @_listenForRefocus()
      @_listenForResize(els.document)

      tag_list_controller
        state: @state
        tags: @documentSet.tags
        tagSelectEl: els.tags
        tagThisEl: els.tagThis
        keyboardController: keyboardController

      @metadataSchemaEditorApp = new MetadataSchemaEditorApp(documentSet: @documentSet)
      els.metadataSchemaEditorApp.appendChild(@metadataSchemaEditorApp.el)
      @metadataSchemaEditorApp.render()

      @globalActions =
        openMetadataSchemaEditor: => @metadataSchemaEditorApp.show()

      document_list_controller(els.documentList, els.documentCursor, @state, keyboardController, @globalActions)
      new DocumentListTitleView(state: @state).$el.appendTo(els.documentListTitle)

      new DocumentListParamsSelectorApp({
        documentSet: @documentSet,
        state: @state,
        el: els.documentListParams,
        globalActions: @globalActions,
      })

      new ViewAppController
        el: els.view
        state: @state
        transactionQueue: @transactionQueue
        keyboardController: keyboardController
        globalActions: @globalActions
        viewAppConstructors:
          tree: TreeApp
          view: ViewApp

      new TransactionQueueErrorMonitor
        model: @transactionQueue
        el: els.transactionQueueErrorMonitor

      if @tourEnabled
        TourController()

    _attachNavbar: ->
      $('nav .show-export-options').on 'click', (e) =>
        e.preventDefault()
        ExportDialog.show(documentSet: @documentSet, documentList: @state.get('documentList'))

      $('nav .show-metadata-schema-editor').on 'click', (e) =>
        @globalActions.openMetadataSchemaEditor()
