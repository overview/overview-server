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
        'views.DocumentSet.show.tag_list.header': 'header'
      view = new Backbone.View
      state = new Backbone.Model
      tagStoreProxy =
        tagLikeStore: {}
        setChangeOptions: jasmine.createSpy('setChangeOptions')
        canMap: jasmine.createSpy('canMap')
        map: jasmine.createSpy('map')
        unmap: jasmine.createSpy('unmap')
      cache =
        add_tag: jasmine.createSpy('add_tag')
        create_tag: jasmine.createSpy('create_tag')
        update_tag: jasmine.createSpy('update_tag')
        delete_tag: jasmine.createSpy('delete_tag')
        transaction_queue:
          queue: ->
      $dialog = $('<div></div>')
      oldFnModal = $.fn.modal
      $.fn.modal = jasmine.createSpy('$.fn.modal').andReturn($dialog)
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
        tagStoreProxy.unmap.andReturn(tag)

      it 'should call cache.delete_tag', ->
        view.trigger('remove', model)
        expect(tagStoreProxy.unmap).toHaveBeenCalledWith(model)
        expect(cache.delete_tag).toHaveBeenCalledWith(tag)

      it 'should unset the state taglike if needed', ->
        state.set('taglike', { tagId: 1 })
        view.trigger('remove', model)
        expect(state.get('taglike')).toBe(null)

      it 'should not unset the state taglike if not needed', ->
        state.set('taglike', { tagId: 2 })
        view.trigger('remove', model)
        expect(state.get('taglike')).toEqual({ tagId: 2 })

      it 'should reset the documentListParams if needed', ->
        state.set(documentListParams: { type: 'tag', tagId: 1 })
        state.setDocumentListParams = jasmine.createSpy('setDocumentListParams')
        view.trigger('remove', model)
        expect(state.setDocumentListParams).toHaveBeenCalled()

      it 'should not reset the documentListParams if not needed', ->
        state.set(documentListParams: { type: 'tag', tagId: 2 })
        state.setDocumentListParams = jasmine.createSpy('setDocumentListParams')
        view.trigger('remove', model)
        expect(state.setDocumentListParams).not.toHaveBeenCalled()
