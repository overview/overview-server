define [
  'underscore',
  'jquery',
  'backbone',
  '../models/DocumentList'
  '../models/list_selection'
  '../controllers/TagDialogController'
  '../views/DocumentList'
  '../views/DocumentListTitle'
  '../views/DocumentListCursor'
  '../views/TagThis'
  './ListSelectionController'
  'apps/DocumentDisplay/app'
], (_, $, Backbone, DocumentList, ListSelection, TagDialogController, DocumentListView, DocumentListTitleView, DocumentListCursorView, TagThisView, ListSelectionController, DocumentDisplayApp) ->
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
    # * keyboardController (a KeyboardController)
    # * listEl (an HTMLElement)
    # * titleEl (an HTMLElement)
    # * cursorEl (an HTMLElement)
    # * tagThisEl (an HTMLElement)
    #
    # Properties created here, whose attributes may change:
    # * listSelection (a ListSelectionController)
    # * listView
    # * cursorView
    initialize: (attrs, options) ->
      throw 'Must specify options.tags, a Tags' if !options.tags
      throw 'Must specify options.state, a State' if !options.state
      throw 'Must specify options.keyboardController, a KeyboardController' if !options.keyboardController
      throw 'Must specify options.listEl, an HTMLElement' if !options.listEl
      throw 'Must specify options.titleEl, an HTMLElement' if !options.titleEl
      throw 'Must specify options.cursorEl, an HTMLElement' if !options.cursorEl
      throw 'Must specify options.tagThisEl, an HTMLElement' if !options.tagThisEl

      @tags = options.tags
      @state = options.state
      @keyboardController = options.keyboardController

      @listEl = options.listEl
      @titleEl = options.titleEl
      @cursorEl = options.cursorEl
      @tagThisEl = options.tagThisEl

      @_addListSelection()
      @_addTitleView()
      @_addListView()
      @_addCursorView()
      @_addTagThisView()

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

    _addTitleView: ->
      view = new DocumentListTitleView
        documentList: @state.get('documentList')
        state: @state
        el: @titleEl

      @listenTo @state, 'change:documentList', (__, documentList) ->
        view.setDocumentList(documentList)

      @titleView = view

    _addListView: ->
      view = new DocumentListView
        model: @state.get('documentList')
        selection: @listSelection
        tags: @tags
        el: @listEl

      @listenTo view, 'tag-remove-clicked', (event) =>
        tag = @tags.get(event.tagCid)
        queryParams = { documents: String(event.documentId || 0) }
        @state.untag(tag, queryParams)

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
        documentDisplayApp: DocumentDisplayApp
        tags: @tags
        el: @cursorEl

      @listenTo view, 'tag-remove-clicked', (event) =>
        tag = @tags.get(event.tagCid)
        queryParams = { documents: String(event.documentId || 0) }
        @state.untag(tag, queryParams)

      @listenTo @state, 'change:documentList', (__, documentList) ->
        view.setDocumentList(documentList)

      @cursorView = view

    _addTagThisView: ->
      view = new TagThisView
        state: @state
        tags: @tags
        keyboardController: @keyboardController
        el: @tagThisEl

      @tagThisView = view

  document_list_controller = (titleDiv, listDiv, tagThisDiv, cursorDiv, state, keyboardController) ->
    controller = new Controller({},
      tags: state.tags
      state: state
      keyboardController: keyboardController
      listEl: listDiv
      titleEl: titleDiv
      cursorEl: cursorDiv
      tagThisEl: tagThisDiv
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

      docid = state.get('documentList')?.documents?.at?(new_index)?.id

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

    (->
      view = controller.tagThisView

      view.on 'create-clicked', (name) ->
        tags = state.tags
        tag = tags.create(name: name)
        params = state.getSelectionQueryParams()
        state.tag(tag, params)

      view.on 'add-clicked', (tag) ->
        params = state.getSelectionQueryParams()
        state.tag(tag, params)

      view.on 'remove-clicked', (tag) ->
        params = state.getSelectionQueryParams()
        state.untag(tag, params)

      view.on 'organize-clicked', ->
        new TagDialogController(tags: state.tags, state: state)
    )()

    keyboardController.register
      J: go_down    # GMail
      K: go_up      # GMail
      U: select_all # GMail
      'Control+A': select_all # Windows, Mac, Linux
