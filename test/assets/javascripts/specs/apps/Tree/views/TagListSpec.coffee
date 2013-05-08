require [
  'underscore'
  'backbone'
  'apps/Tree/views/TagList'
  'i18n'
], (_, Backbone, TagList, i18n) ->
  makeModel = (name="name", color="#abcdef", options={}) ->
    new Backbone.Model(_.extend({}, { name: name, color: color }, options))

  tagToCount = (tag) ->
    m = /(\d+)/.exec(tag.cid)
    +m[1]

  describe 'apps/Tree/views/TagList', ->
    collection = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages({
        'views.DocumentSet.show.tag_list.preamble': 'preamble'
        'views.DocumentSet.show.tag_list.export': 'export'
        'views.DocumentSet.show.tag_list.remove': 'remove'
        'views.DocumentSet.show.tag_list.remove.confirm': 'remove.confirm,{0},{1}'
        'views.DocumentSet.show.tag_list.submit': 'submit'
        'views.DocumentSet.show.tag_list.tag_name.placeholder': 'tag_name.placeholder'
        'views.DocumentSet.show.tag_list.n_documents': 'n_documents,{0}'
      })
      collection = new Backbone.Collection([])
      view = new TagList({
        collection: collection
        tagToCount: tagToCount
      })

    afterEach ->
      view?.remove()

    it 'should render an empty list', ->
      expect(view.$('ul').children().length).toEqual(1)

    it 'should render a "new" form', ->
      expect(view.$('li.new form').length).toEqual(1)

    it 'should render list items on reset', ->
      collection.reset([ makeModel() ])
      expect(view.$('ul').children().length).toEqual(2)

    it 'should trigger add', ->
      val = undefined
      view.once('add', (v) -> val = v)
      $form = view.$('form')
      $form.find('input[name=name]').val('new tag')
      $form.submit()
      expect(val).toEqual({ name: 'new tag' })

    it 'should not show an export link', ->
      view = new TagList({
        collection: collection
        tagToCount: tagToCount
        exportUrl: 'https://example.org'
      })
      expect(view.$('a.export').length).toEqual(0)

    describe 'starting with two tags', ->
      beforeEach ->
        collection.reset([ makeModel('tag10'), makeModel('tag20') ])

      it 'should add a tag to the end of the list', ->
        collection.add(makeModel('tag30'))
        expect(view.$('ul>li:eq(2)').html()).toContain('tag30')

      it 'should add a tag to the beginning of the list', ->
        collection.add([makeModel('tag05')], { at: 0 })
        expect(view.$('ul>li:eq(0)').html()).toContain('tag05')

      it 'should add a tag to the middle of the list', ->
        collection.add([makeModel('tag15')], { at: 1 })
        expect(view.$('ul>li:eq(1)').html()).toContain('tag15')

      it 'should remove a tag', ->
        collection.remove(collection.first())
        expect(view.$('ul>li:eq(0)').html()).toContain('tag20')

      it 'should change a tag', ->
        collection.first().set({
          name: 'tag11'
          color: '#111111'
        })
        $li = view.$('li:eq(0)')
        expect($li.find('input[name=name]').val()).toEqual('tag11')
        expect($li.find('input[name=color]').val()).toEqual('#111111')

      it 'should not change a tag when interacting', ->
        collection.first().set(
          { name: 'tag11', color: '#111111' },
          { interacting: true }
        )
        $li = view.$('li:eq(0)')
        expect($li.find('input[name=name]').val()).toEqual('tag10')
        expect($li.find('input[name=color]').val()).toEqual('#abcdef')

      it 'should change a tag ID', ->
        collection.first().set({ id: 3 })
        expect(view.$('li:eq(0) input[name=id]').val()).toEqual('3')

      it 'should change a tag id even when interacting', ->
        collection.first().set({ id: 3 }, { interacting: true })
        expect(view.$('li:eq(0) input[name=id]').val()).toEqual('3')

      it 'should show an export link', ->
        view = new TagList({
          collection: collection
          tagToCount: tagToCount
          exportUrl: 'https://example.org'
        })
        expect(view.$('a.export').attr('href')).toEqual('https://example.org')

      it 'should trigger update when changing fields', ->
        tag = undefined
        attrs = undefined
        view.once('update', (v1, v2) -> tag = v1; attrs = v2)
        $form = view.$('form:eq(0)')
        $input = $form.find('input[name=name]')
        $input.val('foobar')
        $input.change()
        expect(tag).toBeDefined()
        expect(tag.cid).toEqual(collection.first().cid)
        expect(tag.get('name')).toEqual('tag10')
        expect(attrs).toBeDefined()
        expect(attrs.name).toEqual('foobar')

      it 'should not leave the page when pressing Enter', ->
        # Normally, a submit would crash the whole test suite.
        # So if nothing happens here, we're okay
        view.$('form:eq(0)').submit()
        expect(1).toEqual(1)

      it 'should trigger remove', ->
        tag = undefined
        view.once('remove', (v) -> tag = v)
        spyOn(window, 'confirm').andReturn(true)
        view.$('li:eq(0) a.remove').click()
        expect(tag).toBeDefined()

      it 'should not trigger remove if not confirmed', ->
        tag = undefined
        view.once('remove', (v) -> tag = v)
        spyOn(window, 'confirm').andReturn(false)
        view.$('li:eq(0) a.remove').click()
        expect(tag).toBeUndefined()

      it 'should confirm remove with the tag name and count', ->
        spyOn(window, 'confirm').andReturn(false)
        view.$('li:eq(0) a.remove').click()
        expect(window.confirm).toHaveBeenCalledWith("remove.confirm,tag10,#{tagToCount(collection.first())}")
