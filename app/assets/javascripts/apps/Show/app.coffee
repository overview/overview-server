define [
  'underscore'
  'jquery'
  'backbone'
  './models/TransactionQueue'
  './models/DocumentSet'
  './models/State'
  './controllers/KeyboardController'
  './controllers/logger'
  './controllers/VizsController'
  './controllers/tag_list_controller'
  './controllers/search_result_list_controller'
  './controllers/document_list_controller'
  './controllers/document_contents_controller'
  './controllers/VizAppController'
  './controllers/TourController'
  './views/TransactionQueueErrorMonitor'
  './views/Mode'
  '../Tree/app'
  '../Job/app'
], (_, $, Backbone, \
    TransactionQueue, DocumentSet, State, \
    KeyboardController, Logger, \
    VizsController, tag_list_controller, search_result_list_controller, \
    document_list_controller, document_contents_controller, \
    VizAppController, \
    TourController, \
    TransactionQueueErrorMonitor, \
    ModeView, \
    TreeApp, JobApp) ->

  class App
    constructor: (options) ->
      throw 'need options.mainEl' if !options.mainEl

      @el = options.mainEl

      # TODO remove searchDisabled entirely
      @searchDisabled = @el.getAttribute('data-is-searchable') == 'false'
      @tourEnabled = @el.getAttribute('data-tooltips-enabled') == 'true'

      transactionQueue = @_initializeTransactionQueue()
      documentSet = @_initializeDocumentSet(transactionQueue)
      documentSet.vizs.on('reset', => _.defer(=> @_initializeUi(documentSet)))

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
        <div id="tree-app-left">
          <div id="tree-app-search"></div>
          <div id="tree-app-vizs"></div>
          <div id="tree-app-viz"></div>
          <div id="tree-app-tags"></div>
        </div>
        <div id="tree-app-right">
          <div id="tree-app-document-list"></div>
          <div id="tree-app-document-cursor"></div>
        </div>
        <div id="transaction-queue-error-monitor">
        </div>
      """

      $(@el).html(html)

      if @searchDisabled
        $('#tree-app-search').remove()
        $('#tree-app-left').addClass('search-disabled')

      el = (id) -> document.getElementById(id)

      main: @el
      vizs: el('tree-app-vizs')
      viz: el('tree-app-viz')
      tags: el('tree-app-tags')
      search: el('tree-app-search')
      documentList: el('tree-app-document-list')
      documentCursor: el('tree-app-document-cursor')
      document: el('tree-app-document')
      transactionQueueErrorMonitor: el('transaction-queue-error-monitor')

    _initializeTransactionQueue: ->
      transactionQueue = new TransactionQueue()

      # Override Backbone.ajax so all Backbone operations use transactionQueue
      #
      # XXX This means collection.fetch()`.done()` and `.fail()` will not work:
      # we use real Promise objects, not jQuery ones. You can use `success:`
      # and `error:` callbacks, or use the two-argument `.then()`.
      Backbone.ajax = (args...) -> transactionQueue.ajax(args...)

      transactionQueue

    _initializeDocumentSet: (transactionQueue) ->
      documentSetId = +window.location.pathname.split('/')[2]
      documentSet = new DocumentSet(documentSetId, transactionQueue)
      # We just kicked off the initial server request

    _initializeUi: (documentSet) ->
      els = @_buildHtml()
      keyboardController = new KeyboardController(document)

      viz = null
      if (m = ///^\/documentsets\/#{documentSet.id}\/([a-zA-Z]+-[0-9]+)$///.exec(document.location.pathname))?
        viz = documentSet.vizs.get(m[1])
      viz ||= documentSet.vizs.at(0)

      state = new State(documentListParams: documentSet.documentListParams(viz).all(), viz: viz)

      state.on 'change:viz', (__, viz) ->
        # Change URL so a page refresh brings us to this viz
        url = "/documentsets/#{documentSet.id}/#{viz.id}"
        window.history?.replaceState(url, '', url)

      controller = new VizsController(documentSet.vizs, state)
      els.vizs.appendChild(controller.el)

      document_contents_controller
        state: state
        keyboardController: keyboardController

      new ModeView(el: @el, state: state)

      tag_list_controller
        documentSet: documentSet
        state: state
        el: els.tags

      if !@searchDisabled
        search_result_list_controller
          documentSet: documentSet
          state: state
          el: els.search

      @_listenForRefocus()
      @_listenForResize(els.document)

      document_list_controller(els.documentList, els.documentCursor, documentSet, state, keyboardController)

      new VizAppController
        el: els.viz
        state: state
        documentSet: documentSet
        transactionQueue: documentSet.transactionQueue
        keyboardController: keyboardController
        vizAppConstructors:
          viz: TreeApp
          job: JobApp
          error: JobApp

      new TransactionQueueErrorMonitor
        model: documentSet.transactionQueue
        el: els.transactionQueueErrorMonitor

      if @tourEnabled
        TourController()
