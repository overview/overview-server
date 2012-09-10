TagListView = require('views/tag_list_view').TagListView

observable = require('models/observable').observable

$ = jQuery

class MockTagList
  observable(this)

  constructor: () ->
    @tags = []

  find_tag_by_name: (name) ->
    _.find(@tags, (o) -> o.name == name)

  find_tag_by_id: (id) ->
    _.find(@tags, (o) -> o.id == id)

class MockSelection
  constructor: (obj) ->
    this[k] = v for k, v of obj

class MockState
  observable(this)

  constructor: () ->
    @selection = new MockSelection({ nodes: [], tags: [], documents: []})
    @focused_tag = undefined

describe 'views/tag_list_view', ->
  describe 'TagListView', ->
    div = undefined
    view = undefined
    tag_list = undefined
    state = undefined

    beforeEach ->
      div = $('<div></div>')[0]
      $('body').append(div)
      tag_list = new MockTagList()
      state = new MockState()
      view = new TagListView(div, tag_list, state)

    afterEach ->
      $(div).remove() # removes event handlers

    it 'should start without tags', ->
      non_form_li_exists = _.any($('ul', div), (e) -> $('form', e).length == 0)
      expect(non_form_li_exists).toBe(false)

    it 'should end with a form', ->
      $form = $('form', div)
      expect($form.length).toEqual(1)

    describe 'with tags', ->
      tag1 = { position: 0, id: 1, name: 'AA', doclist: { n: 10, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] } }
      tag2 = { position: 1, id: 2, name: 'BB', doclist: { n: 8, docids: [ 2, 4, 6, 8, 10, 12, 14, 16 ] } }

      beforeEach ->
        tag_list.tags.push(tag1)
        tag_list._notify('tag-added', tag1)
        tag_list.tags.push(tag2)
        tag_list._notify('tag-added', tag2)

      it 'should show tags', ->
        $lis = $('li', div)
        expect($lis.length).toEqual(3)
        expect($($lis[0]).text()).toMatch(/^AA/)
        expect($($lis[1]).text()).toMatch(/^BB/)

      it 'should remove tags', ->
        tag_list.tags.shift()
        tag_list._notify('tag-removed', { position: 0, tag: tag1 })
        $lis = $('li', div)
        expect($lis.length).toEqual(2)
        expect($($lis[0]).text()).toMatch(/^BB/)

      it 'should notify :create-submitted', ->
        $form = $('form', div)
        $form.find('input[type=text]').val('foo')
        val = undefined
        view.observe('create-submitted', (v) -> val = v)
        $form.submit()
        expect(val).toEqual({ name: 'foo' })

      it 'should trim the string in :create-submitted', ->
        $form = $('form', div)
        $form.find('input[type=text]').val('  foo ')
        val = undefined
        view.observe('create-submitted', (v) -> val = v)
        $form.submit()
        expect(val).toEqual({ name: 'foo' })

      it 'should reset the form after :create-submitted', ->
        $form = $('form', div)
        $form.find('input[type=text]').val('foo')
        $form.submit()
        expect($form.find('input[type=text]').val()).toEqual('')

      it 'should notify :add-clicked', ->
        val = undefined
        view.observe('add-clicked', (v) -> val = v)
        $(div).find('a.tag-add:eq(0)').click()
        expect(val).toBe(tag1)

      it 'should notify :remove-clicked', ->
        val = undefined
        view.observe('remove-clicked', (v) -> val = v)
        $(div).find('a.tag-remove:eq(0)').click()
        expect(val).toBe(tag1)

      it 'should notify :add-clicked when trying to create an existing tag', ->
        $form = $('form', div)
        $form.find('input[type=text]').val('AA')
        val = undefined
        view.observe('add-clicked', (v) -> val = v)
        $form.submit()
        expect(val).toEqual(tag1)

      it 'should notify :tag-clicked when clicking a tag', ->
        val = undefined
        view.observe('tag-clicked', (v) -> val = v)
        $('a.tag-name:eq(0)', div).click()
        expect(val).toBe(tag1)

      it 'should set "shown" on shown tag', ->
        state.focused_tag = tag1
        state._notify('focused_tag-changed', tag1)
        class_name = $('li:eq(0)', div).attr('class')
        expect(class_name).toMatch(/\bshown\b/)

      it 'should set "selected" on selected tags', ->
        state.selection.tags = [1, 2]
        state._notify('selection-changed', state.selection)
        $lis = $('li.selected', div)
        expect($lis.length).toEqual(2)
