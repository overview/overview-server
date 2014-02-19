define [
  'jquery'
  'apps/ImportOptions/views/TagIdInput'
  'i18n'
], ($, TagIdInput, i18n) ->
  describe 'apps/ImportOptions/views/TagIdInput', ->
    el = undefined
    model = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.index.ImportOptions.tag.loading': 'tag.loading'
        'views.DocumentSet.index.ImportOptions.tag.error': 'tag.error'
        'views.DocumentSet.index.ImportOptions.tag.empty': 'tag.empty'
        'views.DocumentSet.index.ImportOptions.tag.name': 'tag.name,{0},{1}'
        'views.DocumentSet.index.ImportOptions.tag.allDocuments': 'tag.allDocuments'

      el = document.createElement('div')
      document.body.appendChild(el)
      model = new Backbone.Model
      view = new TagIdInput(el: el, model: model, tagListUrl: '/tags')
      view.render()

    afterEach ->
      view.remove()

    it 'should set tag_id to the empty string on the model', ->
      expect(model.get('tag_id')).toEqual('')

    it 'should show all tags are selected', ->
      expect(view.$('.dropdown-toggle').text()).toEqual('tag.allDocuments')

    describe 'after clicking .dropdown-toggle', ->
      deferred = undefined

      beforeEach ->
        deferred = $.Deferred()
        spyOn($, 'ajax').andReturn(deferred)
        view.$('.dropdown-toggle').click()

      it 'should show li.loading', -> expect(view.$('.dropdown.open li.loading')).toBeVisible()
      it 'should request the tags', -> expect($.ajax).toHaveBeenCalled()

      describe 'after loading fails', ->
        beforeEach -> deferred.reject()

        it 'should remove li.loading', -> expect(view.$('li.loading').length).toEqual(0)
        it 'should add li.error', -> expect(view.$('li.error').text()).toEqual('tag.error')

      describe 'after loading succeeds', ->
        beforeEach -> deferred.resolve(tags: [
          { id: 1, name: 'Tag 1', color: '#000001', size: 1 }
          { id: 2, name: 'Tag 2', color: '#000002', size: 2 }
          { id: 3, name: 'Tag 3', color: '#000003', size: 3 }
          { id: 4, name: 'Tag 4', color: '#000004', size: 4 }
        ])

        it 'should remove li.loading', -> expect(view.$('li.loading').length).toEqual(0)
        it 'should add a select-all option', -> expect(view.$('a[data-tag-id=""]').text()).toEqual('tag.allDocuments')
        it 'should add an option per tag', -> expect(view.$('a[data-tag-id="3"]').text()).toEqual('tag.name,Tag 3,3')

        it 'should select all on click select-all', ->
          model.set('tag_id', 3)
          view.$('a[data-tag-id=""]').click()
          expect(model.get('tag_id')).toEqual('')

        describe 'on tag select', ->
          beforeEach -> view.$('a[data-tag-id="3"]').click()

          it 'should set tag_id on the model', -> expect(model.get('tag_id')).toEqual('3')
          it 'should hide the dropdown', -> expect(view.$('.dropdown')).not.toHaveClass('open')
          it 'should adjust the dropdown-toggle text', -> expect(view.$('.dropdown-toggle').text()).toEqual('tag.name,Tag 3,3')
