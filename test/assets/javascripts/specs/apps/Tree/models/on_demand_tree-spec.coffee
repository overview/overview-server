define [
  'jquery'
  'apps/Tree/models/on_demand_tree'
], ($, OnDemandTree) ->
  class MockDocumentStore
    constructor: () ->
      @documents = {}

  class MockCache
    constructor: () ->
      @deferreds = []

    resolve_deferred: (@type, @id) ->
      @deferreds.push(ret = new $.Deferred())
      ret

  describe 'models/on_demand_tree', ->
    describe 'OnDemandTree', ->
      cache = undefined
      tree = undefined

      create_tree = (cache_size) ->
        tree = new OnDemandTree(cache, { cache_size: cache_size })

      add_nodes_through_deferred = (nodes) ->
        deferred = tree.demand_node(nodes[0].id)
        deferred.resolve({ nodes: nodes })

      # HACK to convert from old-style IdTree to new-style
      # Before, each node would have a "children" property; now it does not.
      # There are so many reasons this is bad and should be removed... :)
      add_node_through_deferred = (parentId, childIds) ->
        deferred = tree.demand_node(parentId)
        childNodes = ({ id: id, parentId: parentId } for id in childIds)
        childNodes.unshift(tree.getNode(parentId)) if parentId isnt null
        deferred.resolve({ nodes: childNodes })

      beforeEach ->
        cache = new MockCache()

      describe 'starting with an empty tree', ->
        beforeEach ->
          create_tree(5)

        it 'should start with id_tree empty', ->
          expect(tree.id_tree.root).to.eq(null)

        it 'should demand_root() and call resolve_deferred("root")', ->
          deferred = tree.demand_root()
          expect(cache.type).to.eq('root')
          expect(cache.id).to.be.undefined
          expect(deferred.done).not.to.be.undefined

        it 'should add results to the tree', ->
          add_node_through_deferred(null, [1])
          add_node_through_deferred(1, [2, 3])
          expect(tree.id_tree.root).to.eq(1)
          expect(tree.id_tree.children[1]).to.deep.eq([2, 3])

      describe 'with a non-empty tree', ->
        beforeEach ->
          create_tree(10)
          add_nodes_through_deferred([
            { id: 1, parentId: null, size: 50 }
            { id: 2, parentId: 1, size: 30 }
            { id: 3, parentId: 1, size: 20 }
            { id: 4, parentId: 2, size: 14 }
            { id: 5, parentId: 2, size: 16 }
          ])

        it 'should get node objects from ids', ->
          expect(tree.getNode(1)).to.deep.eq({ id: 1, parentId: null, size: 50 })

        it 'should not get unresolved node objects', ->
          expect(tree.getNode(20)).to.be.undefined

        it 'should rewrite a tag id', ->
          tree.nodes[1].tagCounts = { "1": 20, "2": 10 }
          tree.rewrite_tag_id(2, 7)
          expect(tree.nodes[1].tagCounts).to.deep.eq({ "1": 20, "7": 10 })

        it 'should allow demand_node() on unresolved nodes', ->
          deferred = tree.demand_node(4)
          expect(cache.type).to.eq('node')
          expect(cache.id).to.eq(4)

        it 'should allow demand_node() on resolved nodes', ->
          deferred = tree.demand_node(1)
          expect(cache.type).to.eq('node')
          expect(cache.id).to.eq(1)

        it 'should add nodes added through demand_node()', ->
          deferred = tree.demand_node(4)
          deferred.resolve({ nodes: [
            id: 6, parentId: 4, size: 10
            id: 7, parentId: 4, size: 2
          ]})
          expect(tree.getNode(7)).not.to.be.undefined

        it 'should not add child nodes if their parent disappeared before they were resolved', ->
          # GitHub: https://github.com/overview/overview-server/issues/222
          deferred = tree.demand_node(4)
          tree.unload_node_children(1)
          deferred.resolve({ nodes: [
            id: 6, parentId: 4, size: 10
            id: 7, parentId: 4, size: 2
          ]})
          expect(tree.getNode(7)).to.be.undefined
          expect(tree.nodes).to.deep.eq({
            1: { id: 1, parentId: null, size: 50 }
          })

        it 'should unload nodes through unload_node_children()', ->
          tree.unload_node_children(1)
          expect(tree.id_tree.children[1]).to.be.undefined
          expect(tree.nodes[2]).to.be.undefined

      describe 'with a full tree', ->
        beforeEach ->
          create_tree(1+3+9+27+81)
          # A full tree, three children per parent, with sequential IDs
          id_to_stub_node = (id) ->
            parentId = Math.floor((id + 1) / 3)
            parentId = null if parentId < 1
            { id: id, parentId: parentId }

          add_nodes_through_deferred(id_to_stub_node(id) for id in [ 1..1+3+9+27+81 ])

        it 'should collapse a node while adding new nodes', ->
          spy = sinon.spy()
          tree.id_tree.observe('change', spy)
          add_node_through_deferred(120, [124, 125, 126])
          expect(spy.firstCall.args[0]).to.deep.eq({ added: [ 124, 125, 126 ] })
          expect(spy.secondCall.args[0].removed.length).to.be.greaterThan(2)

        it 'should not remove an important node when adding a new node', ->
          spy = sinon.spy()
          tree.id_tree.observe('change', spy)
          add_node_through_deferred(120, [124, 125, 126])
          r = spy.secondCall.args[0].removed[0]
          expect(r).not.to.eq(14) # parent
          expect(r).not.to.eq(15) # uncle
          expect(r).not.to.eq(16) # uncle
          expect(r).not.to.eq(5) # grandparent
          expect(r).not.to.eq(6) # great-uncle
          expect(r).not.to.eq(7) # great-uncle
          expect(r).not.to.eq(2)
          expect(r).not.to.eq(3)
          expect(r).not.to.eq(4)
          expect(r).not.to.eq(1) # root

        it 'should add as many nodes as possible without throwing AllPagesFrozen', ->
          # Tree size is 1+3+9+27+81 = 121 nodes
          # For node '121', there will be 1+3+3+3+3=13 uncles/ancestors/self
          # So we should be able to add 121-13=108 children
          nodes = ({ parentId: 121, id: 1000 + i } for i in [ 0 ... 108 ])
          nodes.unshift(tree.getNode(121))
          add_nodes_through_deferred(nodes) # throws error on failure

        it 'should throw AllPagesFrozen if the addition will fail', ->
          nodes = ({ parentId: 121, id: 1000 + i } for i in [ 0 ... 109 ])
          nodes.unshift(tree.getNode(121))
          expect(-> add_nodes_through_deferred(nodes)).to.throw('AllPagesFrozen')
