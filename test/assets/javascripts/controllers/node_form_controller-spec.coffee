node_form_controller = require('controllers/node_form_controller').node_form_controller

observable = require('models/observable').observable

class MockNodeFormView
  observable(this)

  constructor: (@node) ->

  close: () -> @_notify('closed')
  change: (new_node) -> @_notify('change', new_node)

# Mostly copy/pasted from tag_form_controller-spec
describe 'controllers/node_form_controller', ->
  describe 'node_form_controller', ->
    controller = undefined
    log_values = undefined
    cache = undefined
    state = undefined
    view = undefined
    node = { id: 1, description: 'node', color: '#abcdef' }

    options = {
      log: (s1, s2) -> log_values.push([s1, s2])
      create_form: (node) -> view = new MockNodeFormView(node)
    }

    beforeEach ->
      log_values = []
      cache = jasmine.createSpyObj('cache', [ 'update_node' ])
      state = jasmine.createSpyObj('state', [ 'set' ])
      state.selection = jasmine.createSpyObj('selection', [ 'minus' ])
      controller = node_form_controller(node, cache, state, options)

    it 'should create a view when called', ->
      expect(view).toBeDefined()

    it 'should call cache.update_node on change', ->
      new_node = { description: 'node2' }
      view.change(new_node)
      expect(cache.update_node).toHaveBeenCalledWith(node, new_node)

    it 'should log on start', ->
      expect(log_values[0]).toEqual(['began editing node', '1 (node)'])

    it 'should log on exit', ->
      view.close()
      expect(log_values[1]).toEqual(['stopped editing node', '1 (node)'])

    it 'should log on change', ->
      view.change({ description: 'new-description', color: '#fedcba' })
      expect(log_values[1]).toEqual(['edited node', '1: description: <<node>> to <<new-description>>'])

    it 'should log on no-change', ->
      view.change({ description: 'node', color: '#abcdef' })
      expect(log_values[1]).toEqual(['edited node', '1: (no change)'])
