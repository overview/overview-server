define [
  'jquery'
  'apps/Tree/models/cache'
  'apps/Tree/models/observable'
], ($, Cache, observable) ->
  Deferred = $.Deferred

  class MockTagStore
    observable(this)

    constructor: () ->
      @tags = []

    create_tag: (name) ->
      this.add({ id: -1, name: name, count: 0, color: '#fabcde' })

    add: (tag) ->
      @tags.push(tag)
      tag

    remove: (tag) ->
      @tags.splice(@tags.indexOf(tag), 1)
      tag

    change: (tag, map) ->
      tag[k] = v for k, v of map
      undefined

    find_by_name: (name) ->
      _.find(@tags, (o) -> o.name == name)

  class MockDocumentStore
    constructor: () -> @documents = {}
    remove_tag_id: () ->

  class MockTransactionQueue
    constructor: () ->
      @deferred = undefined
      @callbacks = []

    queue: (callback) ->
      @callbacks.push(callback)

    next: () ->
      @deferred = @callbacks.shift().apply()

  class MockSelection
    observable(this)

    constructor: () ->
      @nodes = []
      @documents = []
      @tags = []

    documents_from_cache: (cache) ->
      []

    allows_correct_tagcount_adjustments: () ->
      !!(@nodes.length && !@documents.length && !@tags.length)

  class MockOnDemandTree
    observable(this)

    constructor: () ->
      @id_tree = { root: -1, children: {}, parent: {} }
      @nodes = {}
      @id_tree = { batchAdd: (cb) -> cb.apply({}) }

    add_tag_to_node: () ->

    remove_tag_from_node: () ->

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
      beforeEach ->
        @cache = new Cache()
        @cache.document_store = new MockDocumentStore()
        @cache.tag_store = new MockTagStore()
        @cache.on_demand_tree = new MockOnDemandTree()
        @cache.transaction_queue = new MockTransactionQueue()
        @cache.server = new MockServer()

      it 'should have a document_store', ->
        expect(@cache.document_store).not.to.be.undefined

      it 'should have an on_demand_tree', ->
        expect(@cache.on_demand_tree).not.to.be.undefined

      it 'should have a tag_store', ->
        expect(@cache.tag_store).not.to.be.undefined

      describe 'refresh_untagged', ->
        tree = undefined

        beforeEach ->
          tree = {
            nodes: {
              "1": { id: 1, tagCounts: { "1": 10 } },
              "2": { id: 2, tagCounts: { "1": 5 } },
              "3": { id: 3, tagCounts: { "1": 5 } },
            }
          }
          @cache.on_demand_tree = tree

        it 'should POST to the untagged /node-counts', ->
          @cache.refresh_untagged()
          @cache.transaction_queue.next()
          expect(@cache.server.posts[0]).to.deep.eq([ 'untagged_node_counts', { nodes: '1,2,3' }, { path_argument: 0 } ])

      describe 'refresh_tagcounts', ->
        tree = undefined

        beforeEach ->
          tree = {
            id_tree: {
              batchAdd: sinon.spy()
              batchRemove: sinon.spy()
            }
            nodes: {
              "1": { id: 1, tagCounts: { "1": 10 } },
              "2": { id: 2, tagCounts: { "1": 5 } },
              "3": { id: 3, tagCounts: { "1": 5 } },
            }
          }
          @cache.on_demand_tree = tree

        it 'should POST to the tag\'s /node-counts', ->
          @cache.refresh_tagcounts({ id: 1, name: 'foo' })
          @cache.transaction_queue.next()
          expect(@cache.server.posts[0]).to.deep.eq([ 'tag_node_counts', { nodes: '1,2,3' }, { path_argument: 1 } ])

        describe 'when receiving a response', ->
          tag = undefined

          beforeEach ->
            tag = { id: 1, name: 'foo' }
            @cache.refresh_tagcounts(tag)
            @cache.transaction_queue.next()
            undefined # avoid mocha-as-promised

          it 'should update node counts', ->
            @cache.server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0 ])
            expect(tree.nodes[2].tagCounts[1]).to.eq(20)

          it 'should delete node counts that are 0', ->
            @cache.server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0 ])
            expect(tree.nodes[3].tagCounts).to.deep.eq({})

          it 'should not crash when receiving an unloaded node', ->
            @cache.server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0, 4, 20 ])

          it 'should call id_tree.batchAdd()', ->
            # After refreshing node counts, we need to redraw. See #128.
            @cache.server.deferreds[0].resolve([ 1, 20, 2, 20, 3, 0, 4, 20 ])
            expect(@cache.on_demand_tree.id_tree.batchAdd).to.have.been.called

      describe 'with a node', ->
        node = undefined
        tree = undefined

        beforeEach ->
          node = { id: 1, description: "description" }
          tree = {
            id_tree: {
              batchAdd: (cb) -> cb(); @edited = true
            }
            nodes: { "1": node }
          }
          @cache.on_demand_tree = tree

        describe 'edit_node', ->
          beforeEach ->
            @cache.edit_node(node, { id: 1, description: 'description 2' })

          it 'should update the node', ->
            expect(node.description).to.eq('description 2')

          it 'should mark the tree as edited', ->
            expect(tree.id_tree.edited).to.be(true)

        describe 'update_node', ->
          beforeEach ->
            @sandbox = sinon.sandbox.create()
            @sandbox.spy(@cache, 'edit_node')
            @cache.update_node(node, { id: 1, description: 'description 2' })
            undefined # avoid mocha-as-promised

          afterEach ->
            @sandbox.restore()

          it 'should call edit_node', ->
            expect(@cache.edit_node).to.have.been.calledWith(node, sinon.match(id: node.id, description: 'description 2'))

          it 'should queue a server call', ->
            expect(@cache.transaction_queue.callbacks.length).to.eq(1)

          describe 'after making the server call', ->
            post = undefined

            beforeEach ->
              @cache.transaction_queue.next()
              post = @cache.server.posts[0]
              undefined # avoid mocha-as-promised

            afterEach ->
              post = undefined

            it 'should have POSTed', ->
              expect(post).not.to.be.undefined

            it 'should post to node_update with the node ID', ->
              expect(post[0]).to.eq('node_update')
              expect(post[2].path_argument).to.eq(1)

      describe 'with a tag', ->
        tree = undefined
        tag = undefined

        beforeEach ->
          tag = { id: 1, name: 'AA', color: '#fabcde' }
          @cache.tag_store.add(tag)
          tree = {
            id_tree: {
              batchAdd: (cb) -> cb(); @edited = true
            }
            nodes: {
              "1": { id: 1, tagCounts: { "1": 10 } },
              "2": { id: 2, tagCounts: { "1": 5 } },
              "3": { id: 3, tagCounts: { "1": 5 } },
            }
          }
          @cache.on_demand_tree = tree

        describe 'edit_tag', ->
          beforeEach ->
            @cache.edit_tag(tag, { id: tag.id, name: 'foo', color: '#654321' })

          it 'should set the tag name and color', ->
            expect(tag.name).to.eq('foo')
            expect(tag.color).to.eq('#654321')

        describe 'remove_tag', ->
          it 'should remove_tag from the tag_store', ->
            @cache.tag_store.remove = sinon.stub()
            @cache.remove_tag(tag)
            expect(@cache.tag_store.remove).to.have.been.calledWith(tag)

          it 'should remove_tag_id from the document_store', ->
            @cache.document_store.remove_tag_id = sinon.stub()
            @cache.remove_tag(tag)
            expect(@cache.document_store.remove_tag_id).to.have.been.calledWith(tag.id)

          it 'should call id_tree.batchAdd()', ->
            @cache.remove_tag(tag)
            expect(@cache.on_demand_tree.id_tree.edited).to.be(true)

          it 'should remove tagCounts for the deleted tag', ->
            @cache.remove_tag(tag)
            expect(tree.nodes["1"].tagCounts).to.deep.eq({})
            expect(tree.nodes["2"].tagCounts).to.deep.eq({})
            expect(tree.nodes["3"].tagCounts).to.deep.eq({})

        describe 'delete_tag', ->
          deferred = undefined

          beforeEach ->
            deferred = $.Deferred()
            @cache.tag_api =
              destroy: sinon.stub().returns(deferred)

          it 'should call tag_api.destroy(tag)', ->
            @cache.delete_tag(tag)
            expect(@cache.tag_api.destroy).to.have.been.calledWith(tag)

          it 'should not remove the tag from the store yet', ->
            # Why not? Because we can run into problems like this:
            # 1. Delete tag (and remove it from store)
            # 2. In comes a previously-requested doclist from the server
            # 3. When rendering the doclist, we have a missing tag
            # 4. Tag gets deleted from the server
            #
            # This is one of a zillion problems. We may wish to add a "deleted"
            # flag to tags, but we can't delete them immediately.
            @cache.tag_store.remove = sinon.stub()
            @cache.delete_tag(tag)
            expect(@cache.tag_store.remove).not.to.have.been.called

          it 'should remove the tag from the store when destroying is finished', ->
            @cache.delete_tag(tag)
            @cache.tag_store.remove = sinon.stub()
            deferred.resolve()
            expect(@cache.tag_store.remove).to.have.been.calledWith(tag)
