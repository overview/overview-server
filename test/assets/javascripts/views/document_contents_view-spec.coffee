DocumentContentsView = require('views/document_contents_view').DocumentContentsView

observable = require('models/observable').observable # a small unit-testing transgression

class MockState
  observable(this)

  constructor: () ->
    @selection = { nodes: [], tags: [], documents: [] }

class MockCache
  constructor: () ->
    @document_store = { documents: {
      '1': { id: 1, title: "doc 1", documentcloud_id: "1-doc-1" }
      '2': { id: 2, title: "doc 2", documentcloud_id: "2-doc-2" }
    }}
    @server = {
      router: {
        route_to_path: (key, id) -> "/#{key}/#{id}"
      }
    }


describe 'views/document_contents_view', ->
  describe 'DocumentContentsView', ->
    cache = undefined
    state = undefined
    div = undefined
    view = undefined

    create_view = () -> new DocumentContentsView(div, cache, state)
    
    beforeEach ->
      div = $('<div></div>')[0]
      $('body').append(div)
      cache = new MockCache()
      state = new MockState()

    afterEach ->
      $(div).remove() # Removes all callbacks
      div = undefined
      cache = undefined
      state = undefined
      view = undefined

    describe 'beginning empty', ->
      beforeEach ->
        view = create_view()

      it 'should be empty when there is no selected document', ->
        expect($(view.div).children().length).toEqual(0)

      it 'should build an iframe when there is a selected document', ->
        state.selection.documents = [1]
        state._notify('selection-changed', state.selection)
        expect(view.iframe.getAttribute('src')).toEqual('/document_view/1')

    describe 'beginning on a document', ->
      beforeEach ->
        state.selection.documents = [1]
        view = create_view()

      it 'should show the iframe', ->
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

      it 'should call the iframe load_documentcloud_document() on change', ->
        id = undefined
        view.iframe = {
          contentWindow: {
            load_documentcloud_document: (x) -> id = x
          }
        }
        state.selection.documents = [2]
        state._notify('selection-changed', state.selection)
        expect(id).toEqual('2-doc-2')

      it 'should call load_documentcloud_document() when the selection changes but the selected document does not', ->
        id = undefined
        view.iframe = {
          contentWindow: {
            load_documentcloud_document: (x) -> id = x
          }
        }
        state.selection.nodes = [1]
        state._notify('selection-changed', state.selection)
        expect(id).toBeUndefined()
