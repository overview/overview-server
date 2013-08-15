define [
  'apps/Tree/controllers/tag_form_controller'
  'apps/Tree/models/observable'
  'backbone'
], (tag_form_controller, observable, Backbone) ->
  class MockTagFormView
    observable(this)

    constructor: (@tag) ->

    close: () -> @_notify('closed')
    change: (new_tag) -> @_notify('change', new_tag)
    delete: () -> @_notify('delete')

  describe 'controllers/tag_form_controller', ->
    describe 'tag_form_controller', ->
      controller = undefined
      log_values = undefined
      cache = undefined
      state = undefined
      view = undefined
      tag = { id: 1, name: 'tag', color: '#abcdef' }

      options = {
        log: (s1, s2) -> log_values.push([s1, s2])
        create_form: (tag) -> view = new MockTagFormView(tag)
      }

      beforeEach ->
        log_values = []
        cache = jasmine.createSpyObj('cache', [ 'update_tag', 'delete_tag' ])
        state = new Backbone.Model({ selection: jasmine.createSpyObj('selection', [ 'minus' ]) })
        controller = tag_form_controller(tag, cache, state, options)

      it 'should create a view when called', ->
        expect(view).toBeDefined()

      it 'should call cache.update_tag on change', ->
        new_tag = { name: 'tag2', color: '#fedcba' }
        view.change(new_tag)
        expect(cache.update_tag).toHaveBeenCalledWith(tag, new_tag)

      it 'should call cache.delete_tag on delete', ->
        view.delete()
        expect(cache.delete_tag).toHaveBeenCalledWith(tag)

      it 'should deselect state.taglike if necessary on delete', ->
        state.set('taglike', tag)
        view.delete()
        expect(state.get('taglike')).toBe(null)

      it 'should remove the tag from state.selection if necessary on delete', ->
        empty_selection = { tags: [], nodes: [], documents: [] }
        state.set('selection', {
          tags: []
          nodes: []
          documents: []
          minus: (sub) ->
            expect(sub).toEqual({ tags: [1] })
            empty_selection
        })

        view.delete()
        expect(state.get('selection')).toEqual(empty_selection)

      it 'should log on start', ->
        expect(log_values[0]).toEqual(['began editing tag', '1 (tag)'])

      it 'should log on exit', ->
        view.close()
        expect(log_values[1]).toEqual(['stopped editing tag', '1 (tag)'])

      it 'should log on change', ->
        view.change({ name: 'new-name', color: '#fedcba' })
        expect(log_values[1]).toEqual(['edited tag', '1: name: <<tag>> to <<new-name>> color: <<#abcdef>> to <<#fedcba>>'])

      it 'should log on half-change', ->
        view.change({ name: 'new-name', color: '#abcdef' })
        expect(log_values[1]).toEqual(['edited tag', '1: name: <<tag>> to <<new-name>>'])
        view.change({ name: 'tag', color: '#fedcba' })
        expect(log_values[2]).toEqual(['edited tag', '1: color: <<#abcdef>> to <<#fedcba>>'])

      it 'should log on no-change', ->
        view.change({ name: 'tag', color: '#abcdef' })
        expect(log_values[1]).toEqual(['edited tag', '1: (no change)'])

      it 'should log on delete', ->
        view.delete()
        expect(log_values[1]).toEqual(['deleted tag', '1 (tag)'])
