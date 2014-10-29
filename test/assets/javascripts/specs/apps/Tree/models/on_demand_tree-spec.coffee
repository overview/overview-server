define [
  'jquery'
  'backbone'
  'apps/Tree/models/on_demand_tree'
], ($, Backbone, OnDemandTree) ->
  class DocumentSet extends Backbone.Model

  class View extends Backbone.Model

  class TransactionQueue
    ajax: (options) -> $.ajax(options?() || options)

  describe 'models/on_demand_tree', ->
    sandbox = null
    tree = null

    beforeEach ->
      sandbox = sinon.sandbox.create(useFakeServer: true)
      @documentSet = new DocumentSet(id: 1)
      @view = new View(id: 2)
      @documentSet.transactionQueue = new TransactionQueue()

    afterEach ->
      tree = null
      sandbox.restore()
      sandbox = null

    respondWithJson = (json) ->
      reqs = sandbox.server.requests
      req = reqs[reqs.length - 1]
      req.respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(json))

    add_nodes_through_deferred = (nodes) ->
      tree.demandNode(nodes[0].id)
      respondWithJson(nodes: nodes)

    # HACK to convert from old-style IdTree to new-style
    # Before, each node would have a "children" property; now it does not.
    # There are so many reasons this is bad and should be removed... :)
    add_node_through_deferred = (parentId, childIds) ->
      tree.demandNode(parentId)
      childNodes = ({ id: id, parentId: parentId } for id in childIds)
      childNodes.unshift(tree.getNode(parentId)) if parentId isnt null
      respondWithJson(nodes: childNodes)

    describe 'starting with an empty tree', ->
      beforeEach ->
        tree = new OnDemandTree(@documentSet, @view, cache_size: 5)

      it 'should start with id_tree empty', ->
        expect(tree.id_tree.root).to.eq(null)

      it 'should demandRoot()', ->
        deferred = tree.demandRoot()
        expect(sandbox.server.requests[0].url).to.eq('/trees/2/nodes.json')
        expect(deferred.done).not.to.be.undefined

      it 'should add results to the tree', ->
        add_node_through_deferred(null, [1])
        add_node_through_deferred(1, [2, 3])
        expect(tree.id_tree.root).to.eq(1)
        expect(tree.id_tree.children[1]).to.deep.eq([2, 3])

    describe 'with a non-empty tree', ->
      beforeEach ->
        tree = new OnDemandTree(@documentSet, @view, cache_size: 10)
        add_nodes_through_deferred([
          { id: 1, parentId: null, size: 50 }
          { id: 2, parentId: 1, size: 30 }
          { id: 3, parentId: 1, size: 20 }
          { id: 4, parentId: 2, size: 14 }
          { id: 5, parentId: 2, size: 16 }
        ])

      it 'should get node objects from ids', ->
        node = tree.getNode(1)
        expect(node.id).to.eq(1)
        expect(node.parentId).to.be.null
        expect(node.size).to.eq(50)

      it 'should not get unresolved node objects', ->
        expect(tree.getNode(20)).to.be.undefined

      it 'should allow demandNode() on unresolved nodes', ->
        deferred = tree.demandNode(4)
        expect(sandbox.server.requests[sandbox.server.requests.length - 1].url).to.eq('/trees/2/nodes/4.json')

      it 'should allow demandNode() on resolved nodes', ->
        deferred = tree.demandNode(1)
        expect(sandbox.server.requests[sandbox.server.requests.length - 1].url).to.eq('/trees/2/nodes/1.json')

      it 'should add nodes added through demandNode()', ->
        tree.demandNode(4)
        respondWithJson({ nodes: [
          id: 6, parentId: 4, size: 10
          id: 7, parentId: 4, size: 2
        ]})
        expect(tree.getNode(7)).not.to.be.undefined

      it 'should not add child nodes if their parent disappeared before they were resolved', ->
        # GitHub: https://github.com/overview/overview-server/issues/222
        tree.demandNode(4)
        tree.unloadNodeChildren(1)
        respondWithJson({ nodes: [
          id: 6, parentId: 4, size: 10
          id: 7, parentId: 4, size: 2
        ]})
        expect(tree.getNode(7)).to.be.undefined

      it 'should unload nodes through unloadNodeChildren()', ->
        tree.unloadNodeChildren(1)
        expect(tree.id_tree.children[1]).to.be.undefined
        expect(tree.nodes[2]).to.be.undefined

    describe 'with a full tree', ->
      beforeEach ->
        cacheSize = 1 + 3 + 9 + 27 + 81
        tree = new OnDemandTree(@documentSet, @view, cache_size: cacheSize)
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
