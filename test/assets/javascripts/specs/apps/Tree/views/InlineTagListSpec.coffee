define [
  'apps/Tree/views/InlineTagList'
  'backbone'
  'i18n'
], (InlineTagListView, Backbone, i18n) ->
  class MockTag extends Backbone.Model

  class MockTagCollection extends Backbone.Collection
    model: MockTag

  describe 'views/InlineTagList', ->
    beforeEach ->
      i18n.reset_messages({
        'views.InlineTagList.create': 'create'
        'views.InlineTagList.add': 'add'
        'views.InlineTagList.remove': 'remove'
        'views.InlineTagList.show_untagged': 'show_untagged'
        'views.InlineTagList.organize': 'organize'
      })

    describe 'InlineTagList', ->
      state = undefined
      collection = undefined
      view = undefined

      beforeEach ->
        state = new Backbone.Model
        collection = new MockTagCollection
        placeholderTagIdToModel = -> { cid: 'c0' } # we'll stub it out later if needed
        view = new InlineTagListView
          collection: collection
          state: state
          tagIdToModel: placeholderTagIdToModel

      afterEach ->
        view.remove()

      it 'should start without tags', ->
        expect(view.$('li').length).to.eq(3)

      it 'should show a form', ->
        expect(view.$('form').length).to.eq(1)

      describe 'with tags', ->
        tag1 = undefined
        tag2 = undefined

        beforeEach ->
          tag1 = new MockTag(position: 0, id: 1, name: 'AA', color: '#123456')
          tag2 = new MockTag(position: 1, id: 2, name: 'BB')
          collection.reset([ tag1, tag2 ])

        it 'should show tags', ->
          expect(view.$('li:eq(0) .tag-name').text()).to.eq('AA')
          expect(view.$('li:eq(1) .tag-name').text()).to.eq('BB')

        it 'should remove tags', ->
          collection.remove(tag1)
          expect(view.$('li:eq(0) .tag-name').text()).to.eq('BB')

        it 'should add tags in the expected position', ->
          collection.add(new MockTag(position: 1, id: 3, name: 'CC', color: '#cccccc'), at: 1)
          expect(view.$('li:eq(1) .tag-name').text()).to.eq('CC')

        it 'should not re-render the whole thing when a tag is added', ->
          # If the whole thing did re-render, then the form.submit event would
          # bubble up to the document but the form would no longer be a child
          # of div#tag-list. That means the tracking code wouldn't be able to
          # tell it's a "created tag" event.
          #
          # See https://www.pivotaltracker.com/story/show/67400952
          el = view.$('form')[0]
          collection.add(new MockTag(position: 1, id: 3, name: 'CC', color: '#cccccc'), at: 1)
          while el? && el != view.el
            el = el.parentNode

          expect(el).to.eq(view.el)

        it 'should notify :create-submitted', ->
          spy = sinon.spy()
          view.on('create-submitted', spy)
          view.$('input[type=text]').val('foo')
          view.$('form').submit()
          expect(spy).to.have.been.calledWith('foo')

        it 'should trim the string in :create-submitted', ->
          spy = sinon.spy()
          view.on('create-submitted', spy)
          view.$('input[type=text]').val('   foo ')
          view.$('form').submit()
          expect(spy).to.have.been.calledWith('foo')

        it 'should not notify :create-submitted when input is empty', ->
          # https://github.com/overview/overview-server/issues/567
          spy = sinon.spy()
          view.on('create-submitted', spy)
          view.$('form').submit()
          expect(spy).not.to.have.been.called

        it 'should focus the input when trying to add just spaces', ->
          spy = sinon.spy()
          $('body').append(view.$el) # to make focusing work
          $input = view.$('input[type=text]')
          view.$('form').submit()
          expect($input[0]).to.eq($input[0].ownerDocument.activeElement)

        it 'should reset the form after :create-submitted', ->
          view.$('input[type=text]').val('   foo ')
          view.$('form').submit()
          expect(view.$('input[type=text]').val()).to.eq('')

        it 'should notify :add-clicked', ->
          spy = sinon.spy()
          view.on('add-clicked', spy)
          view.$('.tag-add:eq(0)').click()
          expect(spy).to.have.been.called
          expect(spy.lastCall.args[0].id).to.eq(1)

        it 'should notify :remove-clicked', ->
          spy = sinon.spy()
          view.on('remove-clicked', spy)
          view.$('.tag-remove:eq(0)').click()
          expect(spy).to.have.been.called
          expect(spy.lastCall.args[0].id).to.eq(1)

        it 'should notify :add-clicked when trying to create an existing tag', ->
          spy = sinon.spy()
          view.on('add-clicked', spy)
          view.$('input[type=text]').val('AA')
          view.$('form').submit()
          expect(spy).to.have.been.called
          expect(spy.lastCall.args[0].id).to.eq(1)

        it 'should notify :name-clicked when clicking a tag', ->
          spy = sinon.spy()
          view.on('name-clicked', spy)
          view.$('.tag-name:eq(0)').click()
          expect(spy).to.have.been.called
          expect(spy.lastCall.args[0].id).to.eq(1)

        it 'should notify :organize-clicked when clicking the organize link', ->
          spy = sinon.spy()
          view.on('organize-clicked', spy)
          view.$('.organize').click()
          expect(spy).to.have.been.called

        it 'should set "selected" on selected tags', ->
          view.tagIdToModel = -> tag1
          state.set('documentListParams', { type: 'tag', tagId: 1 })
          expect(view.$('li:eq(0)').hasClass('selected')).to.be(true)
          expect(view.$('li:eq(1)').hasClass('selected')).to.be(false)

        it 'should set "selected" on untagged', ->
          state.set('documentListParams', { type: 'untagged' })
          expect(view.$('li.untagged').hasClass('selected')).to.be(true)

        it 'should use the tag color when given', ->
          expect(view.$('li:eq(0)').css('background-color')).to.eq('rgb(18, 52, 86)')

        it 'should change a tag color', ->
          collection.at(0).set({ color: '#654321' })
          expect(view.$('li:eq(0)').css('background-color')).to.eq('rgb(101, 67, 33)')

        it 'should change a tag name', ->
          collection.at(0).set({ name: 'AA2' })
          expect(view.$('li:eq(0) .tag-name').text()).to.eq('AA2')

        it 'should notify :untagged-click when clicking the untagged button', ->
          spy = sinon.spy()
          view.on('untagged-clicked', spy)
          view.$('.untagged').click()
          expect(spy).to.have.been.called
