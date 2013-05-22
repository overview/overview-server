require [
  'jquery'
  'underscore'
  'backbone'
  'apps/Tree/views/DocumentList'
  'i18n'
  'tinycolor'
], ($, _, Backbone, DocumentList, i18n, tinycolor) ->
  HEIGHT = 100
  LI_HEIGHT = 10

  Tag = Backbone.Model

  Document = Backbone.Model

  TagCollection = Backbone.Collection.extend
    model: Tag

  DocumentCollection = Backbone.Collection.extend
    model: Document

  makeDummyDocument = () -> new Document({})
  makeDocument = (id) ->
    new Document({
      id: id
      title: "Title #{id}"
      description: "Description #{id}"
      tagids: [ id ]
    })

  makeTag = (id) ->
    new Tag({
      id: id,
      name: "Tag #{id}"
      color: "#abcdef"
    })

  describe 'apps/Tree/views/DocumentList', ->
    selection = undefined
    documents = undefined
    tags = undefined
    view = undefined
    el = undefined
    tagIdToModel = (id) -> tags.get(id)

    makeDocumentsAndTags = (ids, addDummy) ->
      d = []
      t = []

      lastId = -1

      if ids.length
        lastId = ids[ids.length - 1]
        for id in [ 0 .. lastId ]
          if _.indexOf(ids, id) != -1
            d.push(makeDocument(id))
          else
            d.push(makeDummyDocument(id))
          t.push(makeTag(id))

      if addDummy
        d.push(makeDummyDocument(lastId + 1))

      [ d, t ]

    makeCollections = (ids, addDummy) ->
      [ d, t ] = makeDocumentsAndTags(ids, addDummy)

      selection = new Backbone.Model({
        cursorIndex: undefined
        selectedIndices: []
      })
      documents = new DocumentCollection(d)
      tags = new TagCollection(t)
      view = new DocumentList({
        el: document.getElementById('views-DocumentListSpec')
        liAttributes: """style="height:#{LI_HEIGHT}px;display:block;margin:0;padding:0;overflow:hidden;" """
        ulAttributes: """style="height:100%;display:block;margin:0;padding:0;list-style:none;" """
        selection: selection
        collection: documents
        tags: tags
        tagIdToModel: tagIdToModel
      })

    beforeEach ->
      $div = $("<div id=\"views-DocumentListSpec\" style=\"overflow:hidden;height:#{HEIGHT}px\"></div>")
      $div.appendTo('body')
      i18n.reset_messages({
        'views.DocumentSet.show.DocumentList.title': 'title,{0}'
        'views.DocumentSet.show.DocumentList.title.empty': 'title.empty'
        'views.DocumentSet.show.DocumentList.description': 'description,{0}'
        'views.DocumentSet.show.DocumentList.description.empty': 'description.empty'
        'views.DocumentSet.show.DocumentList.placeholder': 'placeholder'
        'views.DocumentSet.show.DocumentList.loading': 'loading'
        'views.DocumentSet.show.DocumentList.terms.label': 'terms.label'
        'views.DocumentSet.show.DocumentList.tag.remove': 'tag.remove'
        'views.DocumentSet.show.DocumentList.tag.remove.title': 'tag.remove'
      })

    afterEach ->
      $('#views-DocumentListSpec').remove()
      view?.remove()
      view?.off()

    describe 'with an empty list', ->
      beforeEach ->
        makeCollections([])

      it 'should render nothing', ->
        expect(view.$el.html()).toEqual('')

      it 'should add a new document', ->
        documents.add(makeDummyDocument())
        expect(view.$el.html()).not.toEqual('')

    describe 'with a complete DocumentCollection', ->
      beforeEach ->
        makeCollections([0, 1, 2])

      it 'should render a list of documents', ->
        expect(view.$('ul.documents').length).toEqual(1)

      it 'should add an item', ->
        documents.add(makeDummyDocument())
        expect(view.$('ul.documents>li').length).toEqual(4)
        expect(view.$('ul.documents>li:last').attr('data-cid')).toEqual(documents.last().cid)

      it 'should insert an item', ->
        documents.add(makeDummyDocument(), { at: 1 })
        expect(view.$('ul.documents>li').length).toEqual(4)
        expect(view.$('ul.documents>li:eq(1)').attr('data-cid')).toEqual(documents.at(1).cid)

      # We never remove items ... yet ...

      it 'should render selection', ->
        selection.set('selectedIndices', [0, 2])
        $lis = view.$('ul.documents>li')
        expect($lis.eq(0).hasClass('selected')).toBe(true)
        expect($lis.eq(1).hasClass('selected')).toBe(false)
        expect($lis.eq(2).hasClass('selected')).toBe(true)

      it 'should render document-selected class if one document is selected', ->
        selection.set('selectedIndices', [0])
        expect(view.$el.hasClass('document-selected')).toBe(true)

      it 'should not render document-selected class if no document is selected', ->
        selection.set('selectedIndices', [])
        expect(view.$el.hasClass('document-selected')).toBe(false)

      it 'should not render document-selected class if multiple documents are selected', ->
        selection.set('selectedIndices', [0, 2])
        expect(view.$el.hasClass('document-selected')).toBe(false)

      it 'should render the cursor', ->
        selection.set('cursorIndex', 1)
        expect(view.$('ul.documents>li:eq(1)').hasClass('cursor')).toBe(true)

      it 'should render a document title', ->
        expect(view.$('li.document:eq(0) h3').text()).toEqual('title,Title 0')

      it 'should render a document description', ->
        expect(view.$('li.document:eq(0) .description').text()).toEqual('description,Description 0')

      it 'should remember an empty title', ->
        documents.at(0).set('title', '')
        expect(view.$('li.document:eq(0) h3').text()).toEqual('title.empty')

      it 'should remember an empty description', ->
        documents.at(0).set('description', '')
        expect(view.$('li.document:eq(0) .description').text()).toEqual('description.empty')

      it 'should render document tags', ->
        $tagEl = view.$('ul.documents>li:eq(0) ul.tags>li:eq(0)')
        expect($tagEl.find('.name').text()).toEqual('Tag 0')
        expect(tinycolor($tagEl.find('.tag').css('background-color')).toHex()).toEqual('abcdef')

      it 'should update tags as they change', ->
        tags.get(0).set({ color: '#111111', name: 'Tag 111111' })
        $tagEl = view.$('ul.documents>li:eq(0) div.tag:eq(0)')
        expect($tagEl.find('.name').text()).toEqual('Tag 111111')
        expect(tinycolor($tagEl.css('background-color')).toHex()).toEqual('111111')

      it 'should sort tags in documents', ->
        tags.comparator = (a, b) -> b.id - a.id
        tags.sort()
        documents.at(0).set({ tagids: [ 0, 1, 2 ] })
        $tags = view.$('ul.documents>li:eq(0) li.tag')
        expect($tags.eq(0).attr('data-cid')).toEqual(tags.get(2).cid)
        expect($tags.eq(1).attr('data-cid')).toEqual(tags.get(1).cid)
        expect($tags.eq(2).attr('data-cid')).toEqual(tags.get(0).cid)

      it 'should update documents as they change', ->
        documents.get(0).set({ title: 'new title', tagids: [1] })
        $documentEl = view.$('ul.documents>li:eq(0)')
        expect($documentEl.find('h3').text()).toEqual('title,new title')
        expect($documentEl.find('.tag .name').text()).toEqual('Tag 1')

      it 'should fire remove-tag', ->
        args = undefined
        view.on('remove-tag', -> args = _.toArray(arguments))
        view.$('ul.documents>li:eq(0) .tag:eq(0) .remove').click()
        expect(_.pluck(args, 'cid')).toEqual(_.pluck([documents.get(0), tags.get(0)], 'cid'))

      it 'should fire click', ->
        callback = jasmine.createSpy()
        view.on('click-document', callback)
        view.$('ul.documents>li:eq(1)').click()
        expect(callback).toHaveBeenCalled()
        expect(callback.mostRecentCall.args[0]).toBe(documents.at(1))
        expect(callback.mostRecentCall.args[1]).toBe(1)
        expect(callback.mostRecentCall.args[2]).toEqual({ meta: false, shift: false })

      it 'should render a new collection on setCollection()', ->
        view.setCollection(new DocumentCollection([]))
        expect(view.$el.html()).toEqual('')

      it 'should listen for added items after setCollection()', ->
        documents = new DocumentCollection([])
        view.setCollection(documents)
        documents.add(makeDummyDocument())
        expect(view.$el.html()).not.toEqual('')

      it 'should not listen on the old collection after setCollection()', ->
        view.setCollection(new DocumentCollection([]))
        documents.add(makeDummyDocument())
        expect(view.$el.html()).toEqual('')

    describe 'with a DocumentCollection that has a final dummy', ->
      beforeEach ->
        makeCollections([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], true)

      it 'should show a loading indicator', ->
        expect(view.$('ul.documents>li:eq(10) h3').text()).toEqual('loading')

    describe 'with a DocumentCollection that has a middle dummy', ->
      beforeEach ->
        makeCollections([0, 1, 2, 3, 5, 6, 7])

      it 'should show a placeholder', ->
        expect(view.$('ul.documents>li:eq(4) h3').text()).toEqual('placeholder')

    describe 'with a long DocumentCollection', ->
      beforeEach ->
        makeCollections(_.range(0, 100))

      it 'should return the max viewed index', ->
        expect(view.maxViewedIndex).toEqual(9) # that is, 10 are viewed

      it 'should trigger the max viewed index', ->
        callback = jasmine.createSpy()
        view.on('change:maxViewedIndex', callback)
        view.$el.scrollTop(101)
        view.$el.trigger('scroll')
        expect(callback).toHaveBeenCalledWith(view, 20)

      it 'should adjust scroll to fit the cursorIndex', ->
        selection.set('cursorIndex', 20) # 21st; top: 200px bottom: 210px
        scrollTop = parseFloat(view.$el.scrollTop())
        expect(scrollTop).toBeGreaterThan(109.9999)
        expect(scrollTop).toBeLessThan(200.0000001)
