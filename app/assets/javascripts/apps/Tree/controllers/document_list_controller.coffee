define [
  'underscore',
  'jquery',
  'backbone',
  '../models/DocumentList'
  '../models/list_selection'
  '../views/DocumentList'
  '../views/DocumentListTitle'
  '../views/DocumentListCursor'
  './ListSelectionController'
  './node_form_controller'
  './tag_form_controller'
  './logger'
  'apps/DocumentDisplay/app'
], (_, $, Backbone, DocumentList, ListSelection, DocumentListView, DocumentListTitleView, DocumentListCursorView, ListSelectionController, node_form_controller, tag_form_controller, Logger, DocumentDisplayApp) ->
  log = Logger.for_component('document_list')
  DOCUMENT_LIST_REQUEST_SIZE = 20

  DocumentsUrl = window.location.pathname.split('/').slice(0, 3).join('/') + '/documents'

  doUntilWithSetInterval = (func, test, period) ->
    interval = undefined

    loopFunc = ->
      func()
      if test()
        window.clearInterval(interval)
      # no race: this function is atomic, because JS is single-threaded.

    func()
    if !test()
      interval = setInterval(loopFunc, period)
    else
      interval = undefined

  VIEW_OPTIONS = {
    buffer_documents: 5,
  }

  tag_to_short_string = (tag) ->
    "#{tag.id} (#{tag.attributes.name})"

  node_to_short_string = (node) ->
    "#{node.id} (#{node.description})"

  node_diff_to_string = (node1, node2) ->
    changed = false
    s = ''
    if node1.description != node2.description
      s += " description: <<#{node1.description}>> to <<#{node2.description}>>"
    if !changed
      s += ' (no change)'
    s

  class Controller extends Backbone.Model
    # Properties set on initialize:
    # * tags (a Tags)
    # * state (a State)
    # * listEl (an HTMLElement)
    # * cursorEl (an HTMLElement)
    #
    # Properties created here, whose attributes may change:
    # * listSelection (a ListSelectionController)
    # * listView
    # * cursorView
    #
    # Read-only Backbone attributes, which can be replaced entirely:
    # * documentList (a DocumentList, always non-null)
    defaults:
      documentList: null

    initialize: (attrs, options) ->
      throw 'Must specify options.tags, a Tags' if !options.tags
      throw 'Must specify options.state, a State' if !options.state
      throw 'Must specify options.listEl, an HTMLElement' if !options.listEl
      throw 'Must specify options.cursorEl, an HTMLElement' if !options.cursorEl
      throw 'FIXME must specify options.onDemandtree for now, an OnDemandTree' if !options.onDemandTree

      @tags = options.tags
      @state = options.state
      @onDemandTree = options.onDemandTree
      @listEl = options.listEl
      @cursorEl = options.cursorEl

      @_addDocumentList()
      @_addListSelection()
      @_addTitleView()
      @_addListView()
      @_addCursorView()

    _addDocumentList: ->
      refresh = =>
        old = @get('documentList')
        if old?
          old.stopListening()
          @stopListening(old)

        params = @state.get('documentListParams')
        documentList = new DocumentList({}, params: params, url: DocumentsUrl)
        @set(documentList: documentList)

      @listenTo(@state, 'change:documentListParams', refresh)
      refresh()

    _addListSelection: ->
      isValidIndex = (i) => @get('documentList')?.documents?.at(i)?.id?

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
      # * here, we set the state's document to the first selected one.
      #   (Because we assume there is only one -- if we want to change this,
      #   we need to make the state have multiple documents.)
      # * TODO on state document change, we modify listSelection. (We only do
      #   this when changing oneDocumentSelected. Will the rest be needed?)
      setStateSelectionFromListSelection = =>
        cursorIndex = @listSelection.get('cursorIndex')
        collection = @get('documentList')?.documents
        document = cursorIndex? && collection?.at(cursorIndex) || null

        @state.set({
          document: document
          oneDocumentSelected: cursorIndex?
        }, { fromDocumentListController: true })

      setListSelectionFromStateSelection = (__, ___, options) =>
        return if options?.fromDocumentListController

        # If we're navigating individual documents and we change selection, go
        # to the top of the new document list.
        #
        # The new doclist won't have loaded, so documentId will be null. We
        # catch that by watching for add() on documentCollection.
        if @state.get('oneDocumentSelected')
          @listSelection.set
            cursorIndex: 0
            selectedIndices: [0]
        else
          @listSelection.onSelectAll()

      @listenTo(@listSelection, 'change:cursorIndex', setStateSelectionFromListSelection)
      @listenTo(@state, 'change:oneDocumentSelected', setListSelectionFromStateSelection)
      @on 'change:documentList', =>
        setListSelectionFromStateSelection() # may call setStateSelectionFromListSelection
        oldDocCollection = @previous('documentList')?.documents
        newDocCollection = @get('documentList')?.documents
        @stopListening(oldDocCollection) if oldDocCollection?
        @listenToOnce(newDocCollection, 'add', setStateSelectionFromListSelection) if newDocCollection?

    _addTitleView: ->
      view = new DocumentListTitleView
        documentList: @get('documentList')

      @on 'change:documentList', (__, documentList) ->
        view.setDocumentList(documentList)

      @listenTo view, 'edit-node', (node) =>
        log('began editing node', node_to_short_string(node))
        node_form_controller(node, @onDemandTree)

      @listenTo view, 'edit-tag', (tag) ->
        log('clicked edit tag', tag_to_short_string(tag))
        tag_form_controller(tag)

      @titleView = view
      view.$el.appendTo(@listEl)

    _addListView: ->
      view = new DocumentListView
        collection: @get('documentList').documents
        selection: @listSelection
        tags: @tags

      @listenTo view, 'click-document', (model, index, options) =>
        log('clicked document', "#{model.id} index:#{index} meta:#{options.meta} shift: #{options.shift}")
        @listSelection.onClick(index, options)

      pageSizeBuffer = 5 # how far from the end we begin a request for more
      neededIndex = 0 # index we want visible
      fetching = false

      fetchAnotherPageIfNeeded = =>
        documentList = @get('documentList')

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
        documentList = @get('documentList')
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

      @on 'change:documentList', (__, documentList) ->
        view.setCollection(documentList.documents)
        startFetching()

      startFetching()

      @listView = view
      view.$el.appendTo(@listEl)

    _addCursorView: ->
      view = new DocumentListCursorView
        selection: @listSelection
        documentList: @get('documentList')
        documentDisplayApp: DocumentDisplayApp
        tags: @tags
        el: @cursorEl

      @on 'change:documentList', (__, documentList) ->
        view.setDocumentList(documentList)

      @cursorView = view

  document_list_controller = (listDiv, cursorDiv, documentSet, state, onDemandTree) ->
    # FIXME remove onDemandTree requirement by making Node a Backbone.Model
    controller = new Controller({},
      tags: documentSet.tags
      state: state
      onDemandTree: onDemandTree
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

      docid = controller.get('documentList')?.documents?.at?(new_index)?.id
      log("went #{up_or_down}", "docid:#{docid} index:#{new_index} meta:#{options.meta} shift: #{options.shift}")

      listSelection[func](options)

    go_down = (event) -> go_up_or_down('down', event)
    go_up = (event) -> go_up_or_down('up', event)
    select_all = (event) -> controller.listSelection.onSelectAll()

    (->
      view = controller.cursorView
      view.on('next-clicked', -> go_down({}))
      view.on('previous-clicked', -> go_up({}))
      view.on('list-clicked', -> select_all({}))

      # With content <a></a><b></b>, CSS can match "a:hover ~ b", but
      # there's no way to do the reverse "b:hover ~ a".
      $listView = controller.listView.$el
      view.$el.on('mouseenter', -> $listView.addClass('hover'))
      view.$el.on('mouseleave', -> $listView.removeClass('hover'))
    )()

    {
      go_up: go_up
      go_down: go_down
      select_all: select_all
    }
