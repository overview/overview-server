define [
  'jquery'
  'apps/ImportOptions/views/TagIdInput'
  'i18n'
], ($, TagIdInput, i18n) ->
  describe 'apps/ImportOptions/views/TagIdInput', ->
    el = undefined
    model = undefined
    view = undefined
    deferred = undefined

    beforeEach ->
      jasmine.Ajax.install()
      i18n.reset_messages
        'views.DocumentSet.index.ImportOptions.tag.loading': 'tag.loading'
        'views.DocumentSet.index.ImportOptions.tag.error': 'tag.error'
        'views.DocumentSet.index.ImportOptions.tag.empty': 'tag.empty'
        'views.DocumentSet.index.ImportOptions.tag.name': 'tag.name,{0},{1}'
        'views.DocumentSet.index.ImportOptions.tag.allDocuments': 'tag.allDocuments'

      spyOn($, 'ajax').and.callFake(-> deferred = $.Deferred())

      el = document.createElement('div')
      document.body.appendChild(el)
      model = new Backbone.Model
      view = new TagIdInput(el: el, model: model, tagListUrl: '/tags')
      view.render()

    afterEach ->
      jasmine.Ajax.uninstall()
      view.remove()

    it 'should select all documents', -> expect(view.$('select option:selected').text()).toEqual('tag.allDocuments')
    it 'should have class=loading', -> expect(view.$el).toHaveClass('loading')
    it 'should show loading', -> expect(view.$('option:disabled').text()).toEqual('tag.loading')
    it 'should request the tags', -> expect($.ajax).toHaveBeenCalled()

    describe 'after loading fails', ->
      beforeEach -> deferred.reject()

      it 'should have class=error', -> expect(view.$el).toHaveClass('error')
      it 'should show error', -> expect(view.$('option:disabled').text()).toEqual('tag.error')

    describe 'after loading succeeds', ->
      beforeEach -> deferred.resolve(tags: [
        { id: 1, name: 'Tag 1', color: '#000001', size: 1 }
        { id: 2, name: 'Tag 2', color: '#000002', size: 2 }
        { id: 3, name: 'Tag 3', color: '#000003', size: 3 }
        { id: 5, name: 'Tag 5', color: '#000003', size: 3 } # out of order
        { id: 4, name: 'Tag 4', color: '#000004', size: 4 }
      ])

      it 'should remove status class', -> expect(view.$el.attr('class')).toEqual('')
      it 'should remove status option', -> expect(view.$('option:eq(1)').attr('value')).toEqual('1')
      it 'should add a select-all option', -> expect(view.$('option[value=""]').text()).toEqual('tag.allDocuments')
      it 'should add an option per tag', -> expect(view.$('option[value="3"]').text()).toEqual('tag.name,Tag 3,3')
      it 'should disable options that have too few elements', -> expect(view.$('option[value="1"]')).toBeDisabled()
      it 'should sort options', -> expect(view.$('option:eq(4)').attr('value')).toEqual('4')

      it 'should select all on select all', ->
        model.set('tag_id', 3)
        $select = view.$('select')
        $select.val('')
        $select.change()
        expect(model.get('tag_id')).toEqual('')

      it 'should select tag on select tag', ->
        $select = view.$('select')
        $select.val('3')
        $select.change()
        expect(model.get('tag_id')).toEqual('3')
