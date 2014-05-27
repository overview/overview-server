define [
  'apps/Tree/controllers/TagDialogController'
  'i18n'
  'bootstrap-modal'
], (TagDialogController, i18n) ->
  describe 'apps/Tree/controllers/TagDialogController', ->
    view = undefined
    controller = undefined
    tagStoreProxy = undefined
    cache = undefined
    oldFnModal = undefined
    $dialog = undefined
    state = undefined

    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.tag_list.header': 'header'
      view = new Backbone.View
      state = new Backbone.Model
      tagStoreProxy =
        tagLikeStore: {}
        setChangeOptions: sinon.stub()
        canMap: sinon.stub()
        map: sinon.stub()
        unmap: sinon.stub()
      cache =
        add_tag: sinon.spy()
        create_tag: sinon.spy()
        update_tag: sinon.spy()
        delete_tag: sinon.spy()
        transaction_queue:
          queue: ->
      $dialog = $('<div></div>')
      oldFnModal = $.fn.modal
      $.fn.modal = sinon.stub().returns($dialog)
      controller = new TagDialogController
        view: view
        tagStoreProxy: tagStoreProxy
        cache: cache
        state: state

    afterEach ->
      controller.stopListening()
      $dialog.remove()
      $.fn.modal = oldFnModal

    describe 'on view:remove', ->
      model = undefined
      tag = undefined

      beforeEach ->
        model = new Backbone.Model(id: 1)
        tag = { id: 1 }
        tagStoreProxy.unmap.returns(tag)

      it 'should call cache.delete_tag', ->
        view.trigger('remove', model)
        expect(tagStoreProxy.unmap).to.have.been.calledWith(model)
        expect(cache.delete_tag).to.have.been.calledWith(tag)

      it 'should unset the state taglike if needed', ->
        state.set('taglike', { tagId: 1 })
        view.trigger('remove', model)
        expect(state.get('taglike')).to.be(null)

      it 'should not unset the state taglike if not needed', ->
        state.set('taglike', { tagId: 2 })
        view.trigger('remove', model)
        expect(state.get('taglike')).to.deep.eq({ tagId: 2 })

      it 'should reset the documentListParams if needed', ->
        state.set(documentListParams: { type: 'tag', tagId: 1 })
        state.setDocumentListParams = sinon.spy()
        view.trigger('remove', model)
        expect(state.setDocumentListParams).to.have.been.called

      it 'should not reset the documentListParams if not needed', ->
        state.set(documentListParams: { type: 'tag', tagId: 2 })
        state.setDocumentListParams = sinon.spy()
        view.trigger('remove', model)
        expect(state.setDocumentListParams).not.to.have.been.called
