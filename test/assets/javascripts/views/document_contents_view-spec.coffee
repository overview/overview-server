DocumentContentsView = require('views/document_contents_view').DocumentContentsView

observable = require('models/observable').observable # a small unit-testing transgression

class MockState
  observable(this)

  constructor: () ->
    @selection = { nodes: [], tags: [], documents: [] }

class MockRouter
  route_to_path: (key, id) ->
    @last_path = "/#{key}/#{id}"


describe 'views/document_contents_view', ->
  describe 'DocumentContentsView', ->
    state = undefined
    router = undefined
    document = { id: 1, title: "doc 1" }
    document2 = { id: 2, title: "doc 2" }
    div = undefined
    view = undefined

    create_view = () -> new DocumentContentsView(div, state, router)
    
    beforeEach ->
      state = new MockState()
      router = new MockRouter()
      div = $('<div></div>')[0]
      $('body').append(div)
      view = undefined

    afterEach ->
      state = undefined
      router = undefined
      $(div).remove() # Removes all callbacks
      div = undefined
      view = undefined

    describe 'beginning empty', ->
      beforeEach ->
        view = create_view()

      it 'should be empty when there is no selected document', ->
        expect($(view.div).children().length).toEqual(0)

      it 'should build an iframe when there is a selected document', ->
        state.selection.documents = [1]
        state._notify('selection-changed', state.selection)
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

    describe 'beginning on a document', ->
      beforeEach ->
        state.selection.documents = [1]
        view = create_view()

      it 'should show the iframe', ->
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

      it 'should change the URL when the document changes', ->
        state.selection.documents = [2]
        state._notify('selection-changed', state.selection)
        $iframe = $(view.div).find('iframe')
        expect($iframe.attr('src')).toEqual('/document_view/2')

      it 'should not change the URL when the selection changes but the selected document does not', ->
        $iframe = $(view.div).find('iframe')
        $iframe.attr('src', '/somewhere-else')
        state.selection.nodes = [1]
        state._notify('selection-changed', state.selection)
        expect($iframe.attr('src')).toEqual('/somewhere-else')
