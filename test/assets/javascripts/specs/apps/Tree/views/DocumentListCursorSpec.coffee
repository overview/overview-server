define [
  'apps/Tree/views/DocumentListCursor',
  'i18n'
], (View, i18n) ->

  DocumentList = Backbone.Model.extend
    describeSelection: -> [ 'all' ]

  describe 'apps/Tree/views/DocumentListCursor', ->
    view = undefined
    selection = undefined
    documentList = undefined
    displayApp = undefined

    initAt = (cursorIndex, nDocuments) ->
      selection = new Backbone.Model({ cursorIndex: cursorIndex })
      documentList = nDocuments? && new DocumentList({ n: nDocuments }) || undefined
      documentList?.documents = new Backbone.Collection([])

      view = new View({
        selection: selection
        documentList: documentList
        tags: new Backbone.Collection([])
        tagIdToModel: -> undefined
        documentDisplayApp: (options) ->
          @options = options
          @setDocument = sinon.spy()
          displayApp = this
      })

    testClickTriggersEvent = (selector, trigger, shouldBeCalled) ->
      spy = sinon.spy()
      view.on(trigger, spy)
      view.$(selector).click()
      expect(spy.callCount).to.eq(shouldBeCalled && 1 || 0)

    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.DocumentListCursor.position_html': 'position_html,{0},{1}'
        'views.Tree.show.DocumentListCursor.next': 'next'
        'views.Tree.show.DocumentListCursor.previous': 'previous'
        'views.Tree.show.DocumentListCursor.list': 'list'
        'views.Tree.show.DocumentListCursor.selection.all_html': 'selection.all_html'
        'views.Tree.show.DocumentListCursor.selection.node_html': 'selection.node_html,{0}'
        'views.Tree.show.DocumentListCursor.selection.searchResult_html': 'selection.searchResult_html,{0}'
        'views.Tree.show.DocumentListCursor.selection.tag_html': 'selection.tag_html,{0}'
        'views.Tree.show.DocumentListCursor.selection.untagged_html': 'selection.untagged_html,{0}'
        'views.Tree.show.DocumentListCursor.description': 'description,{0}'
        'views.Tree.show.DocumentListCursor.description.empty': 'description.empty'
        'views.Tree.show.helpers.DocumentHelper.title': 'title,{0}'
        'views.Tree.show.helpers.DocumentHelper.title.empty': 'title.empty'

    describe 'starting with a full list at no index', ->
      beforeEach ->
        initAt(undefined, 10)
        documentList.documents.reset([
          new Backbone.Model({ id: 1 })
          new Backbone.Model({ id: 2 })
          new Backbone.Model({ id: 3 })
          new Backbone.Model({ id: 4 })
          new Backbone.Model({ id: 5 })
        ])

      it 'should have className not-showing-document when there is no index', ->
        expect(view.el.className).to.eq('not-showing-document')

      it 'should have className showing-document when the cursorIndex changes', ->
        selection.set({ cursorIndex: 1 })
        expect(view.el.className).to.eq('showing-document')

      it 'should render the selection', ->
        documentList.describeSelection = -> [ 'node', 'foo' ]
        selection.set({ cursorIndex: 1 })
        expect(view.$('.selection').html()).to.eq('selection.node_html,foo')

      it 'should have className showing-document when the document list is populated', ->
        documentList.documents.reset([])
        selection.set({ cursorIndex: 1 })
        documentList.documents.add(new Backbone.Model({ id: 6 }))
        documentList.documents.add(new Backbone.Model({ id: 7 }))
        expect(view.el.className).to.eq('showing-document')

      it 'should call documentDisplayApp.setDocument with a document', ->
        selection.set({ cursorIndex: 2 })
        expect(displayApp.setDocument).to.have.been.calledWith(documentList.documents.at(2).attributes)

      it 'should call documentDisplayApp.setDocument with undefined', ->
        selection.set({ cursorIndex: 2 }) # defined
        selection.set({ cursorIndex: 7 }) # undefined
        expect(displayApp.setDocument).to.have.been.calledWith(undefined)

    it 'should recognize document 0/10 as "1 of 10"', ->
      initAt(0, 10)
      expect(view.$('div.position').text()).to.eq('position_html,1,10')

    it 'should disable "previous" at 0/10', ->
      initAt(0, 10)
      expect(view.$('a.previous').hasClass('disabled')).to.be(true)

    it 'should enable "previous" at 1/10', ->
      initAt(1, 10)
      expect(view.$('a.previous').hasClass('disabled')).to.be(false)

    it 'should disable "next" at 9/10', ->
      initAt(9, 10)
      expect(view.$('a.next').hasClass('disabled')).to.be(true)

    it 'should enable "next" at 8/10', ->
      initAt(8, 10)
      expect(view.$('a.next').hasClass('disabled')).to.be(false)

    it 'should not render at 10/10', ->
      initAt(10, 10)
      expect(view.el.className).to.eq('showing-unloaded-document')

    it 'should link to "list"', ->
      initAt(1, 10)
      expect(view.$('a.list').text()).to.eq('list')

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

    it 'should trigger "list-clicked"', ->
      initAt(1, 10)
      testClickTriggersEvent('a.list', 'list-clicked', true)

    it 'should allow setDocumentList with an unfilled DocumentList', ->
      initAt(5, 10)
      documentList = new DocumentList({ n: 3 })
      documentList.documents = new Backbone.Collection()
      view.setDocumentList(documentList)
      expect(view.el.className).to.eq('showing-unloaded-document')

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
