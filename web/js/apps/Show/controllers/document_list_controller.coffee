define [
  'underscore',
  'jquery',
  'backbone',
  '../models/list_selection'
  '../views/DocumentList'
  '../views/DocumentListCursor'
  './ListSelectionController'
  'apps/DocumentDisplay/app'
  'apps/CurrentDocumentMetadata/App'
], (_, $, Backbone, ListSelection, DocumentListView, DocumentListCursorView, ListSelectionController, DocumentDisplayApp, CurrentDocumentMetadataApp) ->
  class Controller extends Backbone.Model
    # Properties set on initialize:
    # * documentSet (a DocumentSet)
    # * tags (a Tags)
    # * state (a State)
    # * listEl (an HTMLElement)
    # * cursorEl (an HTMLElement)
    #
    # Properties created here, whose attributes may change:
    # * listSelection (a ListSelectionController)
    # * listView
    # * cursorView
    initialize: (attrs, options) ->
      throw 'Must specify options.documentSet, a DocumentSet' if !options.documentSet
      throw 'Must specify options.state, a State' if !options.state
      throw 'Must specify options.globalActions, an Object of Functions' if !options.globalActions
      throw 'Must specify options.listEl, an HTMLElement' if !options.listEl
      throw 'Must specify options.cursorEl, an HTMLElement' if !options.cursorEl

      @documentSet = options.documentSet
      @tags = @documentSet.tags
      @state = options.state
      @globalActions = options.globalActions

      @listEl = options.listEl
      @cursorEl = options.cursorEl

      @_addListSelection()
      @_addListView()
      @_addCursorView()

    _addListSelection: ->
      isValidIndex = (i) => @state.get('documentList')?.documents?.at(i)?.id?

      @listSelection = new ListSelectionController
        selection: new ListSelection()
        cursorIndex: undefined
        isValidIndex: isValidIndex
        platform: /Mac/.test(navigator.platform) && 'mac' || 'anything-but-mac'

      # Update the state's selection when the user clicks around or docs load.
      #
      # When the user clicks, here's what happens:
      #
      # * on click, listSelection changes selectedIndices.
      # * we grab the first (and, we assume, only) document.
      # * we set the state's document.
      setStateSelectionFromListSelection = =>
        cursorIndex = @listSelection.get('cursorIndex')
        collection = @state.get('documentList')?.documents
        document = cursorIndex? && collection?.at(cursorIndex) || null

        @state.set(document: document)

      @listenTo(@listSelection, 'change:cursorIndex', setStateSelectionFromListSelection)
      @listenTo(@state, 'change:documentList', => @listSelection.onSelectAll())
      @listenTo(@state, 'change:document', (__, newValue) => @listSelection.onSelectAll() if !newValue?)

    _addListView: ->
      view = new DocumentListView
        model: @state.get('documentList')
        selection: @listSelection
        tags: @tags
        el: @listEl

      @listenTo view, 'click-document', (model, index, options) =>
        @listSelection.onClick(index, options)

      pageSizeBuffer = 5 # how far from the end we begin a request for more
      neededIndex = 0 # index we want visible
      fetching = false

      fetchAnotherPageIfNeeded = =>
        documentList = @state.get('documentList')

        return if fetching
        return if !documentList?
        return if documentList.isComplete()
        return if documentList.documents.length >= neededIndex + pageSizeBuffer

        fetching = true
        documentList.fetchNextPage()
          .then(-> fetching = false)

      startFetching = () =>
        fetching = false
        neededIndex = 0
        documentList = @state.get('documentList')
        return if !documentList?

        fetchAnotherPageIfNeeded()
        @listenTo documentList, 'change:nPagesFetched', ->
          # When we receive this event, a promise is still being resolved
          # within DocumentList. So we wait for the promise to complete, then
          # fetch the new page.
          _.defer(fetchAnotherPageIfNeeded)

      @listenTo view, 'change:maxViewedIndex', (__, viewedIndex) ->
        neededIndex = viewedIndex
        fetchAnotherPageIfNeeded()

      @listenTo @state, 'change:documentList', (__, documentList) ->
        view.setModel(documentList)
        startFetching()

      startFetching()

      @listView = view

    _addCursorView: ->
      view = new DocumentListCursorView
        selection: @listSelection
        documentList: @state.get('documentList')
        documentDisplayApp: DocumentDisplayApp.default # TODO when switching to ES6 modules, nix ".default"
        documentMetadataApp: CurrentDocumentMetadataApp.create(state: @state, globalActions: @globalActions)
        tags: @tags
        views: @state.documentSet.views
        el: @cursorEl

      @listenTo @state, 'change:documentList', (__, documentList) ->
        view.setDocumentList(documentList)

      @cursorView = view

  document_list_controller = (listDiv, cursorDiv, state, keyboardController, globalActions) ->
    controller = new Controller({},
      documentSet: state.documentSet
      state: state
      globalActions: globalActions
      listEl: listDiv
      cursorEl: cursorDiv
    )

    go_up_or_down = (up_or_down, event) ->
      listSelection = controller.listSelection
      options = {
        meta: event.ctrlKey || event.metaKey || false
        shift: event.shiftKey || false
      }
      diff = up_or_down == 'down' && 1 || -1
      new_index = (listSelection.cursorIndex || -1) + diff
      func = up_or_down == 'down' && 'onDown' || 'onUp'

      listSelection[func](options)

    go_down = (event) -> go_up_or_down('down', event)
    go_up = (event) -> go_up_or_down('up', event)
    select_all = (event) -> controller.listSelection.onSelectAll()

    (->
      view = controller.cursorView
      view.on('next-clicked', -> go_down({}))
      view.on('previous-clicked', -> go_up({}))

      # With content <a></a><b></b>, CSS can match "a:hover ~ b", but
      # there's no way to do the reverse "b:hover ~ a".
      $listView = controller.listView.$el
      view.$el.on('mouseenter', -> $listView.addClass('hover'))
      view.$el.on('mouseleave', -> $listView.removeClass('hover'))
    )()

    keyboardController.register
      J: go_down    # GMail
      K: go_up      # GMail
      U: select_all # GMail
      'Control+A': select_all # Windows, Mac, Linux

    globalActions.goToNextDocument = () => go_down({})
    globalActions.goToPreviousDocument = () => go_up({})
    globalActions.beginCreatePdfNote = () => controller.cursorView.documentDisplayApp.beginCreatePdfNote()
    globalActions.goToPdfNote = (pdfNote) => controller.cursorView.documentDisplayApp.goToPdfNote(pdfNote)
