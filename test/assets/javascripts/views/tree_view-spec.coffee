# This isn't really a unit test: it depends upon models
TreeView = require('views/tree_view').TreeView

Event = jQuery.Event
Deferred = jQuery.Deferred

OnDemandTree = require('models/on_demand_tree').OnDemandTree
AnimatedTree = require('models/animated_tree').AnimatedTree
Selection = require('models/selection').Selection
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

class MockResolver
  get_deferred: (type, id) -> new Deferred()

describe 'views/tree_view', ->
  describe 'TreeView', ->
    on_demand_tree = undefined
    animated_tree = undefined
    selection = undefined
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
      selection = new Selection()
      resolver = new MockResolver()
      on_demand_tree = new OnDemandTree(resolver, { cache_size: 1000 })
      animated_tree = new AnimatedTree(on_demand_tree, selection, animator)
      div = $('<div style="width:100px;height:100px"></div>')[0]
      $('body').append(div)
      options = {}
      events = undefined

    afterEach ->
      options = {}
      $(div).remove() # Removes all callbacks
      $(window).off('resize.tree-view')
      div = undefined
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
      view = new TreeView(div, animated_tree, options)
      rgb_background = color_to_rgb(view.options.color.background)
      rgb_node = color_to_rgb(view.options.color.node)
      rgb_node_unloaded = color_to_rgb(view.options.color.node_unloaded)
      rgb_node_selected = color_to_rgb(view.options.color.node_selected)

    maybe_observe_events = () ->
      return if events?

      events = []
      view.observe('click', (nodeid) -> events.push(['click', nodeid]))

    click_pixel = (x, y) ->
      maybe_observe_events()
      $canvas = $(view.canvas)

      canvas_position = $canvas.position()
      e = Event('click')
      e.pageX = canvas_position.left + x
      e.pageY = canvas_position.top + y
      $canvas.trigger(e)

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

      it 'should make a canvas of the proper size', ->
        $canvas = $(view.canvas)
        expect($canvas.width()).toEqual(100)
        expect($canvas.height()).toEqual(100)

      it 'should render an empty canvas', ->
        check_pixel(0, 0, rgb_background)
        check_pixel(50, 50, rgb_background)
        check_pixel(99, 99, rgb_background)

      it 'should add a root that appears after drawing', ->
        Timecop.freeze new Date(0), -> add_node_through_deferred(1, [], 1)
        Timecop.freeze new Date(1000), -> view.update()
        check_pixel(0, 0, rgb_background)
        check_pixel(50, 50, rgb_node)
        check_pixel(99, 99, rgb_background)

      it 'should draw unloaded nodes', ->
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

    describe 'with a full tree', ->
      beforeEach ->
        # two-level tree
        Timecop.freeze new Date(0), ->
          add_node_through_deferred(1, [2, 3], 2)
          add_node_through_deferred(2, [], 1)
          add_node_through_deferred(3, [], 1)
        Timecop.freeze new Date(1000), -> animated_tree.update()
        create_view()

      it 'should put padding around nodes', ->
        # check for backgrounds
        check_pixel(1, 25, rgb_background) # left of the root node
        check_pixel(98, 25, rgb_background) # left of the root node
        check_pixel(1, 75, rgb_background) # left of the leftmost node
        check_pixel(98, 75, rgb_background) # right of the rightmost node
        check_pixel(50, 75, rgb_background) # in between the subnodes

      it 'should draw a line from parent to child', ->
        middle_x_of_parent = 50
        middle_x_of_child = 25
        middle_y = 50
        rgb = get_pixel((middle_x_of_parent + middle_x_of_child) * 0.5, middle_y)
        expect(rgb).toNotEqual(rgb_background)

      it 'should trigger :click with the proper node ID', ->
        click_pixel(75, 75)
        expect(events[0]).toEqual(['click', 3])

      it 'should highlight the selected node', ->
        rgb = get_pixel(25, 75)
        expect(rgb).toEqual(rgb_node)
        at 100, -> selection.update({ nodes: [2] })
        at 1100, -> view.update()
        rgb = get_pixel(25, 75)
        expect(rgb).toEqual(rgb_node_selected)

    describe 'with a non-full tree', ->
      beforeEach ->
        # three-level binary tree, right-middle node isn't full
        Timecop.freeze new Date(0), ->
          add_node_through_deferred(1, [2, 7], 4)
          add_node_through_deferred(2, [3, 4], 2)
          add_node_through_deferred(3, [], 1)
          add_node_through_deferred(4, [], 1)
        Timecop.freeze new Date(1000), -> animated_tree.update()
        create_view()

      it 'should trigger :click on a unloaded node', ->
        click_pixel(75, 50)
        expect(events[0]).toEqual(['click', 7])

      it 'should trigger :click on an unloaded node when we click below it', ->
        click_pixel(75, 75)
        expect(events[0]).toEqual(['click', 7])

      it 'should select the proper level, with pixel perfection', ->
        click_pixel(20, 0)
        expect(events[0]).toEqual(['click', 1])
        click_pixel(20, 30)
        expect(events[1]).toEqual(['click', 1])
        click_pixel(20, 34)
        expect(events[2]).toEqual(['click', 2])
        click_pixel(20, 66)
        expect(events[3]).toEqual(['click', 2])
        click_pixel(20, 67)
        expect(events[4]).toEqual(['click', 3])
        click_pixel(20, 99)
        expect(events[5]).toEqual(['click', 3])

      it 'should select the proper document, with pixel perfection', ->
        click_pixel(0, 70)
        expect(events[0]).toEqual(['click', 3])
        click_pixel(24, 70)
        expect(events[1]).toEqual(['click', 3])
        click_pixel(25, 70)
        expect(events[2]).toEqual(['click', 4])
        click_pixel(49, 70)
        expect(events[3]).toEqual(['click', 4])
        click_pixel(50, 70)
        expect(events[4]).toEqual(['click', 7])
        click_pixel(99, 70)
        expect(events[5]).toEqual(['click', 7])

      it 'should resize and notify :needs-update when resized', ->
        called = false
        view.observe('needs-update', (() -> called = true))
        $(div).width(50)
        $(window).resize()
        expect(called).toBe(true)
        view.update()
        expect($(view.canvas).width()).toEqual(50)
