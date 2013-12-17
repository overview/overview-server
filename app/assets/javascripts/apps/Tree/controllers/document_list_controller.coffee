define [
  'jquery',
  'backbone',
  '../models/document_list'
  '../models/list_selection'
  '../collections/DocumentListProxy'
  '../collections/TagStoreProxy'
  '../collections/SearchResultStoreProxy'
  '../views/node_form_view'
  '../views/DocumentList'
  '../views/DocumentListTitle'
  '../views/DocumentListCursor'
  './ListSelectionController'
  './node_form_controller'
  './tag_form_controller'
  './logger'
  'apps/DocumentDisplay/app'
], ($, Backbone, DocumentList, ListSelection, DocumentListProxy, TagStoreProxy, SearchResultStoreProxy, NodeFormView, DocumentListView, DocumentListTitleView, DocumentListCursorView, ListSelectionController, node_form_controller, tag_form_controller, Logger, DocumentDisplayApp) ->
  log = Logger.for_component('document_list')
  DOCUMENT_LIST_REQUEST_SIZE = 20

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
    "#{tag.id} (#{tag.name})"

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
    # Only set on initialize (properties may change):
    # * tagStore (a TagStore)
    # * documentStore (a DocumentStore)
    # * cache (a Cache)
    # * state (a State)
    # * listEl (an HTMLElement)
    # * cursorEl (an HTMLElement)
    #
    # Read-only, may change or be set to something new:
    # * documentList (a DocumentList, may be undefined)
    # * documentListProxy (a DocumentListProxy, may be undefined)
    # * documentCollection (a Backbone.Collection, always defined)
    #
    # Read-only, only properties may change:
    # * listSelection (a ListSelectionController)
    # * tagCollection (a Backbone.Collection)
    # * listView
    # * cursorView
    defaults:
      documentStore: undefined
      tagStore: undefined
      state: undefined
      cache: undefined

      documentList: undefined

      listSelection: undefined
      tagCollection: undefined
      documentCollection: undefined

    initialize: (attrs, options) ->
      throw 'Must specify tagStore, a TagStore' if !attrs.tagStore
      throw 'Must specify searchResultStore, a SearchResultStore' if !attrs.searchResultStore
      throw 'Must specify documentStore, a DocumentStore' if !attrs.documentStore
      throw 'Must specify state, a State' if !attrs.state
      throw 'Must specify cache, a Cache' if !attrs.cache

      @state = @get('state')
      @cache = @get('cache')

      @_addDocumentList()
      @_addListSelection()
      @_addTagCollection()
      @_addSearchResultCollection()
      @_addDocumentCollection()
      @_addTitleView()
      @_addListView()
      @_addCursorView()

    _refreshDocumentList: ->
      params = @state.get('documentListParams')
      documentList = new DocumentList(@cache, params)
      @set(documentList: documentList)

    _addDocumentList: ->
      @listenTo(@state, 'change:documentListParams', @_refreshDocumentList)
      @_refreshDocumentList()

    _addTagCollection: ->
      @tagStoreProxy = new TagStoreProxy(@get('tagStore'))
      @set('tagCollection', @tagStoreProxy.collection)

    _addSearchResultCollection: ->
      @searchResultStoreProxy = new SearchResultStoreProxy(@get('searchResultStore'))
      @set('searchResultCollection', @searchResultStoreProxy.collection)

      @listenTo @get('searchResultCollection'), 'change', (model) =>
        if (params = @state.get('documentListParams'))? && params.type == 'searchResult' && params.searchResultId == model.id
          # Create the same list again: its contents have changed
          @_refreshDocumentList()

    _addDocumentCollection: ->
      documentStore = @get('documentStore')
      tagStore = @get('tagStore')

      refresh = =>
        @get('documentListProxy')?.destroy()
        documentList = @get('documentList')
        if documentList
          documentListProxy = new DocumentListProxy(documentList)
          @set('documentListProxy', documentListProxy)
          @set('documentCollection', documentListProxy.model.documents)
        else
          @set('documentListProxy', undefined)
          @set('documentCollection', new Backbone.Collection([]))

      @on('change:documentList', refresh)
      refresh()

    _addListSelection: ->
      isValidIndex = (i) => @get('documentCollection')?.at(i)?.id?
      @set('listSelection', listSelection = new ListSelectionController({
        selection: new ListSelection()
        cursorIndex: undefined
        isValidIndex: isValidIndex
        platform: /Mac/.test(navigator.platform) && 'mac' || 'anything-but-mac'
      }))

      # Update the state's selection when the user clicks around or docs load.
      #
      # When the user clicks, here's what happens:
      #
      # * on click, listSelection changes selectedIndices.
      # * here, we set the state's documentId to the first selected index.
      #   (Because we assume there is only one -- if we want to change this,
      #   we need to make the state have multiple documentIds.)
      # * TODO on state documentId change, we modify listSelection. (We only do
      #   this when changing oneDocumentSelected. Will the rest be needed?)

      setStateSelectionFromListSelection = =>
        collection = @get('documentCollection')
        cursorIndex = listSelection.get('cursorIndex')
        docId = cursorIndex? && collection.at(cursorIndex)?.id || null

        @get('state').set
          documentId: docId
          oneDocumentSelected: cursorIndex?

      setListSelectionFromStateSelection = =>
        # If we're navigating individual documents and we change selection, go
        # to the top of the new document list.
        #
        # The new doclist won't have loaded, so documentId will be null. We
        # catch that by watching for add() on documentCollection.
        if @state.get('oneDocumentSelected')
          listSelection.set
            cursorIndex: 0
            selectedIndices: [0]
        else
          listSelection.onSelectAll()

      @listenTo(listSelection, 'change:cursorIndex', setStateSelectionFromListSelection)
      @listenTo(@state, 'change:oneDocumentSelected', setListSelectionFromStateSelection)
      @on 'change:documentCollection', =>
        setListSelectionFromStateSelection() # may call setStateSelectionFromListSelection
        oldDocCollection = @previous('documentCollection')
        newDocCollection = @get('documentCollection')
        @stopListening(oldDocCollection) if oldDocCollection?
        @listenToOnce(newDocCollection, 'add', setStateSelectionFromListSelection)

    _addTitleView: ->
      view = new DocumentListTitleView
        documentList: @get('documentList')
        cache: @cache

      @on 'change:documentList', =>
        view.setDocumentList(@get('documentList'))

      @listenTo view, 'edit-node', (nodeid) ->
        node = @cache.on_demand_tree.nodes[nodeid]
        log('began editing node', node_to_short_string(node))
        node_form_controller(node, @cache, @state)

      @listenTo view, 'edit-tag', (tagid) ->
        tag = @cache.tag_store.find_by_id(tagid)
        log('clicked edit tag', tag_to_short_string(tag))
        tag_form_controller(tag, @cache, @state)

      @set('titleView', view)
      view.$el.appendTo(@get('listEl'))

    _addListView: ->
      view = new DocumentListView
        collection: @get('documentCollection')
        selection: @get('listSelection')
        tags: @get('tagCollection')
        tagIdToModel: (id) => @tagStoreProxy.map(id)

      @on 'change:documentCollection', (__, documentCollection) =>
        view.setCollection(documentCollection)

      @listenTo view, 'click-document', (model, index, options) =>
        log('clicked document', "#{model.id} index:#{index} meta:#{options.meta} shift: #{options.shift}")
        @get('listSelection').onClick(index, options)

      # Handle loading by calling @get('documentList').slice()
      firstMissingIndex = 0 # one more than the last index we've requested
      pageSize = 20 # number of documents we request at once
      pageSizeBuffer = 5 # how far from the end we begin a request for more

      fetchMissingDocuments = (needed) =>
        while firstMissingIndex < needed + pageSizeBuffer
          @get('documentList')?.slice(firstMissingIndex, firstMissingIndex + pageSize)
          firstMissingIndex += pageSize

      @on 'change:documentList', =>
        firstMissingIndex = 0
        fetchMissingDocuments(1)
      @listenTo(view, 'change:maxViewedIndex', (model, value) => fetchMissingDocuments(value))

      @set('listView', view)
      view.$el.appendTo(@get('listEl'))

    _addCursorView: ->
      view = new DocumentListCursorView
        selection: @get('listSelection')
        documentList: @get('documentListProxy')?.model
        documentDisplayApp: DocumentDisplayApp
        tags: @get('tagCollection')
        tagIdToModel: (id) => @tagStoreProxy.map(id)
        el: @get('cursorEl')

      @on 'change:documentListProxy', (model, documentListProxy) ->
        view.setDocumentList(documentListProxy?.model)

      @set('cursorView', view)

  document_list_controller = (listDiv, cursorDiv, cache, state) ->
    controller = new Controller({
      tagStore: cache.tag_store
      searchResultStore: cache.search_result_store
      documentStore: cache.document_store
      state: state
      cache: cache
      listEl: listDiv
      cursorEl: cursorDiv
    })

    go_up_or_down = (up_or_down, event) ->
      listSelection = controller.get('listSelection')
      options = {
        meta: event.ctrlKey || event.metaKey || false
        shift: event.shiftKey || false
      }
      diff = up_or_down == 'down' && 1 || -1
      new_index = (listSelection.cursorIndex || -1) + diff
      func = up_or_down == 'down' && 'onDown' || 'onUp'

      docid = controller.get('documentCollection').at(new_index)?.id

      log("went #{up_or_down}", "docid:#{docid} index:#{new_index} meta:#{options.meta} shift: #{options.shift}")
      listSelection[func](options)

    go_down = (event) -> go_up_or_down('down', event)
    go_up = (event) -> go_up_or_down('up', event)
    select_all = (event) -> controller.get('listSelection').onSelectAll()

    (->
      view = controller.get('cursorView')
      view.on('next-clicked', -> go_down({}))
      view.on('previous-clicked', -> go_up({}))
      view.on('list-clicked', -> select_all({}))

      # With content <a></a><b></b>, CSS can match "a:hover ~ b", but
      # there's no way to do the reverse "b:hover ~ a".
      $listView = controller.get('listView').$el
      view.$el.on('mouseenter', -> $listView.addClass('hover'))
      view.$el.on('mouseleave', -> $listView.removeClass('hover'))
    )()

    {
      go_up: go_up
      go_down: go_down
      select_all: select_all
    }
