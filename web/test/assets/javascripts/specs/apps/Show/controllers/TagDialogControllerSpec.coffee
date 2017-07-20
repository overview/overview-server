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
      initialize: ->
        @refineDocumentListParams = sinon.spy()

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub($.fn, 'modal').returnsThis()
      @sandbox.stub(Backbone, 'ajax')
      @sandbox.stub(Backbone, 'sync') # just in case

      i18n.reset_messages
        'views.Tree.show.tag_list.header': 'header'

      @state = new State()
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

    it 'should fetch counts from the server', ->
      expect(Backbone.ajax).to.have.been.calledWithMatch(url: '/path/to/tags')

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

      it 'should reset the documentListParams if needed', ->
        @state.set(documentList: { params: { tags: { ids: [ 1 ] }}})
        @view.trigger('remove', @tag)
        expect(@state.refineDocumentListParams).to.have.been.calledWith(tags: null)

      it 'should not reset the documentListParams if not needed', ->
        @state.set(documentList: { params: { tags: { ids: [ 2 ] }}})
        @view.trigger('remove', @tag)
        expect(@state.refineDocumentListParams).not.to.have.been.called
