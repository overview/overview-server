# Not a unit test, because this is more useful
AnimatedTree = require('models/animated_tree').AnimatedTree

observable = require('models/observable').observable
OnDemandTree = require('models/on_demand_tree').OnDemandTree
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

Deferred = jQuery.Deferred

class MockResolver
  get_deferred: (type, id) -> new Deferred()

class MockState
  observable(this)

  constructor: () ->
    @selection = { nodes: [], tags: [], documents: [] }

describe 'models/animated_tree', ->
  describe 'AnimatedTree', ->
    on_demand_tree = undefined
    animated_tree = undefined
    state = undefined

    beforeEach ->
      interpolator = new PropertyInterpolator(1000, (x) -> x)
      animator = new Animator(interpolator)
      state = new MockState()
      resolver = new MockResolver()
      on_demand_tree = new OnDemandTree(resolver, { cache_size: 1000 })
      animated_tree = new AnimatedTree(on_demand_tree, state, animator)

    add_nodes = (list) ->
      response = {
        nodes: ({ id: item[0], children: item[1], doclist: { n: item[2] } } for item in list),
      }
      on_demand_tree.demand_node(list[0][0]).resolve(response)

    remove_nodes = (list) ->
      on_demand_tree.id_tree.edit (editable) ->
        editable.remove(id) for id in list

    at = (ms, callback) -> Timecop.freeze new Date(ms), callback

    complete = (callback) ->
      at(1, callback)
      at(1001, -> animated_tree.update())

    it 'should start with no root', ->
      expect(animated_tree.root).toBeUndefined()

    it 'should have an id_tree', ->
      expect(animated_tree.id_tree).toBe(on_demand_tree.id_tree)

    it 'should get a root when resolved', ->
      add_nodes([[1, [2], 1]])
      expect(animated_tree.root).toBeDefined()

    it 'should have children, loaded and unloaded nodes when resolved', ->
      add_nodes([[1, [2], 3]])
      root = animated_tree.root
      expect(root.loaded).toBe(true)
      expect(root.loaded_animation_fraction.current).toEqual(1)
      expect(root.children.length).toEqual(1)
      expect(root.children[0].loaded).toBe(false)
      expect(root.children[0].loaded_animation_fraction.current).toEqual(0)

    it 'should set num_documents on loaded nodes', ->
      add_nodes([[1, [2, 3], 3]])
      expect(animated_tree.root.num_documents.current).toEqual(3)

    it 'should spread num_documents over the unloaded nodes', ->
      add_nodes([[1, [2, 3, 4], 7], [2, [], 3]])
      expect(animated_tree.root.children[1].num_documents.current).toEqual(2)

    it 'should allow a selected, loaded node', ->
      complete ->
        state.selection.nodes = [1]
        state._notify('selection-changed', state.selection)
      add_nodes([[1, [], 1]])
      expect(animated_tree.root.selected).toBe(true)
      expect(animated_tree.root.selected_animation_fraction.current).toEqual(1)

    it 'should allow a selected, unloaded node', ->
      complete ->
        state.selection.nodes = [2]
        state._notify('selection-changed', state.selection)
      add_nodes([[1, [2], 1]])
      node = animated_tree.root.children[0]
      expect(node.selected).toBe(true)
      expect(node.selected_animation_fraction.current).toEqual(1)

    it 'should animate selection', ->
      add_nodes([[1, [2], 1]])
      node = animated_tree.root
      Timecop.freeze new Date(100), ->
        state.selection.nodes = [1]
        state._notify('selection-changed', state.selection)
      expect(node.selected).toBe(true)
      property = node.selected_animation_fraction
      expect(property.v2).toEqual(1)
      expect(property.current).toEqual(0)

    it 'should animate a selection change', ->
      complete ->
        state.selection.nodes = [1, 2]
        state._notify('selection-changed', state.selection)
      add_nodes([[1, [2, 3], 3]])
      n1 = animated_tree.root
      n2 = n1.children[0]
      n3 = n1.children[1]
      Timecop.freeze new Date(100), ->
        state.selection.nodes = [2, 3]
        state._notify('selection-changed', state.selection)
      expect(n1.selected).toBe(false)
      expect(n2.selected).toBe(true)
      expect(n3.selected).toBe(true)
      expect(n1.selected_animation_fraction.current).toBe(1)
      expect(n2.selected_animation_fraction.current).toBe(1)
      expect(n3.selected_animation_fraction.current).toBe(0)

    it 'should animate adding a node', ->
      add_nodes([[1, [2, 3], 3]])
      Timecop.freeze new Date(100), -> add_nodes([[2, [4, 5], 2]])
      n2 = animated_tree.root.children[0]
      expect(n2.loaded).toBe(true)
      expect(n2.loaded_animation_fraction.current).toEqual(0)
      expect(n2.children[0].loaded).toBe(false)
      expect(animated_tree.needs_update()).toBe(true)
      Timecop.freeze new Date(600), -> animated_tree.update()
      expect(n2.loaded_animation_fraction.current).toEqual(0.5)
      expect(animated_tree.needs_update()).toBe(true)
      Timecop.freeze new Date(1100), -> animated_tree.update()
      expect(n2.loaded_animation_fraction.current).toEqual(1)
      expect(animated_tree.needs_update()).toBe(false)

    it 'should animate removing/unloading nodes', ->
      add_nodes([[1, [2, 3], 3], [2, [4, 5], 2]])
      n2 = animated_tree.root.children[0]
      n4 = n2.children[0]
      Timecop.freeze new Date(100), -> remove_nodes([2])
      # This should unload node 2 and remove nodes 4 and 5
      expect(n2.loaded).toBe(false)
      expect(n4.loaded).toBe(false)
      expect(n2.loaded_animation_fraction.current).toEqual(1)
      expect(n2.children.length).toEqual(2)
      expect(animated_tree.needs_update()).toBe(true)
      Timecop.freeze new Date(600), -> animated_tree.update()
      expect(n2.loaded_animation_fraction.current).toEqual(0.5)
      expect(n2.children.length).toEqual(2)
      expect(animated_tree.needs_update()).toBe(true)
      Timecop.freeze new Date(1100), -> animated_tree.update()
      expect(n2.loaded_animation_fraction.current).toEqual(0)
      expect(n2.children.length).toEqual(0)
      expect(animated_tree.needs_update()).toBe(false)

    it 'should signal :needs-update on first load', ->
      called = false
      animated_tree.observe('needs-update', (() -> called = true))
      add_nodes([[1, [2], 3]])
      expect(called).toBe(true)

    it 'should signal :needs-update on change', ->
      called = false
      add_nodes([[1, [2], 3]])
      animated_tree.observe('needs-update', (() -> called = true))
      add_nodes([[2, [3], 3]])
      expect(called).toBe(true)

    it 'should signal :needs-update on selection change', ->
      Timecop.freeze new Date(100), -> add_nodes([[1, [2], 3]])
      Timecop.freeze new Date(1100), -> animated_tree.update()
      called = false
      animated_tree.observe('needs-update', (() -> called = true))
      state.selection.nodes = [1]
      state._notify('selection-changed', state.selection)
      expect(called).toBe(true)

    it 'should update num_documents in just-loaded nodes', ->
      at 100, -> add_nodes([[1, [2, 3], 3]])
      n2 = animated_tree.root.children[0]
      at 1100, -> animated_tree.update()
      at 2000, -> add_nodes([[2, [4, 5], 2]])
      expect(n2.num_documents.current).toEqual(1.5)
      at 3000, -> animated_tree.update()
      expect(n2.num_documents.current).toEqual(2)

    it 'should update num_documents in siblings of just-loaded nodes', ->
      at 100, -> add_nodes([[1, [2, 3], 3]])
      n3 = animated_tree.root.children[1]
      at 1100, -> animated_tree.update()
      at 2000, -> add_nodes([[2, [4, 5], 2]])
      expect(n3.num_documents.current).toEqual(1.5)
      at 3000, -> animated_tree.update()
      expect(n3.num_documents.current).toEqual(1)
