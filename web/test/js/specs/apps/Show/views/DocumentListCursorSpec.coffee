define [
  'apps/Show/views/DocumentListCursor',
  'i18n'
], (View, i18n) ->
  class Document extends Backbone.Model
    defaults:
      title: 'title'
      description: 'description'
      tags: []

    hasTag: (tag) -> tag.id in @get('tags')

  class Documents extends Backbone.Collection
    model: Document

  class Views extends Backbone.Collection

  class Tag extends Backbone.Model
    defaults:
      name: 'tag'
      color: '#abcdef'

    getClass: -> 'tag'
    getStyle: -> ''

  class Tags extends Backbone.Collection
    model: Tag

  class DocumentList extends Backbone.Model
    defaults:
      length: null

    initialize: ->
      @params = {}
      @documents = new Documents

  class Selection extends Backbone.Model

  describe 'apps/Show/views/DocumentListCursor', ->
    view = undefined
    tags = undefined
    views = undefined
    selection = undefined
    documentList = undefined
    displayApp = undefined
    documentMetadataApp = undefined

    initAt = (cursorIndex, nDocuments) ->
      selection = new Selection(cursorIndex: cursorIndex)
      documentList = nDocuments? && new DocumentList(length: nDocuments) || undefined
      documentList?.documents = new Backbone.Collection([])

      if nDocuments
        for id in [ 0 ... nDocuments ]
          documentList.documents.add({ id: id })

      tags = new Tags([])
      views = new Views([])

      documentMetadataApp =
        el: $('<div class="document-metadata-app"></div>')[0]
        setDocument: sinon.spy()

      view = new View
        selection: selection
        documentList: documentList
        tags: tags
        views: views
        documentDisplayApp: (options) ->
          @options = options
          @setDocument = sinon.spy()
          @setSearch = sinon.spy()
          displayApp = this
        documentMetadataApp: documentMetadataApp

    testClickTriggersEvent = (selector, trigger, shouldBeCalled) ->
      spy = sinon.spy()
      view.on(trigger, spy)
      view.$(selector).click()
      expect(spy.callCount).to.eq(shouldBeCalled && 1 || 0)

    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.DocumentListCursor.position_html': 'position_html,{0},{1}'
        'views.Tree.show.DocumentListCursor.backToList': 'backToList'
        'views.Tree.show.DocumentListCursor.next': 'next'
        'views.Tree.show.DocumentListCursor.previous': 'previous'
        'views.Tree.show.DocumentListCursor.description': 'description,{0}'
        'views.Tree.show.DocumentListCursor.description.empty': 'description.empty'
        'views.Tree.show.DocumentListCursor.title.edit': 'title.edit'
        'views.Tree.show.DocumentListCursor.title.save': 'title.save'
        'views.Tree.show.DocumentListCursor.title.reset': 'title.reset'
        'views.Tree.show.helpers.DocumentHelper.title': 'title,{0}'
        'views.Tree.show.helpers.DocumentHelper.title.empty': 'title.empty'
        # ick ick ick...
        'views.DocumentSet.show.DocumentDisplayPreferences.dropdown.title': 'dropdown.title'
        'views.DocumentSet.show.DocumentDisplayPreferences.text.false': 'prefs.text.false'
        'views.DocumentSet.show.DocumentDisplayPreferences.text.true': 'prefs.text.true'
        'views.DocumentSet.show.DocumentDisplayPreferences.sidebar': 'prefs.sidebar'
        'views.DocumentSet.show.DocumentDisplayPreferences.wrap': 'prefs.wrap'
        'views.DocumentSet.show.DocumentDisplayPreferences.openInNewTab': 'prefs.openInNewTab'

    describe 'starting with a full list at no index', ->
      beforeEach ->
        initAt(undefined, 10)
        documentList.documents.reset([
          new Document({ id: 1 })
          new Document({ id: 2 })
          new Document({ id: 3 })
          new Document({ id: 4 })
          new Document({ id: 5 })
        ])

      it 'should have className not-showing-document when there is no index', ->
        expect(view.el.className).to.eq('not-showing-document')

      it 'should have className showing-document when the cursorIndex changes', ->
        selection.set({ cursorIndex: 1 })
        expect(view.el.className).to.eq('showing-document')

      it 'should have className showing-document when the document list is populated', ->
        documentList.documents.reset([])
        selection.set({ cursorIndex: 1 })
        documentList.documents.add(new Backbone.Model({ id: 6 }))
        documentList.documents.add(new Backbone.Model({ id: 7 }))
        expect(view.el.className).to.eq('showing-document')

      it 'should call documentDisplayApp.setDocument with a document', ->
        selection.set({ cursorIndex: 2 })
        expect(displayApp.setDocument).to.have.been.calledWith(documentList.documents.at(2))

      it 'should call documentDisplayApp.setDocument with null', ->
        selection.set({ cursorIndex: 2 }) # defined
        selection.set({ cursorIndex: 7 }) # undefined
        expect(displayApp.setDocument).to.have.been.calledWith(null)

      it 'should add the documentMetadataApp el', ->
        expect(view.el.querySelector('.document-metadata-app')).not.to.be.null

    it 'should recognize document 0/10 as "1 of 10"', ->
      initAt(0, 10)
      expect(view.$('div.document-nav h4').text()).to.eq('position_html,1,10')

    it 'should disable "previous" at 0/10', ->
      initAt(0, 10)
      expect(view.$('a.previous').hasClass('disabled')).to.be.true

    it 'should enable "previous" at 1/10', ->
      initAt(1, 10)
      expect(view.$('a.previous').hasClass('disabled')).to.be.false

    it 'should disable "next" at 9/10', ->
      initAt(9, 10)
      expect(view.$('a.next').hasClass('disabled')).to.be.true

    it 'should enable "next" at 8/10', ->
      initAt(8, 10)
      expect(view.$('a.next').hasClass('disabled')).to.be.false

    it 'should not render at 10/10', ->
      initAt(10, 10)
      expect(view.el.className).to.eq('showing-unloaded-document')

    it 'should trigger "next-clicked"', ->
      initAt(1, 10)
      testClickTriggersEvent('a.next', 'next-clicked', true)

    it 'should not trigger "next-clicked" when disabled', ->
      initAt(9, 10)
      testClickTriggersEvent('a.next', 'next-clicked', false)

    it 'should trigger "previous-clicked"', ->
      initAt(1, 10)
      testClickTriggersEvent('a.previous', 'previous-clicked', true)

    it 'should not trigger "previous-clicked" when disabled', ->
      initAt(0, 10)
      testClickTriggersEvent('a.previous', 'previous-clicked', false)

    it 'should allow setDocumentList with an unfilled DocumentList', ->
      initAt(5, 10)
      documentList = new DocumentList({ n: 3 })
      documentList.documents = new Backbone.Collection()
      view.setDocumentList(documentList)
      expect(view.el.className).to.eq('showing-unloaded-document')

    it 'should call documentDisplayApp.setSearch with a search when changing params', ->
      initAt(5, 10)
      documentList = new DocumentList({ n: 3 })
      documentList.documents = new Backbone.Collection()
      documentList.params = { q: 'foo' }
      view.setDocumentList(documentList)
      expect(displayApp.setSearch).to.have.been.calledWith('foo')

    it 'should allow setDocumentList with a filled DocumentList', ->
      initAt(1, 10)
      documentList = new DocumentList({ n: 10 })
      documentList.documents = new Backbone.Collection([
        new Backbone.Model({})
        new Backbone.Model({})
      ])
      view.setDocumentList(documentList)
      expect(view.el.className).to.eq('showing-document')

    it 'should allow setDocumentList(undefined)', ->
      initAt(5, 10)
      view.setDocumentList(undefined)
      expect(view.el.className).to.eq('showing-unloaded-document')

    it 'should allow starting with documentList undefined', ->
      initAt(5, undefined)
      expect(view.el.className).to.eq('showing-unloaded-document')

    it 'should render a DocumentDetailLinksView', ->
      initAt(1, 3)
      expect(view.detailLinksView).to.be.defined
      expect(view.detailLinksView.views).to.eq(views)
      # and test that the HTML appears
      view.detailLinksView.el.innerHTML = 'FOOBAR'
      expect(view.el.innerHTML).to.contain('FOOBAR')
      # and test that DocumentListCursor.coffee set the documentId on detailLinksView
      expect(view.detailLinksView.documentId).to.eq(1)

    it 'should continue listening for events on detailLinksView after navigating', ->
      initAt(1, 3)
      spy = sinon.spy()
      view.detailLinksView.events = { 'click': spy }
      selection.set({ cursorIndex: 2 })
      view.detailLinksView.el.click()
      expect(spy).to.have.been.called
