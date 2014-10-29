define [
  'backbone'
  'apps/Show/controllers/TagDialogController'
  'i18n'
  'bootstrap-modal'
], (Backbone, TagDialogController, i18n) ->
  describe 'apps/Show/controllers/TagDialogController', ->
    class Tag extends Backbone.Model

    class Tags extends Backbone.Collection
      model: Tag
      url: '/path/to/tags'

    class State extends Backbone.Model
      defaults:
        taglikeCid: null
        view: null

      initialize: ->
        @_reset =
          all: (->)

      resetDocumentListParams: -> @_reset

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub($.fn, 'modal', -> this)
      @sandbox.stub(Backbone, 'sync') # just in case

      i18n.reset_messages
        'views.Tree.show.tag_list.header': 'header'

      @state = new State()
      @state._reset.all = sinon.spy()
      @tags = new Tags(url: '/path/to/tags')
      @tags.fetch = sinon.stub()
      @view = new Backbone.View

      @controller = new TagDialogController
        view: @view
        tags: @tags
        state: @state

    afterEach ->
      @controller.stopListening()
      if ($dialog = $.fn.modal.firstCall?.thisValue)?
        $dialog.remove()
      @sandbox.restore()

    it 'should sync from the server', ->
      expect(@tags.fetch).to.have.been.calledWithMatch(url: '/path/to/tags')

    it 'should sync a view-specific URL if there is a view', ->
      @state.set(view: new Backbone.Model(id: 1234))
      new TagDialogController(view: @view, tags: @tags, state: @state)
      expect(@tags.fetch).to.have.been.calledWithMatch(url: '/path/to/trees/1234/tags')

    describe 'on view:remove', ->
      beforeEach ->
        @tag = new Tag(id: 1)
        @tags.add(@tag)

      it 'should remove the tag from the collection', ->
        @view.trigger('remove', @tag)
        expect(@tags.get(1)).to.be.undefined

      it 'should call Backbone.sync', ->
        @view.trigger('remove', @tag)
        expect(Backbone.sync).to.have.been.calledWith('delete', @tag)

      it 'should unset the state taglikeCid if needed', ->
        @state.set(taglikeCid: @tag.cid)
        @view.trigger('remove', @tag)
        expect(@state.get('taglikeCid')).to.be.null

      it 'should not unset the state taglikeCid if not needed', ->
        @state.set(taglikeCid: 'foo')
        @view.trigger('remove', @tag)
        expect(@state.get('taglikeCid')).to.eq('foo')

      it 'should reset the documentListParams if needed', ->
        @state.set(documentListParams: { type: 'tag', tag: @tag })
        @view.trigger('remove', @tag)
        expect(@state._reset.all).to.have.been.called

      it 'should not reset the documentListParams if not needed', ->
        @state.set(documentListParams: { type: 'tag', tag: new Tag() })
        @view.trigger('remove', @tag)
        expect(@state._reset.all).not.to.have.been.called
