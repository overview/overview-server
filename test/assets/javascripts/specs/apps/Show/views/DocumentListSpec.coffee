define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Show/views/DocumentList'
  'i18n'
], ($, _, Backbone, DocumentListView, i18n) ->
  HEIGHT = 100
  LI_HEIGHT = 10

  class Tag extends Backbone.Model
    defaults:
      name: 'tag'
      color: '#abcdef'

    getClass: -> 'tag'
    getStyle: -> ''

  class Document extends Backbone.Model
    hasTag: (tag) -> tag.id of (@attributes.tagIds || {})

  class TagCollection extends Backbone.Collection
    model: Tag

  class DocumentCollection extends Backbone.Collection
    model: Document

  class DocumentList extends Backbone.Model
    statusCode: null
    error: null
    loading: false

    initialize: (attrs, options) -> @documents = options.documents
    untag: ->

  makeDummyDocument = -> new Document
  makeDocument = (id) ->
    ids = {}
    ids[id] = null
    new Document
      id: id
      title: "Title #{id}"
      description: "Description #{id}"
      tagIds: ids

  makeTag = (id) ->
    new Tag
      id: id,
      name: "Tag #{id}"
      color: "#abcdef"

  describe 'apps/Show/views/DocumentList', ->
    selection = undefined
    list = undefined
    documents = undefined
    documentList = undefined
    tags = undefined
    view = undefined
    el = undefined

    makeDocumentsAndTags = (ids) ->
      d = []
      t = []

      for id in ids
        d.push(makeDocument(id))
        t.push(makeTag(id))

      [ d, t ]

    makeCollections = (ids) ->
      [ d, t ] = makeDocumentsAndTags(ids)

      selection = new Backbone.Model({
        cursorIndex: undefined
        selectedIndices: []
      })
      documents = new DocumentCollection(d)
      tags = new TagCollection(t)
      documentList = new DocumentList({}, documents: documents)
      view = new DocumentListView
        el: document.getElementById('views-DocumentListSpec')
        attributes:
          style: 'position:absolute;overflow:auto;' # position for $.fn.position(); overflow for $.fn.scrollTop()
        liAttributes: """style="height:#{LI_HEIGHT}px;display:block;margin:0;padding:0;overflow:hidden;" """
        ulAttributes: """style="height:100%;display:block;margin:0;padding:0;list-style:none;" """
        selection: selection
        model: documentList
        tags: tags

    beforeEach ->
      $div = $("<div id=\"views-DocumentListSpec\" style=\"overflow:hidden;height:#{HEIGHT}px\"></div>")
      $div.appendTo('body')
      i18n.reset_messages
        'views.Tree.show.DocumentList.description': 'description,{0}'
        'views.Tree.show.DocumentList.description.empty': 'description.empty'
        'views.Tree.show.DocumentList.loading': 'loading'
        'views.Tree.show.DocumentList.terms.label': 'terms.label'
        'views.Tree.show.helpers.DocumentHelper.title': 'title,{0}'
        'views.Tree.show.helpers.DocumentHelper.title.empty': 'title.empty'

    afterEach ->
      $('#views-DocumentListSpec').remove()
      view?.remove()
      view?.off()

    describe 'with an empty list', ->
      beforeEach ->
        makeCollections([])

      it 'should render nothing', ->
        expect(view.$('li')).not.to.exist

      it 'should add a new document', ->
        documents.add(makeDummyDocument())
        expect(view.$('li')).to.exist

      it 'should add an error message', ->
        documentList.set(statusCode: 400, error: 'error message')
        expect(view.$el.html()).to.contain('error message')

    describe 'with a complete DocumentCollection', ->
      beforeEach ->
        makeCollections([0, 1, 2])

      it 'should render a list of documents', ->
        expect(view.$('ul.documents').length).to.eq(1)

      it 'should add an item', ->
        documents.add(makeDummyDocument())
        expect(view.$('ul.documents>li').length).to.eq(4)
        expect(view.$('ul.documents>li:last').attr('data-cid')).to.eq(documents.last().cid)

      it 'should remove an item', ->
        documents.pop()
        expect(view.$('ul.documents>li').length).to.eq(2)

      it 'should render selection', ->
        selection.set('selectedIndices', [0, 2])
        $lis = view.$('ul.documents>li')
        expect($lis.eq(0).hasClass('selected')).to.be.true
        expect($lis.eq(1).hasClass('selected')).to.be.false
        expect($lis.eq(2).hasClass('selected')).to.be.true

      it 'should render document-selected class if one document is selected', ->
        selection.set('selectedIndices', [0])
        expect(view.$el.hasClass('document-selected')).to.be.true

      it 'should not render document-selected class if no document is selected', ->
        selection.set('selectedIndices', [])
        expect(view.$el.hasClass('document-selected')).to.be.false

      it 'should not render document-selected class if multiple documents are selected', ->
        selection.set('selectedIndices', [0, 2])
        expect(view.$el.hasClass('document-selected')).to.be.false

      it 'should render the cursor', ->
        selection.set('cursorIndex', 1)
        expect(view.$('ul.documents>li:eq(1)').hasClass('cursor')).to.be.true

      it 'should render a document title', ->
        expect(view.$('li.document:eq(0) h3').text()).to.eq('title,Title 0')

      it 'should render a document description', ->
        expect(view.$('li.document:eq(0) .description').text()).to.eq('description,Description 0')

      it 'should remember an empty title', ->
        documents.at(0).set('title', '')
        expect(view.$('li.document:eq(0) h3').text()).to.eq('title.empty')

      it 'should remember an empty description', ->
        documents.at(0).set('description', '')
        expect(view.$('li.document:eq(0) .description').text()).to.eq('description.empty')

      it 'should render document tags', ->
        $tagEl = view.$('ul.documents>li:eq(0) ul.tags>li:eq(0)')
        expect($tagEl.find('.name').text()).to.eq('Tag 0')

      it 'should update tags as they change', ->
        tags.get(0).set({ color: '#111111', name: 'Tag 111111' })
        $tagEl = view.$('ul.documents>li:eq(0) div.tag:eq(0)')
        expect($tagEl.find('.name').text()).to.eq('Tag 111111')

      it 'should re-render tags when the collection is tagged', ->
        # Hacky here. How do we detect that tags have been re-rendered?
        tags.get(0).attributes.name = 'Tag 111111' # make a change that isn't rendered
        documents.trigger('tag', tags.get(0)) # we're testing that this triggers a render of tags
        $tagEl = view.$('ul.documents>li:eq(0) div.tag:eq(0)')
        expect($tagEl.find('.name').text()).to.eq('Tag 111111')

      it 'should sort tags in documents', ->
        documents.at(0).set(tagIds: { 2: null, 1: null, 0: null })
        $tags = view.$('ul.documents>li:eq(0) li.tag')
        expect($tags.eq(0).attr('data-cid')).to.eq(tags.get(0).cid)
        expect($tags.eq(1).attr('data-cid')).to.eq(tags.get(1).cid)
        expect($tags.eq(2).attr('data-cid')).to.eq(tags.get(2).cid)

      it 'should update documents as they change', ->
        documents.get(0).set(title: 'new title', tagIds: { 1: null })
        $documentEl = view.$('ul.documents>li:eq(0)')
        expect($documentEl.find('h3').text()).to.eq('title,new title')
        expect($documentEl.find('.tag .name').text()).to.eq('Tag 1')

      it 'should keep the cursor class on a document as it changes', ->
        selection.set('cursorIndex', 0)
        documents.get(0).set({ title: 'new title' })
        expect(view.$('ul.documents>li:eq(0)').hasClass('cursor')).to.be.true

      it 'should keep the selected class on a document as it changes', ->
        selection.set('selectedIndices', [0])
        documents.get(0).set({ title: 'new title' })
        expect(view.$('ul.documents>li:eq(0)').hasClass('selected')).to.be.true

      it 'should fire click', ->
        callback = sinon.spy()
        view.on('click-document', callback)
        view.$('ul.documents>li:eq(1)').click()
        expect(callback).to.have.been.called
        expect(callback.lastCall.args[0]).to.eq(documents.at(1))
        expect(callback.lastCall.args[1]).to.eq(1)
        expect(callback.lastCall.args[2]).to.deep.eq({ meta: false, shift: false })

      it 'should render a new list on setModel()', ->
        view.setModel(new DocumentList({}, documents: new DocumentCollection([])))
        expect(view.$('li')).not.to.exist

      it 'should listen for added items after setModel()', ->
        documents = new DocumentCollection([])
        view.setModel(new DocumentList({}, documents: documents))
        documents.add(makeDummyDocument())
        expect(view.$el.html()).not.to.eq('')

      it 'should not listen on the old collection after setModel()', ->
        view.setModel(new DocumentList({}, documents: new DocumentCollection([])))
        documents.add(makeDummyDocument())
        expect(view.$('li')).not.to.exist

      it 'should have a loading indicator', ->
        expect(view.$('.loading')).to.contain('loading')

      it 'should have class=loading while loading', ->
        documentList.set(loading: true)
        expect(view.$el).to.have.class('loading')

      it 'should not have class=loading while not loading', ->
        documentList.set(loading: false)
        expect(view.$el).not.to.have.class('loading')

      it 'should have class=loading after setModel(loading: true)', ->
        view.setModel(new DocumentList({ loading: true }, documents: new DocumentCollection([])))
        expect(view.$el).to.have.class('loading')

      it 'should not have class=loading after setModel(loading: false)', ->
        view.setModel(new DocumentList({ loading: false }, documents: new DocumentCollection([])))
        expect(view.$el).not.to.have.class('loading')

    describe 'with a long DocumentCollection', ->
      beforeEach ->
        makeCollections(_.range(0, 50))

      it 'should return the max viewed index', ->
        expect(view.maxViewedIndex).to.eq(9) # that is, 10 are viewed

      it 'should trigger the max viewed index', ->
        spy = sinon.spy()
        view.on('change:maxViewedIndex', spy)
        view.$el.scrollTop(105) # viewing [10,20] (base 0)
        view.$el.trigger('scroll')
        expect(spy).to.have.been.called
        expect(spy.firstCall.args[1]).to.eq(20)

      it 'should adjust scroll to fit the cursorIndex', ->
        selection.set('cursorIndex', 20) # 21st; top: 200px bottom: 210px
        scrollTop = parseFloat(view.$el.scrollTop())
        expect(scrollTop).to.be.greaterThan(109.9999)
        expect(scrollTop).to.be.lessThan(200.0000001)
