define [
  'apps/Tree/views/DocumentContents'
  'backbone'
], (DocumentContents, Backbone) ->
  class MockSelection
    constructor: (attrs = {}) ->
      for k in [ 'documents', 'nodes', 'searchResults', 'tags' ]
        this[k] = attrs[k] ? []

    documents_from_cache: -> []

  MockState = Backbone.Model.extend
    defaults:
      selection: new MockSelection()

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

  xdescribe 'views/document_contents_view', ->
    describe 'DocumentContents', ->
      cache = undefined
      state = undefined
      view = undefined

      beforeEach ->
        cache = new MockCache()
        state = new MockState()

      afterEach ->
        view?.stopListening()
        cache = undefined
        state = undefined

      describe 'beginning empty', ->
        beforeEach ->
          view = new DocumentContents({ cache: cache, state: state })

        it 'should be empty when there are no documents in the selection', ->
          expect(view.$el.html()).toEqual('')

        it 'should build an iframe when there is a selected document', ->
          state.set('selection', new MockSelection({ documents: [1] }))
          expect(view.$('iframe').attr('src')).toEqual('/document_view/1')

        it 'should build an iframe when there is a selection that resolves to a document', ->
          newSelection = new MockSelection()
          newSelection.spyOn('documents_from_cache').andReturn([{ id: 1 }])
          state.set('selection', newSelection)
          expect(view.$('iframe').attr('src')).toEqual('/document_view/1')
          expect(newSelection.documents_from_cache).toHaveBeenCalledWith(cache)

      describe 'beginning on a document', ->
        beforeEach ->
          state.set('selection', new MockSelection({ documents: [ 1 ] }))
          view = new DocumentContents({ cache: cache, state: state })

        it 'should show the iframe', ->
          expect(view.$('iframe[src="/document_view/1"]').length).toEqual(1)

        it 'should call the iframe setDocument() on change', ->
          doc = undefined
          view.iframe = {
            contentWindow: {
              setDocument: (x) -> doc = x
            }
          }
          state.set('selection', new MockSelection({ documents: [ 2 ] }))
          expect(doc?.id).toEqual(2)

        it 'should not call setDocument() when the selection changes but the selected document does not', ->
          doc = undefined
          view.iframe = {
            contentWindow: {
              setDocument: (x) -> doc = x
            }
          }
          state.set('selection', new MockSelection({ nodes: [4] }))
          expect(doc).toBeUndefined()
