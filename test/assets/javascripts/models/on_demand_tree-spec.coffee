OnDemandTree = require('models/on_demand_tree').OnDemandTree

Deferred = jQuery.Deferred

class MockResolver
  constructor: () ->
    @deferreds = []

  get_deferred: (@type, @id) ->
    @deferreds.push(ret = new Deferred())
    ret

describe 'models/on_demand_tree', ->
  describe 'OnDemandTree', ->
    resolver = undefined
    tree = undefined

    create_tree = (cache_size) ->
      tree = new OnDemandTree(resolver, { cache_size: cache_size })

    add_nodes_through_deferred = (nodes) ->
      deferred = tree.demand_node(nodes[0].id)
      deferred.resolve({ nodes: nodes })

    add_node_through_deferred = (id, children) ->
      add_nodes_through_deferred([ { id: id, children: children } ])

    create_listen_object = () ->
      ret = { add: [], remove: [], remove_undefined: [], root: [], }

      tree.id_tree.observe('add', (ids) -> ret.add.push(ids))
      tree.id_tree.observe('remove', (ids) -> ret.remove.push(ids))
      tree.id_tree.observe('remove-undefined', (ids) -> ret.remove_undefined.push(ids))
      tree.id_tree.observe('root', (root) -> ret.root.push(root))

      ret

    beforeEach ->
      resolver = new MockResolver()

    describe 'starting with an empty tree', ->
      beforeEach ->
        create_tree(5)

      it 'should start with id_tree empty', ->
        expect(tree.id_tree.root).toEqual(-1)

      it 'should demand_root() and call get_deferred("root")', ->
        deferred = tree.demand_root()
        expect(resolver.type).toEqual('root')
        expect(resolver.id).toBeUndefined()
        expect(deferred.done).toBeDefined()

      it 'should add results to the tree', ->
        add_node_through_deferred(1, [2, 3])
        expect(tree.id_tree.root).toEqual(1)
        expect(tree.id_tree.children[1]).toEqual([2, 3])

    describe 'with a non-empty tree', ->
      beforeEach ->
        create_tree(5)
        add_nodes_through_deferred([
          { id: 1, children: [ 2, 3 ] },
          { id: 2, children: [ 4, 5 ] },
          { id: 3, children: [ 6, 7 ] },
        ])

      it 'should get node objects from ids', ->
        expect(tree.nodes[1]).toEqual({ id: 1, children: [ 2, 3 ] })

      it 'should not get unresolved-node objects', ->
        expect(tree.nodes[4]).toBeUndefined()

      it 'should allow demand_node() on unresolved nodes', ->
        deferred = tree.demand_node(4)
        expect(resolver.type).toEqual('node')
        expect(resolver.id).toEqual(4)

      it 'should allow demand_node() on resolved nodes', ->
        deferred = tree.demand_node(1)
        expect(resolver.type).toEqual('node')
        expect(resolver.id).toEqual(1)

      it 'should add nodes added through demand_node()', ->
        deferred = tree.demand_node(4)
        deferred.resolve({ nodes: [ { id: 4, children: [ 8, 9 ] } ] })
        expect(tree.nodes[4].children).toEqual([8, 9])

    describe 'with a full tree', ->
      beforeEach ->
        create_tree(1+3+9+27)
        # A full tree, three children per parent, with sequential IDs
        id_to_stub_node = (id) ->
          { id: id, children: [ id*3-1, id*3, id*3+1 ] }

        add_nodes_through_deferred(id_to_stub_node(id) for id in [ 1..1+3+9+27 ])

      it 'should remove a node while adding a new node', ->
        o = create_listen_object()
        add_node_through_deferred(41, [99, 100, 101])
        expect(o.add).toEqual([[41]])
        expect(o.remove.length).toEqual(1)
        expect(o.remove[0].length).toEqual(1)
        expect(o.remove_undefined.length).toEqual(1)
        expect(o.remove_undefined[0].length).toEqual(3)

      it 'should not remove an important node when adding a new node', ->
        o = create_listen_object()
        add_node_through_deferred(41, [99, 100, 101])
        r = o.remove[0][0]
        expect(r).toNotEqual(14) # parent
        expect(r).toNotEqual(15) # uncle
        expect(r).toNotEqual(16) # uncle
        expect(r).toNotEqual(5) # grandparent
        expect(r).toNotEqual(6) # great-uncle
        expect(r).toNotEqual(7) # great-uncle
        expect(r).toNotEqual(2)
        expect(r).toNotEqual(3)
        expect(r).toNotEqual(4)
        expect(r).toNotEqual(1) # root

      it 'should throw AllPagesFrozen if the addition will fail', ->
        stub_node = (id) -> { id: id, children: [ id + 1 ] }
        new_nodes = [ { id: 41, children: [1000] } ].concat(stub_node(id) for id in [ 1000 .. 1040 ])
        expect(-> add_nodes_through_deferred(new_nodes)).toThrow('AllPagesFrozen')

      it 'should add as many nodes as possible without throwing AllPagesFrozen', ->
        stub_node = (id) -> { id: id, children: [ id + 1 ] }
        # Tree size is 1+3+9+27 = 40 nodes
        # Of those, there will be 10 uncles/parents/roots of node #41
        # So we should be able to add exactly 30 nodes
        new_nodes = [ { id: 41, children: [1000] } ].concat(stub_node(id) for id in [ 1000 .. 1028 ])
        expect(new_nodes.length).toEqual(30) # assertion
        add_nodes_through_deferred(new_nodes) # throws error on failure
