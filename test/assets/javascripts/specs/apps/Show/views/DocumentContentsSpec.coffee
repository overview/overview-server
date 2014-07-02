define [
  'apps/Show/views/DocumentContents'
  'backbone'
], (DocumentContents, Backbone) ->
  class MockState extends Backbone.Model
    defaults:
      document: null
      oneDocumentSelected: false

  class MockDocument extends Backbone.Model
    defaults:
      title: 'title'

  describe 'views/document_contents_view', ->
    describe 'DocumentContents', ->
      beforeEach ->
        @state = new Backbone.Model()
        @displayApp =
          setDocument: sinon.spy()
          scrollByPages: sinon.spy()
          el: document.createElement('div')
        @view = new DocumentContents(state: @state, documentDisplayApp: @displayApp)
        @aDocument = new MockDocument(id: 3)

      afterEach ->
        @view.stopListening()

      it 'should not set the document', ->
        expect(@displayApp.setDocument).not.to.have.been.called

      it 'should not set the document when oneDocumentSelected is false', ->
        @state.set(document: @aDocument, oneDocumentSelected: false)
        expect(@displayApp.setDocument).not.to.have.been.calledWith(@aDocument.toJSON())

      it 'should not set the document when documentId is null', ->
        @state.set(document: null, oneDocumentSelected: true)
        expect(@displayApp.setDocument).not.to.have.been.called

      it 'should set the document when documentId is non-null and oneDocumentSelected is true', ->
        @state.set(document: @aDocument, oneDocumentSelected: true)
        expect(@displayApp.setDocument).to.have.been.calledWith(@aDocument.toJSON())

      it 'should pass through scroll_by_pages', ->
        @view.scroll_by_pages(1)
        expect(@displayApp.scrollByPages).to.have.been.calledWith(1)
