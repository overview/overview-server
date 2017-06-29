define [
  'backbone'
  'apps/Show/views/DocumentListTitle'
  'i18n'
], (Backbone, DocumentListTitle, i18n) ->
  class DocumentList extends Backbone.Model
    defaults:
      length: null

  class State extends Backbone.Model
    defaults:
      documentList: null

  describe 'apps/Show/views/DocumentListTitleView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentListTitle',
        'title_html': 'title,{0}'
        'loading': 'loading'
        'sort_by.title': 'sort_by.title'
        'sort_by_FIELD': 'sort_by_FIELD'

      @state = new State
      @state.documentSet = new Backbone.Model(metadataFields: [])
      @view = new DocumentListTitle(state: @state)

    afterEach ->
      @view.remove()

    it 'should render loading by default', ->
      expect(@view.$el.html()).to.eq('loading')

    it 'should render loading when a list is loading', ->
      @state.set(documentList: new DocumentList(length: null))
      expect(@view.$el.html()).to.eq('loading')

    it 'should render a title when a list has a length', ->
      @state.set(documentList: new DocumentList(length: 0))
      expect(@view.$el.find('h3').html()).to.eq('title,0')

    it 'should monitor a loading list and render when it has loaded its length', ->
      list = new DocumentList(length: null)
      @state.set(documentList: list)
      list.set(length: 0)
      expect(@view.$el.find('h3').html()).to.eq('title,0')
