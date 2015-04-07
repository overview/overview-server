define [
  'apps/Show/views/TagSelect'
  'backbone'
  'i18n'
], (TagSelectView, Backbone, i18n) ->
  class MockTag extends Backbone.Model
    defaults:
      name: 'foo'

  class MockTagCollection extends Backbone.Collection
    model: MockTag

  class MockState extends Backbone.Model
    defaults:
      documentListParams: null

    initialize: ->
      @_reset =
        byTag: sinon.spy()
        byUntagged: sinon.spy()
        all: sinon.spy()

    resetDocumentListParams: -> @_reset

  describe 'apps/Show/views/TagSelect', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.TagSelect',
        all: 'all'
        untagged: 'untagged'
        organize: 'organize'
        'group.all': 'group.all'

      @state = new MockState
      @collection = new MockTagCollection([ { id: 1, name: 'foo' }, { id: 2, name: 'bar' } ])
      @view = new TagSelectView(collection: @collection, state: @state)
      @$ = @view.$.bind(@view)
      @optionTexts = => @view.$('option').toArray().map((o) -> o.text)

    afterEach ->
      @view?.remove()

    it 'should show tags, sorted', ->
      expect(@optionTexts().slice(2)).to.deep.eq([ 'bar', 'foo' ])

    it 'should add a tag', ->
      @collection.add(name: 'baz')
      expect(@optionTexts()).to.contain('baz')

    it 'should remove a tag', ->
      @collection.remove(@collection.at(0))
      expect(@optionTexts()).not.to.contain('foo')

    it 'should handle reset', ->
      @collection.reset([ { name: 'foo2' }, { name: 'bar2' }])

    it 'should select all on click', ->
      @state.set(documentListParams: { toJSON: -> tags: [ 1 ] })
      @$('select').val('all').change()
      expect(@state._reset.all).to.have.been.called

    it 'should select a tag on click', ->
      tag = @collection.get(2)
      @state.set(documentListParams: { toJSON: -> tags: [ 3 ] })
      @$('select').val(tag.cid).change()
      expect(@state._reset.byTag).to.have.been.calledWith(tag)

    it 'should reselect a tag on click', ->
      tag = @collection.get(2)
      @state.set(documentListParams: { toJSON: -> tags: [ 2 ] })
      @$('select').val(tag.cid).change()
      expect(@state._reset.byTag).to.have.been.calledWith(tag)

    it 'should set the selected value when the highlight changes', ->
      tag = @collection.get(2)
      @state.set(documentListParams: { toJSON: -> tags: [ 2 ] })
      expect(@$('select')).to.have.value(tag.cid)

    it 'should select untagged', ->
      @$('select').val('untagged').change()
      expect(@state._reset.byUntagged).to.have.been.called

    it 'should set the selected value when highlighting untagged', ->
      @state.set(documentListParams: { toJSON: -> tagged: false })
      expect(@$('select')).to.have.value('untagged')

    it 'should trigger organize-clicked', ->
      @view.on('organize-clicked', spy = sinon.spy())
      @$('a.organize').click()
      expect(spy).to.have.been.called
