observable = require('models/observable').observable # a small unit-testing transgression
DocumentListView = require('views/document_list_view').DocumentListView

class MockSelection
  observable(this)

  constructor: (properties={}) ->
    @nodes = (properties.nodes || {})
    @tags = (properties.tags || {})
    @documents = (properties.documents || {})

class MockDocumentList
  observable(this)

  constructor: (@selection) ->
    @documents = []
    @n = undefined
    @placeholder_documents = []

  get_placeholder_documents: () -> @placeholder_documents

mock_document_array = (n, first=1) ->
  ({ id: i, title: "doc#{i}" } for i in [first..(n+first-1)])

describe 'views/document_list_view', ->
  describe 'DocumentListView', ->
    selection = undefined
    document_list = undefined
    div = undefined
    options = undefined

    create_view = () -> new DocumentListView(div, document_list, selection, options)

    beforeEach ->
      options = {}
      selection = new MockSelection()
      document_list = new MockDocumentList(selection)
      div = $('<div></div>')[0]
      $('body').append(div)

    afterEach ->
      options = {}
      $(div).remove() # Removes all callbacks
      div = undefined
      document_list = undefined

    it 'should add nothing for an empty DocumentList', ->
      document_list.n = 0
      view = create_view()
      expect($(view.div).children().length).toEqual(0)

    it 'should unobserve when setting a new DocumentList', ->
      view = create_view()
      expect(document_list._observers._.length).toEqual(1) # if this fails, the test is broken
      view.set_document_list(new MockDocumentList(selection))
      expect(document_list._observers._.length).toEqual(0)

    it 'should observe the new DocumentList', ->
      view = create_view()
      document_list2 = new MockDocumentList(selection)
      view.set_document_list(document_list2)
      expect(document_list2._observers._.length).toEqual(1)

    it 'should ask for placeholder data when a new list is set', ->
      view = create_view()
      document_list2 = new MockDocumentList(selection)
      spyOn(document_list2, 'get_placeholder_documents').andCallThrough()
      view.set_document_list(document_list2)
      expect(document_list2.get_placeholder_documents).toHaveBeenCalled()

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
        selection.documents = [ document_list.documents[0] ]
        view = create_view()
        $div = $(view.div)
        expect($div.find('a[href=#document-1]').hasClass('selected')).toBeTruthy()
        expect($div.find('a[href=#document-2]').hasClass('selected')).toBeFalsy()

      it 'should remove the "selected" class when the selection changes', ->
        selection.documents = [ document_list.documents[0] ]
        view = create_view()
        selection.documents = []
        selection._notify()
        expect($(view.div).find('a[href=#document-1]').hasClass('selected')).toBeFalsy()

      it 'should add the "selected" class when the selection changes', ->
        selection.documents = [ document_list.documents[0] ]
        view = create_view()
        selection.documents = [ document_list.documents[0], document_list.documents[1] ]
        selection._notify()
        expect($(view.div).find('a[href=#document-2]').hasClass('selected')).toBeTruthy()

      it 'should work with selected document IDs or document objects', ->
        selection.documents = [ 1 ]
        view = create_view()
        expect($(view.div).find('a[href=#document-1]').hasClass('selected')).toBeTruthy()

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

      it 'should set need_documents', ->
        view = create_view()
        expect(view.need_documents).toEqual([[0, undefined]])
