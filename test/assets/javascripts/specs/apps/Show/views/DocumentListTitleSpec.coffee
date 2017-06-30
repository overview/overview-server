define [
  'backbone'
  'apps/Show/views/DocumentListTitle'
  'i18n'
], (Backbone, DocumentListTitle, i18n) ->
  class DocumentListParams extends Backbone.Model
    constructor: (obj) ->
      Object.assign(this, obj)

    sortedByMetadataField: (s) ->
      new DocumentListParams(sortByMetadataField: s)

  class DocumentList extends Backbone.Model
    constructor: (attrs) ->
      super(attrs)
      @params = new DocumentListParams()

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
      expect(@view.$el.text()).to.match(/loading/)

    it 'should show a progress bar when loading', ->
      expect(@view.$('progress')).to.exist
      @state.set(documentList: new DocumentList(length: null))
      @state.get('documentList').set(progress: 0.2)
      expect(@view.$('progress')).to.have.attr('value', '0.2')

    it 'should render loading when a list is loading', ->
      @state.set(documentList: new DocumentList(length: null))
      expect(@view.$el.html()).to.match(/loading/)

    it 'should render a title when a list has a length', ->
      @state.set(documentList: new DocumentList(length: 0))
      expect(@view.$el.find('h3').html()).to.eq('title,0')

    it 'should monitor a loading list and render when it has loaded its length', ->
      list = new DocumentList(length: null)
      @state.set(documentList: list)
      list.set(length: 0)
      expect(@view.$el.find('h3').html()).to.eq('title,0')

    it 'should set number of documents in HTML', ->
      list = new DocumentList(length: null)
      @state.set(documentList: list)
      expect(@view.el.getAttribute('data-n-documents')).to.eq('0')
      list.set(length: 0)
      expect(@view.el.getAttribute('data-n-documents')).to.eq('0')
      list.set(length: 2)
      expect(@view.el.getAttribute('data-n-documents')).to.eq('2')

    it 'should not show a dropdown when there are no metadata fields', ->
      @state.set(documentList: new DocumentList(length: 10))
      expect(@view.$('.sort-by').html()).to.eq('sort_by_sort_by.title')
      expect(@view.$('.dropdown')).not.to.exist

    it 'should show a dropdown when there are metadata fields', ->
      @state.set(documentList: new DocumentList(length: 10))
      @state.documentSet.set('metadataFields', [ 'foo' ])
      expect(@view.$('.dropdown')).to.exist

    it 'should sort by the clicked field', ->
      @state.set(documentList: new DocumentList(length: 10))
      @state.documentSet.set('metadataFields', [ 'foo' ])
      @state.setDocumentListParams = sinon.stub()
      @view.$('a[data-sort-by-metadata-field=foo]').click()
      # The params should change
      expect(@state.setDocumentListParams).to.have.been.calledWith(new DocumentListParams(sortByMetadataField: 'foo'))

    it 'should render the sorted field name', ->
      list = new DocumentList(length: 10)
      list.params.sortByMetadataField = 'foo'
      @state.documentSet.set('metadataFields', [ 'foo' ])
      @state.set(documentList: list)
      expect(@view.$('a[data-toggle=dropdown]').text()).to.eq('foo')
