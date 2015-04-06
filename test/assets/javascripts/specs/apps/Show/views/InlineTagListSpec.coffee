define [
  'apps/Show/views/InlineTagList'
  'backbone'
  'i18n'
], (InlineTagListView, Backbone, i18n) ->
  class MockTag extends Backbone.Model

  class MockTagCollection extends Backbone.Collection
    model: MockTag

  describe 'views/InlineTagList', ->
    beforeEach ->
      i18n.reset_messages
        'views.InlineTagList.show_untagged': 'show_untagged'
        'views.InlineTagList.organize': 'organize'

      @state = new Backbone.Model
      @collection = new MockTagCollection
      @view = new InlineTagListView
        collection: @collection
        state: @state

    afterEach ->
      @view.remove()

    it 'should start without tags', ->
      expect(@view.$('li').length).to.eq(2)

    describe 'with tags', ->
      beforeEach ->
        @tag1 = new MockTag(id: 1, name: 'AA', color: '#123456')
        @tag2 = new MockTag(id: 2, name: 'BB')
        @collection.reset([ @tag1, @tag2 ])

      it 'should show tags', ->
        expect(@view.$('li:eq(0) .tag-name').text()).to.eq('AA')
        expect(@view.$('li:eq(1) .tag-name').text()).to.eq('BB')

      it 'should remove tags', ->
        @collection.remove(@tag1)
        expect(@view.$('li:eq(0) .tag-name').text()).to.eq('BB')

      it 'should add tags in the expected position', ->
        @collection.add(new MockTag(id: 3, name: 'CC', color: '#cccccc'), at: 1)
        expect(@view.$('li:eq(1) .tag-name').text()).to.eq('CC')

      it 'should handle sort', ->
        @collection.comparator = (a, b) -> b.attributes.id - a.attributes.id
        @collection.sort()
        expect(@view.$('li:eq(0) .tag-name').text()).to.eq('BB')

      it 'should notify :name-clicked when clicking a tag', ->
        spy = sinon.spy()
        @view.on('name-clicked', spy)
        @view.$('.tag-name:eq(0)').click()
        expect(spy).to.have.been.called
        expect(spy.lastCall.args[0].id).to.eq(1)

      it 'should notify :organize-clicked when clicking the organize link', ->
        spy = sinon.spy()
        @view.on('organize-clicked', spy)
        @view.$('.organize').click()
        expect(spy).to.have.been.called

      it 'should set "selected" on selected tags', ->
        @state.set(documentListParams: { params: { tags: [ 1 ] }})
        expect(@view.$('li:eq(0)').hasClass('selected')).to.be.true
        expect(@view.$('li:eq(1)').hasClass('selected')).to.be.false

      it 'should set "selected" on untagged', ->
        @state.set(documentListParams: { params: { tagged: false }})
        expect(@view.$('li.untagged').hasClass('selected')).to.be.true

      it 'should use the tag color when given', ->
        expect(@view.$('li:eq(0)').css('background-color')).to.eq('rgb(18, 52, 86)')

      it 'should change a tag color', ->
        @tag1.set(color: '#654321')
        expect(@view.$('li:eq(0)').css('background-color')).to.eq('rgb(101, 67, 33)')

      it 'should change a tag name', ->
        @tag1.set(name: 'AA2')
        expect(@view.$('li:eq(0) .tag-name').text()).to.eq('AA2')

      it 'should notify :untagged-click when clicking the untagged button', ->
        spy = sinon.spy()
        @view.on('untagged-clicked', spy)
        @view.$('.untagged').click()
        expect(spy).to.have.been.called
