define [
  'jquery',
  'backbone',
  '../models/document_list'
  '../models/list_selection'
  '../collections/DocumentListProxy'
  '../collections/TagStoreProxy'
  '../views/node_form_view'
  '../views/DocumentList'
  '../views/DocumentListTitle'
  '../views/DocumentListCursor'
  './ListSelectionController'
  './node_form_controller'
  './tag_form_controller'
  './logger'
], ($, Backbone, DocumentList, ListSelection, DocumentListProxy, TagStoreProxy, NodeFormView, DocumentListView, DocumentListTitleView, DocumentListCursorView, ListSelectionController, node_form_controller, tag_form_controller, Logger) ->
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

  Controller = Backbone.Model.extend
    # Only set on initialize (properties may change):
    # * documentViewEl (an HTMLElement)
    # * tagStore (a TagStore)
    # * documentStore (a DocumentStore)
    # * cache (a Cache)
    # * state (a State)
    #
    # Read-only, may change or be set to something new:
    # * selection (a Selection)
    # * selectionModuloDocuments (a Selection with no documents)
    # * documentList (a DocumentList, may be undefined)
    # * documentListProxy (a DocumentListProxy, may be undefined)
    # * documentCollection (a Backbone.Collection, always defined)
    #
    # Read-only, only properties may change:
    # * listSelection (a ListSelectionController)
    # * tagCollection (a Backbone.Collection)
    # * titleView
    # * listView
    # * cursorView
    defaults:
      documentStore: undefined
      tagStore: undefined
      state: undefined
      cache: undefined

      documentList: undefined

      selection: undefined
      selectionModuloDocuments: undefined
      listSelection: undefined
      tagCollection: undefined
      documentCollection: undefined

    initialize: (attrs, options) ->
      throw 'Must specify documentViewEl, an HTMLElement' if !attrs.documentViewEl
      throw 'Must specify tagStore, a TagStore' if !attrs.tagStore
      throw 'Must specify documentStore, a DocumentStore' if !attrs.documentStore
      throw 'Must specify state, a State' if !attrs.state
      throw 'Must specify cache, a Cache' if !attrs.cache

      @_addSelection()
      @_addSelectionModuloDocuments()
      @_addDocumentList()
      @_addListSelection()
      @_addTagCollection()
      @_addDocumentCollection()
      @_addTitleView()
      @_addListView()
      @_addCursorView()
      @_addDocumentView()

    _addSelection: ->
      state = @get('state')

      updateSelection = => @set('selection', @get('state').selection)
      updateSelection()
      state.observe('selection-changed', updateSelection)

    _addSelectionModuloDocuments: ->
      updateFromSelection = =>
        @set('selectionModuloDocuments', @get('selection').pick('nodes', 'tags', 'searchResults'))
      updateFromSelection()
      @on('change:selection', updateFromSelection)

    _addDocumentList: ->
      refresh = =>
        selection = @get('selectionModuloDocuments')
        documentList = if selection?.nodes?.length || selection?.tags?.length || selection?.searchResults?.length
          new DocumentList(@get('cache'), selection)
        else
          undefined
        @set('documentList', documentList)

      @on('change:selectionModuloDocuments', refresh)
      refresh()

    _addTagCollection: ->
      @tagStoreProxy = new TagStoreProxy(@get('tagStore'))
      @set('tagCollection', @tagStoreProxy.collection)

    _addDocumentCollection: ->
      documentStore = @get('documentStore')
      tagStore = @get('tagStore')

      refresh = =>
        @get('documentListProxy')?.destroy()
        documentList = @get('documentList')
        if documentList
          documentListProxy = new DocumentListProxy(documentList, documentStore, tagStore)
          @set('documentListProxy', documentListProxy)
          @set('documentCollection', documentListProxy.model.documents)
        else
          @set('documentListProxy', undefined)
          @set('documentCollection', new Backbone.Collection([]))

      @on('change:documentList', refresh)
      refresh()

    _addListSelection: ->
      isValidIndex = (i) => @get('documentCollection')?.at(i)?.id?
      @set('listSelection', new ListSelectionController({
        selection: new ListSelection()
        cursorIndex: undefined
        isValidIndex: isValidIndex
        platform: /Mac/.test(navigator.platform) && 'mac' || 'anything-but-mac'
      }))

      resetListSelection = => @get('listSelection').onSelectAll()
      @on('change:documentCollection', resetListSelection)

      # Update the state's selection when the user clicks around.
      #
      # You'd think there would be an infinite loop,
      # listSelection.change:selectedIndices to this.change:selection to
      # this.change:selectionModuloDocuments to this.change:documentList to
      # this.change:documentCollection to listSelection.onSelectAll(). But
      # that won't happen, because we only change the "documents" part of the
      # selection, so this will never call change:selectionModuloDocuments.
      #
      # This is still ugly: when the user clicks, we modify both the
      # ListSelection and the State. Ideally we'd let the State changes
      # propagate to the ListSelection, but we can't because only the
      # ListSelection has a cursorIndex.
      @get('listSelection').on 'change:selectedIndices', (model, selectedIndices) =>
        collection = @get('documentCollection')
        docids = []
        for index in selectedIndices || []
          docid = collection.at(index)?.id
          docids.push(docid) if docid?

        selection = @get('selection').replace({ documents: docids })
        @get('state').set('selection', selection)

    _addTitleView: ->
      cache = @get('cache')
      state = @get('state')
      view = new DocumentListTitleView({
        documentList: @get('documentList')
        cache: cache
      })

      @on 'change:documentList', =>
        view.setDocumentList(@get('documentList'))

      view.on 'edit-node', (nodeid) ->
        node = cache.on_demand_tree.nodes[nodeid]
        log('began editing node', node_to_short_string(node))
        node_form_controller(node, cache, state)

      view.on 'edit-tag', (tagid) ->
        tag = cache.tag_store.find_by_id(tagid)
        log('clicked edit tag', tag_to_short_string(tag))
        tag_form_controller(tag, cache, state)

      @set('titleView', view)

    _addListView: ->
      view = new DocumentListView({
        collection: @get('documentCollection')
        selection: @get('listSelection')
        tags: @get('tagCollection')
        tagIdToModel: (id) => @tagStoreProxy.map(id)
      })

      @on 'change:documentCollection', =>
        view.setCollection(@get('documentCollection'))

      view.on 'click-document', (model, index, options) =>
        log('clicked document', "#{model.id} index:#{index} meta:#{options.meta} shift: #{options.shift}")
        @get('listSelection').onClick(index, options)

      # Handle loading by calling @get('documentList').slice()
      firstMissingIndex = 0 # one more than the last index we've requested
      pageSize = 20 # number of documents we request at once
      pageSizeBuffer = 5 # how far from the end we begin a request for more

      @on 'change:documentList', =>
        @get('documentList')?.slice(0, pageSize)
        firstMissingIndex = pageSize

      view.on 'change:maxViewedIndex', (model, value) =>
        while firstMissingIndex < value + pageSizeBuffer
          @get('documentList')?.slice(firstMissingIndex, firstMissingIndex + pageSize)
          firstMissingIndex += pageSize

      @set('listView', view)

    _addCursorView: ->
      view = new DocumentListCursorView({
        selection: @get('listSelection')
        documentList: @get('documentListProxy')?.model
      })

      @on 'change:documentListProxy', (model, documentListProxy) ->
        view.setDocumentList(documentListProxy?.model)

      @set('cursorView', view)

    _addDocumentView: ->
      listView = @get('listView')
      $documentViewEl = $(@get('documentViewEl'))
      $cursorViewEl = @get('cursorView').$el
      $titleViewEl = @get('titleView').$el
      listSelection = @get('listSelection')
      collection = @get('documentCollection')

      # window.setInterval() value that tries to set listView.$el.scrollTop()
      scrollTopInterval = undefined

      refresh = ->
        if scrollTopInterval?
          window.clearInterval(scrollTopInterval)
          scrollTopInterval = undefined

        selectedIndices = listSelection.get('selectedIndices')
        if selectedIndices.length == 1
          # Scroll selection to the right position
          $li = listView.$("li.document:eq(#{selectedIndices[0]})")
          $ul = $li.parent()
          scrollTop = $li.offset().top - $ul.offset().top
          # We'd like to set scrollTop directly, but that won't work when the
          # list is too short to have a scrollbar (but long enough that we need
          # to change the scrollTop -- 5 items, for instance).
          tries = 20
          scrollTopInterval = doUntilWithSetInterval(
            -> listView.$el.scrollTop(scrollTop),
            -> (listView.$el.scrollTop() == scrollTop) || (tries -= 1) <= 0,
            50
          )
          #
          # We'll animate, which will set it lots of times. Ensure the
          # duration of the animation is >= the duration of CSS transitions.
          #listView.$el.scrollTop(scrollTop)
          listView.$el.stop(true, true)
          listView.$el.animate({ scrollTop: scrollTop })

          # Bring in #document
          $container = listView.$el.parent()
          console.log($container)
          documentHeight = Math.floor($container.height() - listView.$el.position().top - $li.outerHeight())
          $documentViewEl.height(documentHeight)
          $cursorViewEl.css({ top: "#{$ul.parent().position().top + $li.outerHeight()}px" })
          listView.$el.css({ bottom: "#{documentHeight}px" })
        else
          $cursorViewEl.css({ top: '100%' })
          listView.$el.css({ bottom: '' })

      listSelection.on('change:selectedIndices', refresh)

      @listenTo(collection, 'change', refresh)
      @on 'change:documentCollection', (model, newCollection) =>
        @stopListening(collection)
        collection = newCollection
        @listenTo(collection, 'change', refresh)

      @on 'change:selection', (model, selection) ->
      $(window).on('resize', refresh)

  document_list_controller = (div, documentDiv, cache, state) ->
    controller = new Controller({
      tagStore: cache.tag_store
      documentStore: cache.document_store
      documentViewEl: documentDiv
      state: state
      cache: cache
    })

    $(div).append(controller.get('titleView').el)
    $(div).append(controller.get('listView').el)
    $(div).append(controller.get('cursorView').el)

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
