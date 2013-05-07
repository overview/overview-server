require [
  'apps/Tree/views/document_contents_view'
  'apps/Tree/models/observable'
], (DocumentContentsView, observable) ->
  class MockState
    observable(this)

    constructor: () ->
      @selection = { nodes: [], tags: [], documents: [], documents_from_cache: () -> [] }

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

        it 'should be empty when there are no documents in the selection', ->
          expect($(view.div).children().length).toEqual(0)

        it 'should build an iframe when there is a selected document', ->
          state.selection.documents = [1]
          state._notify('selection-changed', state.selection)
          expect(view.iframe.getAttribute('src')).toEqual('/document_view/1')

        it 'should build an iframe when there is a selection that resolves to a document', ->
          state.selection.documents_from_cache = () -> [ { id: 1 } ]
          spyOn(state.selection, 'documents_from_cache').andReturn([{ id: 1 }])
          state._notify('selection-changed', state.selection)
          expect(view.iframe.getAttribute('src')).toEqual('/document_view/1')
          expect(state.selection.documents_from_cache).toHaveBeenCalledWith(cache)

      describe 'beginning on a document', ->
        beforeEach ->
          state.selection.documents = [1]
          view = create_view()

        it 'should show the iframe', ->
          expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

        it 'should call the iframe setDocument() on change', ->
          doc = undefined
          view.iframe = {
            contentWindow: {
              setDocument: (x) -> doc = x
            }
          }
          state.selection.documents = [2]
          state._notify('selection-changed', state.selection)
          expect(doc?.id).toEqual(2)

        it 'should not call setDocument() when the selection changes but the selected document does not', ->
          doc = undefined
          view.iframe = {
            contentWindow: {
              setDocument: (x) -> doc = x
            }
          }
          state.selection.nodes = [4]
          state._notify('selection-changed', state.selection)
          expect(doc).toBeUndefined()
