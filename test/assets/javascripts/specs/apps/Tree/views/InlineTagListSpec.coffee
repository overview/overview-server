define [
  'apps/Tree/models/observable'
  'apps/Tree/views/InlineTagList'
], (observable, InlineTagListView) ->
  class MockState
    observable(this)

    constructor: () ->
      @selection = { nodes: [], tags: [], documents: [], searchResults: [] }

  describe 'views/InlineTagList', ->
    describe 'InlineTagList', ->
      state = undefined
      collection = undefined
      view = undefined

      beforeEach ->
        state = new MockState()
        collection = new Backbone.Collection
        placeholderTagIdToModel = -> { cid: 'c0' } # we'll stub it out later if needed
        view = new InlineTagListView({
          collection: collection
          state: state
          tagIdToModel: placeholderTagIdToModel
        })

      afterEach ->
        view.remove()

      it 'should start without tags', ->
        expect(view.$('li').length).toEqual(2)

      it 'should show a form', ->
        expect(view.$('form').length).toEqual(1)

      describe 'with tags', ->
        tag1 = undefined
        tag2 = undefined

        beforeEach ->
          tag1 = { position: 0, id: 1, name: 'AA', color: '#123456', doclist: { n: 10, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] } }
          tag2 = { position: 1, id: 2, name: 'BB', doclist: { n: 8, docids: [ 2, 4, 6, 8, 10, 12, 14, 16 ] } }
          collection.reset([ tag1, tag2 ])

        it 'should show tags', ->
          expect(view.$('li:eq(0) .tag-name').text()).toEqual('AA')
          expect(view.$('li:eq(1) .tag-name').text()).toEqual('BB')

        it 'should remove tags', ->
          collection.remove(tag1)
          expect(view.$('li:eq(0) .tag-name').text()).toEqual('BB')

        it 'should notify :create-submitted', ->
          spy = jasmine.createSpy()
          view.on('create-submitted', spy)
          view.$('input[type=text]').val('foo')
          view.$('form').submit()
          expect(spy).toHaveBeenCalledWith('foo')

        it 'should trim the string in :create-submitted', ->
          spy = jasmine.createSpy()
          view.on('create-submitted', spy)
          view.$('input[type=text]').val('   foo ')
          view.$('form').submit()
          expect(spy).toHaveBeenCalledWith('foo')

        it 'should not notify :create-submitted when input is empty', ->
          # https://github.com/overview/overview-server/issues/567
          spy = jasmine.createSpy()
          view.on('create-submitted', spy)
          view.$('form').submit()
          expect(spy).not.toHaveBeenCalled()

        it 'should reset the form after :create-submitted', ->
          view.$('input[type=text]').val('   foo ')
          view.$('form').submit()
          expect(view.$('input[type=text]').val()).toEqual('')

        it 'should notify :add-clicked', ->
          spy = jasmine.createSpy()
          view.on('add-clicked', spy)
          view.$('.tag-add:eq(0)').click()
          expect(spy).toHaveBeenCalled()
          expect(spy.mostRecentCall.args[0].id).toEqual(1)

        it 'should notify :remove-clicked', ->
          spy = jasmine.createSpy()
          view.on('remove-clicked', spy)
          view.$('.tag-remove:eq(0)').click()
          expect(spy).toHaveBeenCalled()
          expect(spy.mostRecentCall.args[0].id).toEqual(1)

        it 'should notify :add-clicked when trying to create an existing tag', ->
          spy = jasmine.createSpy()
          view.on('add-clicked', spy)
          view.$('input[type=text]').val('AA')
          view.$('form').submit()
          expect(spy).toHaveBeenCalled()
          expect(spy.mostRecentCall.args[0].id).toEqual(1)

        it 'should notify :name-clicked when clicking a tag', ->
          spy = jasmine.createSpy()
          view.on('name-clicked', spy)
          view.$('.tag-name:eq(0)').click()
          expect(spy).toHaveBeenCalled()
          expect(spy.mostRecentCall.args[0].id).toEqual(1)

        it 'should notify :organize-clicked when clicking the organize link', ->
          spy = jasmine.createSpy()
          view.on('organize-clicked', spy)
          view.$('.organize').click()
          expect(spy).toHaveBeenCalled()

        it 'should set "selected" on selected tags', ->
          state.selection.tags = [1]
          view.tagIdToModel = -> collection.at(0)
          state._notify('selection-changed', state.selection)
          expect(view.$('li:eq(0)').hasClass('selected')).toBe(true)
          expect(view.$('li:eq(1)').hasClass('selected')).toBe(false)

        it 'should use the tag color when given', ->
          expect(view.$('li:eq(0)').css('background-color')).toEqual('rgb(18, 52, 86)')

        it 'should change a tag color', ->
          collection.at(0).set({ color: '#654321' })
          expect(view.$('li:eq(0)').css('background-color')).toEqual('rgb(101, 67, 33)')

        it 'should change a tag name', ->
          collection.at(0).set({ name: 'AA2' })
          expect(view.$('li:eq(0) .tag-name').text()).toEqual('AA2')
