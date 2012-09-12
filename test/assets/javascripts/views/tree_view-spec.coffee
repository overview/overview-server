# This isn't really a unit test: it depends upon models
TreeView = require('views/tree_view').TreeView

Event = jQuery.Event
Deferred = jQuery.Deferred

observable = require('models/observable').observable
OnDemandTree = require('models/on_demand_tree').OnDemandTree
AnimatedTree = require('models/animated_tree').AnimatedTree
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

class MockFocus
  observable(this)

  constructor: () ->
    @zoom = 1
    @pan = 0
    @_update_called = false
    @_needs_update = false

  set_zoom: (@zoom) ->
  set_pan: (@pan) ->
  update: () -> @_update_called = true
  needs_update: () -> @_needs_update

class MockResolver
  get_deferred: (type, id) -> new Deferred()

class MockState
  observable(this)

  constructor: () ->
    @selection = { nodes: [], tags: [], documents: [] }

describe 'views/tree_view', ->
  describe 'TreeView', ->
    on_demand_tree = undefined
    animated_tree = undefined
    state = undefined
    focus = undefined
    view = undefined
    div = undefined
    options = undefined
    rgb_background = undefined
    rgb_node = undefined
    rgb_node_unloaded = undefined
    rgb_node_selected = undefined
    events = undefined

    beforeEach ->
      interpolator = new PropertyInterpolator(1000, (x) -> x)
      animator = new Animator(interpolator)
      state = new MockState()
      focus = new MockFocus()
      resolver = new MockResolver()
      on_demand_tree = new OnDemandTree(resolver, { cache_size: 1000 })
      animated_tree = new AnimatedTree(on_demand_tree, state, animator)
      div = $('<div style="width:100px;height:100px"></div>')[0]
      $('body').append(div)
      options = { mousewheel_zoom_factor: 2 }
      events = undefined

    afterEach ->
      options = {}
      $(div).remove() # Removes all callbacks
      $(window).off('.tree-view')
      $('body').off('.tree-view')
      div = undefined
      focus = undefined
      state = undefined
      on_demand_tree = undefined
      animated_tree = undefined
      view = undefined
      events = undefined

    add_nodes_through_deferred = (nodes) ->
      deferred = on_demand_tree.demand_node(nodes[0].id)
      deferred.resolve({ nodes: nodes })

    add_node_through_deferred = (id, children, n_documents) ->
      add_nodes_through_deferred([ { id: id, children: children, doclist: { n: n_documents } } ])

    create_view = () ->
      view = new TreeView(div, animated_tree, focus, options)
      rgb_background = color_to_rgb(view.options.color.background)
      rgb_node = color_to_rgb(view.options.color.node)
      rgb_node_unloaded = color_to_rgb(view.options.color.node_unloaded)
      rgb_node_selected = color_to_rgb(view.options.color.node_selected)

    maybe_observe_events = () ->
      return if events?

      events = []
      view.observe('click', (nodeid) -> events.push(['click', nodeid]))
      view.observe('pan', (fraction) -> events.push(['pan', fraction]))
      view.observe('zoom-pan', (zoom_and_pan) -> events.push(['zoom-pan', zoom_and_pan]))

    mouse_event = (name, x, y, properties={}) ->
      maybe_observe_events()
      $canvas = $(view.canvas)

      canvas_position = $canvas.position()
      e = Event(name)
      e.which = 1
      e.pageX = canvas_position.left + x
      e.pageY = canvas_position.top + y
      e[k] = v for k, v of properties
      $canvas.trigger(e)
      e

    click_pixel = (x, y) -> mouse_event('click', x, y)

    get_pixel = (x, y) ->
      data = view.canvas.getContext('2d').getImageData(x, y, 1, 1).data
      [ data[0], data[1], data[2] ]

    check_pixel = (x, y, expect_rgb) ->
      rgb = get_pixel(x, y)
      expect(rgb).toEqual(expect_rgb)

    color_to_rgb = (s) ->
      [
        parseInt(s.substring(1, 3), 16),
        parseInt(s.substring(3, 5), 16),
        parseInt(s.substring(5, 7), 16),
      ]

    at = (ms, callback) -> Timecop.freeze(new Date(ms), callback)

    describe 'beginning with an empty tree', ->
      beforeEach ->
        create_view()

      it 'should notify :needs-update when the tree does', ->
        called = false
        view.observe('needs-update', (() -> called = true))
        animated_tree._notify('needs-update')
        expect(called).toBe(true)

      it 'should notify :needs-update when the focus does', ->
        called = false
        view.observe('needs-update', (() -> called = true))
        focus._notify('needs-update')
        expect(called).toBe(true)

      it 'should update() the focus', ->
        focus._update_called = false
        focus._needs_update = true
        view.update()
        expect(focus._update_called).toBe(true)

      it 'should make a canvas of the proper size', ->
        $canvas = $(view.canvas)
        expect($canvas.width()).toEqual(100)
        expect($canvas.height()).toEqual(100)

      it 'should render an empty canvas', ->
        check_pixel(0, 0, rgb_background)
        check_pixel(50, 50, rgb_background)
        check_pixel(99, 99, rgb_background)

      xit 'should add a root that appears after drawing', ->
        at(0, -> add_node_through_deferred(1, [], 1))
        at(1000, -> view.update())
        check_pixel(0, 0, rgb_background)
        check_pixel(50, 50, rgb_node)
        check_pixel(99, 99, rgb_background)

      xit 'should draw unloaded nodes', ->
        at 0, -> add_node_through_deferred(1, [2], 1)
        at 1000, -> view.update()
        check_pixel(0, 0, rgb_background)
        check_pixel(50, 25, rgb_node)
        check_pixel(60, 50, rgb_background)
        check_pixel(50, 75, rgb_node_unloaded)
        check_pixel(99, 99, rgb_background)

      it 'should trigger :click on undefined', ->
        click_pixel(50, 50)
        expect(events[0]).toEqual(['click', undefined])

      it 'should resize and notify :needs-update when resized', ->
        called = false
        view.observe('needs-update', (() -> called = true))
        $(div).width(50)
        $(window).resize()
        expect(called).toBe(true)
        view.update()
        expect($(view.canvas).width()).toEqual(50)

      it 'should notify :needs-update and set needs_update=true when zoom changes', ->
        called = false
        view.observe('needs-update', (-> called = true))
        focus._notify('zoom', 0.4)
        expect(called).toBe(true)
        expect(view.needs_update()).toBe(true)

      it 'should notify :needs-update and set needs_update=true when pan changes', ->
        called = false
        view.observe('needs-update', (-> called = true))
        focus._notify('pan', 0.2)
        expect(called).toBe(true)
        expect(view.needs_update()).toBe(true)

    describe 'with a full tree', ->
      beforeEach ->
        # two-level tree
        at 0, ->
          add_node_through_deferred(1, [2, 3], 2)
          add_node_through_deferred(2, [], 1)
          add_node_through_deferred(3, [], 1)
        at(1000, -> animated_tree.update())
        create_view()

      it 'should put padding around nodes', ->
        # check for backgrounds
        check_pixel(1, 25, rgb_background) # left of the root node
        check_pixel(98, 25, rgb_background) # right of the root node
        check_pixel(1, 75, rgb_background) # left of the leftmost node
        check_pixel(98, 75, rgb_background) # right of the rightmost node
        check_pixel(50, 75, rgb_background) # in between the subnodes

      xit 'should zoom and pan', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()

        check_pixel(1, 25, rgb_background) # left of the root node
        check_pixel(98, 25, rgb_node) # the root node, which extends past the canvas
        check_pixel(1, 75, rgb_background) # left of the left node
        check_pixel(50, 75, rgb_node) # node 2
        # FIXME why does this break? check_pixel(99, 25, rgb_background) # right of node 2

      it 'should notify :zoom-pan when dragging', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()
        mouse_event('mousedown', 50, 50)
        mouse_event('mousemove', 70, 50) # delta = 20px/100px * zoom = 0.1; -0.25 + delta = -0.15
        expect(events[0]).toEqual(['zoom-pan', { zoom: 0.5, pan: -0.35 }])

      it 'should calculate the second :zoom-pan properly, too', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()
        mouse_event('mousedown', 50, 50)
        mouse_event('mousemove', 70, 50)
        mouse_event('mousemove', 90, 50) # delta = 40px / 100px * zoom = 0.2
        expect(events[1][1].pan).toBeCloseTo(-0.45, 4)

      it 'should stop notifying :zoom-pan on mouseup', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()
        mouse_event('mousedown', 50, 50)
        mouse_event('mouseup', 50, 50)
        called = false
        view.observe('zoom-pan', -> called = true)
        mouse_event('mousemove', 70, 50)
        expect(called).toBe(false)

      it 'should notify zoom-in on mouse-wheel-up', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()
        mouse_event('mousewheel', 25, 50, { deltaY: 1 })
        expect(events[0]).toEqual(['zoom-pan', { zoom: 0.25, pan: -0.3125 }])

      it 'should select properly when zoomed and panned', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        view.update()

        click_pixel(50, 75)
        expect(events[0]).toEqual(['click', 2])

      it 'should draw a line from parent to child', ->
        middle_x_of_parent = 50
        middle_x_of_child = 25
        middle_y = 50
        rgb = get_pixel((middle_x_of_parent + middle_x_of_child) * 0.5, middle_y)
        expect(rgb).toNotEqual(rgb_background)

      it 'should trigger :click with the proper node ID', ->
        click_pixel(75, 75)
        expect(events[0]).toEqual(['click', 3])

      xit 'should highlight the selected node', ->
        rgb = get_pixel(25, 75)
        expect(rgb).toEqual(rgb_node)
        at 100, ->
          state.selection.nodes = [2]
          state._notify('selection-changed', state.selection)
        at 1100, -> view.update()
        rgb = get_pixel(25, 75)
        expect(rgb).toEqual(rgb_node_selected)
