require [
  'apps/Tree/views/DocumentListCursor',
  'i18n'
], (View, i18n) ->
  describe 'apps/Tree/views/DocumentListCursor', ->
    view = undefined
    selection = undefined
    documentList = undefined

    initAt = (cursorIndex, nDocuments) ->
      selection = new Backbone.Model({ cursorIndex: cursorIndex })
      documentList = nDocuments? && new Backbone.Model({ n: nDocuments }) || nDocuments
      view = new View({ selection: selection, documentList: documentList })

    testClickTriggersEvent = (selector, trigger, shouldBeCalled) ->
      spy = jasmine.createSpy()
      view.on(trigger, spy)
      view.$(selector).click()
      expect(spy.callCount).toEqual(shouldBeCalled && 1 || 0)

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.show.DocumentListCursor.title': 'title,{0},{1}'
        'views.DocumentSet.show.DocumentListCursor.next': 'next'
        'views.DocumentSet.show.DocumentListCursor.previous': 'previous'
        'views.DocumentSet.show.DocumentListCursor.list': 'list'

    describe 'starting with a full list at no index', ->
      beforeEach -> initAt(undefined, 10)

      it 'should show nothing when there is no index', ->
        expect(view.$el.html()).toEqual('')

      it 'should set HTML when the cursorIndex changes', ->
        selection.set({ cursorIndex: 3 })
        expect(view.$el.html()).not.toEqual('')

    it 'should recognize document 0/10 as "1 of 10"', ->
      initAt(0, 10)
      expect(view.$('h4').text()).toEqual('title,1,10')

    it 'should disable "previous" at 0/10', ->
      initAt(0, 10)
      expect(view.$('a.previous').hasClass('disabled')).toBe(true)

    it 'should enable "previous" at 1/10', ->
      initAt(1, 10)
      expect(view.$('a.previous').hasClass('disabled')).toBe(false)

    it 'should disable "next" at 9/10', ->
      initAt(9, 10)
      expect(view.$('a.next').hasClass('disabled')).toBe(true)

    it 'should enable "next" at 8/10', ->
      initAt(8, 10)
      expect(view.$('a.next').hasClass('disabled')).toBe(false)

    it 'should not render at 10/10', ->
      initAt(10, 10)
      expect(view.$el.html()).toEqual('')

    it 'should link to "list"', ->
      initAt(1, 10)
      expect(view.$('a.list').text()).toEqual('list')

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

    it 'should allow setDocumentList', ->
      initAt(5, 10)
      view.setDocumentList(new Backbone.Model({ n: 3 }))
      expect(view.$el.html()).toEqual('')

    it 'should allow setDocumentList(undefined)', ->
      initAt(5, 10)
      view.setDocumentList(undefined)
      expect(view.$el.html()).toEqual('')

    it 'should allow starting with documentList: undefined', ->
      initAt(5, undefined)
      expect(view.$el.html()).toEqual('')
