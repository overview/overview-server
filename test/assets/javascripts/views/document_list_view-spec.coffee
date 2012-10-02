observable = require('models/observable').observable # a small unit-testing transgression
DocumentListView = require('views/document_list_view').DocumentListView

class MockDocumentList
  observable(this)

  constructor: (@selection) ->
    @documents = []
    @n = undefined
    @placeholder_documents = []

  get_placeholder_documents: (document_store, on_demand_tree) -> @placeholder_documents

class MockDocumentStore
  observable(this)

  constructor: () ->
    @documents = {}

class MockTagStore
  observable(this)

  constructor: () ->
    @tags = []

  find_tag_by_id: (id) ->
    _.find(@tags, (t) -> t.id == id) || throw "tagNotFound: #{id}"

class MockState
  observable(this)

  constructor: () ->
    @selection = { nodes: [], tags: [], documents: [] }

mock_document_array = (n, first=1) ->
  ({ id: i, title: "doc#{i}", tagids: [1 + (i-1)%2] } for i in [first..(n+first-1)])

describe 'views/document_list_view', ->
  describe 'DocumentListView', ->
    state = undefined
    document_store = undefined
    tag_store = undefined
    document_list = undefined
    div = undefined
    options = undefined

    create_view = () ->
      cache = { document_store: document_store, tag_store: tag_store, on_demand_tree: {} }
      new DocumentListView(div, cache, document_list, state, options)

    beforeEach ->
      options = {}
      state = new MockState()
      document_list = new MockDocumentList(state.selection)
      document_store = new MockDocumentStore()
      tag_store = new MockTagStore()
      tag_store.tags = [
        { id: 1, position: 0, name: 'AA', color: '#123456' },
        { id: 2, position: 1, name: 'BB' },
      ]
      div = $('<div></div>')[0]
      $('body').append(div)
      window.i18n = () -> JSON.stringify(Array.prototype.slice.apply(arguments))

    afterEach ->
      options = {}
      $(div).remove() # Removes all callbacks
      div = undefined
      document_list = undefined
      document_store = undefined
      tag_store = undefined
      delete window.i18n

    it 'should add nothing for an empty DocumentList', ->
      document_list.n = 0
      view = create_view()
      expect($(view.div).children().length).toEqual(0)

    it 'should unobserve when setting a new DocumentList', ->
      view = create_view()
      expect(document_list._observers._.length).toEqual(1) # if this fails, the test is broken
      view.set_document_list(new MockDocumentList(state.selection))
      expect(document_list._observers._.length).toEqual(0)

    it 'should observe the new DocumentList', ->
      view = create_view()
      document_list2 = new MockDocumentList(state.selection)
      view.set_document_list(document_list2)
      expect(document_list2._observers._.length).toEqual(1)

    it 'should ask for placeholder data when a new list is set', ->
      view = create_view()
      document_list2 = new MockDocumentList(state.selection)
      spyOn(document_list2, 'get_placeholder_documents').andCallThrough()
      view.set_document_list(document_list2)
      expect(document_list2.get_placeholder_documents).toHaveBeenCalledWith(view.cache)

    describe 'get_top_need_documents', ->
      it 'should return the most pressing need_documents', ->
        view = create_view()
        view.need_documents = [[0, 10], [20, undefined]]
        expect(view.get_top_need_documents()).toEqual([0, 10])

      it 'should return undefined when there are no need_documents', ->
        view = create_view()
        view.need_documents = []
        expect(view.get_top_need_documents()).toBeUndefined()

    describe 'starting with a complete list', ->
      beforeEach ->
        document_list.documents = mock_document_array(2)
        document_store.documents[d.id] = d for d in document_list.documents
        document_list.n = 2

      it 'should set need_documents to []', ->
        view = create_view()
        expect(view.need_documents).toEqual([])

      it 'should show the documents in a list', ->
        view = create_view()
        $div = $(view.div)
        expect($div.children().length).toBeTruthy()
        expect($div.find('a[href=#document-1]').length).toEqual(1)
        expect($div.find('a[href=#document-2]').length).toEqual(1)

      it 'should add a tag with its specified color to each list item', ->
        view = create_view()
        $tags = $('a:eq(0) span.tags', view.div)
        expect($tags.length).toEqual(1)
        expect($tags.children(':eq(0)').css('background-color')).toEqual('rgb(18, 52, 86)')

      it 'should add a tag with a generated color to each list item', ->
        view = create_view()
        $tags = $('a:eq(1) span.tags', view.div)
        expect($tags.length).toEqual(1)
        expect($tags.children(':eq(0)').css('background-color')).toEqual('rgb(115, 120, 255)')

      it 'should add a title to the tag span', ->
        view = create_view()
        $span = $('a:eq(0) span.tags span:eq(0)', view.div)
        expect($span.attr('title')).toEqual('AA')

      it 'should add a tag to the list item after the document changes', ->
        view = create_view()
        document_list.documents[0].tagids.push(2)
        document_store._notify('document-changed', document_list.documents[0])
        $tags = $('a:eq(0) span.tags', view.div)
        expect($tags.children().length).toEqual(2)

      it 'should re-color tags after the tag list changes', ->
        view = create_view()
        tag_store.tags[0].color = '#234567'
        tag_store._notify('tag-changed', tag_store.tags[0])
        $tag = $('a:eq(0) span.tags span:eq(0)')
        expect($tag.css('background-color')).toEqual('rgb(35, 69, 103)')

      it 'should not have any "last clicked" document', ->
        view = create_view()
        expect(view.last_document_id_clicked()).toBeUndefined()

      it 'should set the proper "last clicked" document', ->
        view = create_view()
        $(view.div).find('a[href=#document-1]').click()
        expect(view.last_document_id_clicked()).toEqual(1)

      it 'should unset the "last clicked" document if we click somewhere else', ->
        view = create_view()
        $a = $(view.div).find('a[href=#document-1]')
        $a.click()
        $a.parent().click()
        expect(view.last_document_id_clicked()).toBeUndefined()

      it 'should notify "document-clicked"', ->
        view = create_view()
        $a = $(view.div).find('a[href=#document-1]')
        called = false
        view.observe('document-clicked', () -> called = true)
        $a.click()
        expect(called).toBeTruthy()

      it 'should add the "selected" class to selected documents', ->
        state.selection.documents = [1]
        view = create_view()
        $div = $(view.div)
        expect($div.find('a[href=#document-1]').hasClass('selected')).toBeTruthy()
        expect($div.find('a[href=#document-2]').hasClass('selected')).toBeFalsy()

      it 'should remove the "selected" class when the selection changes', ->
        state.selection.documents = [1]
        view = create_view()
        state.selection.documents = []
        state._notify('selection-changed', state.selection)
        expect($(view.div).find('a[href=#document-1]').hasClass('selected')).toBeFalsy()

      it 'should add the "selected" class when the selection changes', ->
        state.selection.documents = [1]
        view = create_view()
        state.selection.documents = [1,2]
        state._notify('selection-changed', state.selection)
        expect($(view.div).find('a[href=#document-2]').hasClass('selected')).toBeTruthy()

    describe 'starting with an incomplete list', ->
      beforeEach ->
        document_list.documents = mock_document_array(4)
        document_list.n = 10

      it 'should only show the incomplete list', ->
        view = create_view()
        $div = $(view.div)
        expect($div.find('a[href=#document-4]').length).toEqual(1)
        expect($div.find('a[href=#document-5]').length).toEqual(0)

      it 'should set need_documents', ->
        view = create_view()
        expect(view.need_documents).toEqual([[4, 10]])

      it 'should set need_documents when gaps get in the document list', ->
        view = create_view()
        document_list.documents.push(undefined)
        document_list.documents.push(undefined)
        for document in mock_document_array(2, 7)
          document_list.documents.push(document)
        document_list._notify()
        expect(view.need_documents).toEqual([[4, 6], [8, 10]])

      it 'should show the number of documents', ->
        view = create_view()
        expect($('.num-documents', view.div).text()).toEqual('["views.DocumentSet.show.document_list.num_documents",10]')

      it 'should make need_documents empty when it is', ->
        view = create_view()
        for document in mock_document_array(6, 5)
          document_list.documents.push(document)
        document_list._notify()
        expect(view.need_documents).toEqual([])

      it 'should notify need-documents when it changes', ->
        view = create_view()
        called = false
        view.observe('need-documents', -> called = true)
        document_list.documents.push(mock_document_array(1, 5)[0])
        document_list._notify()
        expect(called).toBeTruthy()

      it 'should not notify need-documents when it does not change', ->
        view = create_view()
        called = false
        view.observe('need-documents', -> called = true)
        document_list._notify()
        expect(called).toBeFalsy()

      it 'should show a placeholder at the end of the list', ->
        view = create_view()
        $a = $(view.div).find('a[href=#loading-document-4]') # indices start at 0, so this is the 5th
        expect($a.length).toEqual(1)
        expect($a.closest('.placeholder').length).toEqual(1)

      it 'should not show two placeholders', ->
        view = create_view()
        $placeholder = $(view.div).find('.placeholder')
        expect($placeholder.length).toEqual(1)

      it 'should update the list when new documents are fetched', ->
        view = create_view()
        for document in mock_document_array(4, 5)
          document_list.documents.push(document)
        document_list._notify()
        $div = $(view.div)
        expect($div.find('a[href=#loading-document-4]').length).toEqual(0)
        expect($div.find('a[href=#loading-document-8]').length).toEqual(1)
        expect($div.find('a[href=#document-6]').length).toEqual(1)

      it 'should update the list in-place', ->
        view = create_view()
        $(view.div).find('a[href=#document-2]').data('foo', 'bar') # this should stay put
        for document in mock_document_array(4, 5)
          document_list.documents.push(document)
        document_list._notify()
        expect($(view.div).find('a[href=#document-2]').data('foo')).toEqual('bar')

      it 'should not put a placeholder when the list becomes complete', ->
        view = create_view()
        for document in mock_document_array(6, 5)
          document_list.documents.push(document)
        document_list._notify()
        $div = $(view.div)
        expect($div.find('a').length).toEqual(10)
        expect($div.find('.placeholder').length).toEqual(0)

    describe 'starting with just placeholder data', ->
      beforeEach ->
        document_list.placeholder_documents = mock_document_array(4)

      it 'should show the placeholder data', ->
        view = create_view()
        $div = $(view.div)
        expect($div.find('.placeholder a').length).toEqual(4)

      it 'should show a palceholder even if placeholder list is empty', ->
        document_list.placeholder_documents = []
        view = create_view()
        expect($(view.div).find('.placeholder').length).toEqual(1)

      it 'should remove placeholders when resolved', ->
        view = create_view()
        for document in mock_document_array(3)
          document_list.documents.push(document)
        document_list.n = 3
        document_list._notify()
        expect($(view.div).find('.placeholder').length).toEqual(0)

      it 'should show a loading message instead of the number of documents', ->
        view = create_view()
        num_documents_text = $('.num-documents', view.div).text()
        expect($('.num-documents', view.div).text()).toEqual('["views.DocumentSet.show.document_list.loading"]')

      it 'should set need_documents', ->
        view = create_view()
        expect(view.need_documents).toEqual([[0, undefined]])

    describe 'considering div and font height', ->
      beforeEach ->
        document_list.documents = mock_document_array(10)
        document_list.n = 1000
        $(div).css({
          height: 100,
          overflow: 'hidden',
        })
        options = {
          buffer_documents: 10,
          ul_style: 'margin:0;padding:0;list-style:none;height:100px;overflow:scroll;',
          li_style: 'display:block;margin:0;padding:0;font-size:10px;height:10px;line-height:10px;overflow:hidden;',
        }

      it 'should limit need_documents to the scroll length + 10 documents', ->
        view = create_view()
        expect(view.need_documents).toEqual([[10, 20]])

      it 'should set no need_documents if we have enough to fill the view', ->
        document_list.documents = mock_document_array(20)
        view = create_view()
        expect(view.need_documents).toEqual([])

      it 'should make need_documents longer after scrolling', ->
        document_list.documents = mock_document_array(15) # so we can scroll down
        view = create_view()
        $ul = $('ul', div)
        $ul[0].scrollTop = 50 # scroll down 5 documents
        $ul.scroll() # trigger listener
        expect(view.need_documents).toEqual([[15, 25]])

      it 'should still start with [0, undefined]', ->
        document_list.documents = []
        document_list.n = undefined
        view = create_view()
        expect(view.need_documents).toEqual([[0, undefined]])

      it 'should trigger need-documents after scrolling', ->
        document_list.documents = mock_document_array(15) # so we can scroll down
        view = create_view()

        called = false
        view.observe('need-documents', -> called = true)

        $ul = $('ul', div)
        $ul[0].scrollTop = 50 # scroll down 5 documents
        $ul.scroll() # trigger listener

        expect(called).toBeTruthy()

      it 'should not trigger need-documents after scrolling if we have enough', ->
        document_list.documents = mock_document_array(21) # so we can scroll down
        view = create_view()

        called = false
        view.observe('need-documents', -> called = view.need_documents)

        $ul = $('ul', div)
        $ul[0].scrollTop = 10 # that's 1 doc. Another 10 doc are visible, 10 doc buffered = 21
        $ul.scroll() # trigger listener

        expect(called).toBeFalsy()

      it 'should not create fractional needs', ->
        view = create_view()
        $ul = $('ul', div)
        $ul[0].scrollTop = 5 # half a line
        $ul.scroll() # trigger listener
        expect(view.need_documents).toEqual([[10, 21]])
