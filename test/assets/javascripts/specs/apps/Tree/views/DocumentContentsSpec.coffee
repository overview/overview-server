define [
  'apps/Tree/views/DocumentContents'
  'backbone'
], (DocumentContents, Backbone) ->
  class MockSelection
    constructor: (attrs = {}) ->
      for k in [ 'documents', 'nodes', 'searchResults', 'tags' ]
        this[k] = attrs[k] ? []

    documents_from_cache: -> []

  describe 'views/document_contents_view', ->
    describe 'DocumentContents', ->
      cache =
        # valid document IDs: 1 and 2
        document_store:
          documents:
            1: { id: 1, title: "doc 1", documentcloud_id: "1-doc-1" }
            2: { id: 2, title: "doc 2", documentcloud_id: "2-doc-2" }
      state = undefined
      view = undefined
      displayApp = undefined

      beforeEach ->
        state = new Backbone.Model()
        displayApp = new Backbone.View()
        displayApp.setDocument = sinon.spy()
        displayApp.scrollByPages = sinon.spy()

      afterEach ->
        view?.stopListening()
        state = undefined

      describe 'beginning empty', ->
        beforeEach ->
          view = new DocumentContents(cache: cache, state: state, documentDisplayApp: displayApp)

        it 'should not set the document', ->
          expect(displayApp.setDocument).not.to.have.been.called

        it 'should not set the document when oneDocumentSelected is false', ->
          state.set(documentId: 3, oneDocumentSelected: false)
          expect(displayApp.setDocument).not.to.have.been.called

        it 'should not set the document when documentId is null', ->
          state.set(documentId: null, oneDocumentSelected: true)
          expect(displayApp.setDocument).not.to.have.been.called

        it 'should set the document when documentId is non-null and oneDocumentSelected is true', ->
          state.set(documentId: 1, oneDocumentSelected: true)
          expect(displayApp.setDocument).to.have.been.called

        it 'should not set the document when documentId points to a nonexistent document', ->
          state.set(documentId: 99, oneDocumentSelected: true)
          expect(displayApp.setDocument).not.to.have.been.called

        it 'should pass through scroll_by_pages', ->
          view.scroll_by_pages(1)
          expect(displayApp.scrollByPages).to.have.been.calledWith(1)
