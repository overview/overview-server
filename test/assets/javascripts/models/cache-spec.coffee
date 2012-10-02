# This is a heavy test, not a unit test. We stub out when it's simpler.
Cache = require('models/cache').Cache
observable = require('models/observable').observable

Deferred = jQuery.Deferred

class MockServer
  constructor: () ->
    @posts = []
    @deferreds = []

  post: () ->
    @posts.push(Array.prototype.slice.call(arguments, 0))
    deferred = new Deferred()
    @deferreds.push(deferred)
    deferred

describe 'models/cache', ->
  describe 'Cache', ->
    server = undefined
    cache = undefined

    beforeEach ->
      cache = new Cache()
      cache.server = server = new MockServer()

    it 'should have a document_store', ->
      expect(cache.document_store).toBeDefined()

    it 'should have an on_demand_tree', ->
      expect(cache.on_demand_tree).toBeDefined()

    it 'should have a tag_store', ->
      expect(cache.tag_store).toBeDefined()

    describe 'refresh_tagcounts', ->
      tree = undefined

      beforeEach ->
        tree = {
          id_tree: {
            edit: (cb) -> cb(); @edited = true
          }
          nodes: {
            "1": { id: 1, children: [2, 3], tagcounts: { "1": 10 } },
            "2": { id: 2, children: [4, 5], tagcounts: { "1": 5 } },
            "3": { id: 3, children: [6, 7], tagcounts: { "1": 5 } },
          }
        }
        cache.on_demand_tree = tree

      it 'should POST to the tag\'s /node-counts', ->
        cache.refresh_tagcounts({ id: 1, name: 'foo' })
        expect(server.posts[0]).toEqual([ 'tag_node_counts', { nodes: '1,2,3' }, { path_argument: 'foo' } ])

      describe 'When receiving a response', ->
        tag = undefined
        ret = undefined

        beforeEach ->
          tag = { id: 1, name: 'foo' }
          ret = cache.refresh_tagcounts(tag)

        it 'should update node counts', ->
          server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0 ])
          expect(tree.nodes[2].tagcounts[1]).toEqual(20)

        it 'should delete node counts that are 0', ->
          server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0 ])
          expect(tree.nodes[3].tagcounts).toEqual({})

        it 'should zero node counts when the server does not give them', ->
          server.deferreds[0].resolve([ 1, 20 ])
          expect(tree.nodes[2].tagcounts).toEqual({})

        it 'should return the deferred', ->
          server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0 ])
          expect(ret).toBe(server.deferreds[0])

        it 'should not crash when receiving an unloaded node', ->
          server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0, 4, 20 ])

        it 'should call id_tree.edit()', ->
          # After refreshing node counts, we need to redraw. See #128.
          server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0, 4, 20 ])
          expect(cache.on_demand_tree.id_tree.edited).toBe(true)

    describe 'remove_tag', ->
      tree = undefined
      tag = undefined

      beforeEach ->
        tag = { id: 1, name: 'foo', color: '#fabcde' }
        cache.tag_store.add(tag)
        tree = {
          id_tree: {
            edit: (cb) -> cb(); @edited = true
          }
          nodes: {
            "1": { id: 1, children: [2, 3], tagcounts: { "1": 10 } },
            "2": { id: 2, children: [4, 5], tagcounts: { "1": 5 } },
            "3": { id: 3, children: [6, 7], tagcounts: { "1": 5 } },
          }
        }
        cache.on_demand_tree = tree

      it 'should remove_tag from the tag_store', ->
        spyOn(cache.tag_store, 'remove')
        cache.remove_tag(tag)
        expect(cache.tag_store.remove).toHaveBeenCalledWith(tag)

      it 'should call id_tree.edit()', ->
        cache.remove_tag(tag)
        expect(cache.on_demand_tree.id_tree.edited).toBe(true)

      it 'should remove tagcounts for the deleted tag', ->
        cache.remove_tag(tag)
        expect(tree.nodes["1"].tagcounts).toEqual({})
        expect(tree.nodes["2"].tagcounts).toEqual({})
        expect(tree.nodes["3"].tagcounts).toEqual({})
