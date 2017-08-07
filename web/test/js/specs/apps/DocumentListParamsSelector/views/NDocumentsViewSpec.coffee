define [
  'backbone'
  'apps/DocumentListParamsSelector/views/NDocumentsView'
  'i18n'
], (Backbone, NDocumentsView, i18n) ->
  describe 'apps/DocumentListParamsSelector/views/NDocumentsView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentListParamsSelector.NDocumentsView',
        loading: 'loading'
        nDocuments: 'nDocuments,{0}'

      @state = new Backbone.Model(documentList: null)
      @subject = new NDocumentsView(model: @state)

    it 'should show a loading message when there is no DocumentList (edge case during init)', ->
      expect(@subject.$el).to.have.text('loading')

    it 'should show a loading message on a new DocumentList', ->
      @state.set(documentList: new Backbone.Model(length: null))
      expect(@subject.$el).to.have.text('loading')

    it 'should show an nDocuments message when the DocumentList is loaded', ->
      @state.set(documentList: new Backbone.Model(length: 2))
      expect(@subject.$el).to.have.text('nDocuments,2')

    it 'should show an nDocuments message when the DocumentList switches from loading to loaded', ->
      documentList = new Backbone.Model(length: null)
      @state.set(documentList: documentList)
      documentList.set(length: 3)
      expect(@subject.$el).to.have.text('nDocuments,3')

    it 'should listen to a second DocumentList', ->
      @state.set(documentList: new Backbone.Model(length: null))
      documentList2 = new Backbone.Model(length: null)
      @state.set(documentList: documentList2)
      documentList2.set(length: 4)
      expect(@subject.$el).to.have.text('nDocuments,4')

    it 'should stop listening to an obsolete DocumentList', ->
      documentList = new Backbone.Model(length: null)
      @state.set(documentList: documentList)
      @state.set(documentList: new Backbone.Model(length: null))
      documentList.set(length: 5)
      expect(@subject.$el).to.have.text('loading')
