RemoteTagList = require('models/remote_tag_list').RemoteTagList
observable = require('models/observable').observable

Deferred = jQuery.Deferred

class MockTagStore
  observable(this)

  constructor: () ->
    @tags = []

  add: (tag) ->
    @tags.push(tag)
    tag

  remove: (tag) ->
    @tags.remove(@tags.indexOf(tag))
    tag

  change: (tag, map) ->
    tag[k] = v for k, v of map
    undefined

class MockDocumentStore
  constructor: () ->
    @documents = {}
    @added_doclists = []
    @removed_doclists = []

  add_doclist: (doclist, documents) ->
    @documents = _.union(@documents, documents)
    @added_doclists.push([ doclist, documents ])
    undefined

  remove_doclist: (doclist) ->
    @removed_doclists.push(doclist)

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

class MockOnDemandTree
  observable(this)

  constructor: () ->
    @id_tree = { root: -1, children: {}, parent: {} }
    @nodes = {}

class MockServer
  constructor: () ->
    @posts = []
    @deferreds = []

  post: () ->
    @posts.push(Array.prototype.slice.call(arguments, 0))
    deferred = new Deferred()
    @deferreds.push(deferred)
    deferred

describe 'models/remote_tag_list', ->
  describe 'RemoteTagList', ->
    tag_store = undefined
    on_demand_tree = undefined
    document_store = undefined
    selection = undefined
    server = undefined
    transaction_queue = undefined
    remote_tag_list = undefined

    dummy_tag = (id, name, doclist=undefined) ->
      { id: id, name: name, doclist: doclist? && _.clone(doclist) || { n: 0, docids: [] } }

    beforeEach ->
      tag_store = new MockTagStore()
      selection = new MockSelection()
      on_demand_tree = new MockOnDemandTree()
      document_store = new MockDocumentStore()
      transaction_queue = new MockTransactionQueue()
      server = new MockServer()

    afterEach ->
      tag_store = undefined
      on_demand_tree = undefined
      document_store = undefined
      selection = undefined
      server = undefined
      transaction_queue = undefined
      remote_tag_list = undefined

    describe 'with some default tags', ->
      beforeEach ->
        tag_store.tags = [ dummy_tag(1, 'AA'), dummy_tag(2, 'BB') ]
        remote_tag_list = new RemoteTagList(tag_store, on_demand_tree, document_store, transaction_queue, server)

      it 'should pass through :tag-added from the TagStore', ->
        expected = { position: 1, tag: dummy_tag(3, 'CC') }
        actual = undefined
        remote_tag_list.observe('tag-added', (v) -> actual = v)
        tag_store._notify('tag-added', expected)
        expect(actual).toBe(expected)

      it 'should pass through :tag-removed from the TagStore', ->
        expected = { position: 1, tag: tag_store.tags[1] }
        actual = undefined
        remote_tag_list.observe('tag-removed', (v) -> actual = v)
        tag_store._notify('tag-removed', expected)
        expect(actual).toBe(expected)

      it 'should pass through :tag-changed from the TagStore', ->
        expected = tag_store.tags[1]
        actual = undefined
        remote_tag_list.observe('tag-changed', (v) -> actual = v)
        tag_store._notify('tag-changed', expected)
        expect(actual).toBe(expected)

      it 'should mirror TagStore.tags', ->
        tag_store.tags.splice(1, 1)
        expect(remote_tag_list.tags).toBe(tag_store.tags)

      it 'should create_tag() and return a tag', ->
        tag = remote_tag_list.create_tag('foo')
        expect(tag).toEqual({ id: undefined, name: 'foo', count: 0 })

      it 'should send nothing to the server in create_tag()', ->
        tag = remote_tag_list.create_tag('foo')
        expect(transaction_queue.callbacks.length).toEqual(0)

      it 'should add to the TagStore in create_tag()', ->
        tag = remote_tag_list.create_tag('ZZ')
        expect(remote_tag_list.tags[2]).toEqual({ id: undefined, name: 'ZZ', count: 0 })

      describe 'with a partial tree and documents', ->
        beforeEach ->
          on_demand_tree.id_tree.root = 1
          on_demand_tree.id_tree.children = { 1: [2, 3], 2: [4, 5], 3: [6, 7], 4: [8], 5: [9] }
          on_demand_tree.id_tree.parent = { 2: 1, 3: 1, 4: 2, 5: 2, 6: 3, 7: 3, 8: 4, 9: 5 }
          on_demand_tree.nodes = {
            1: { id: 1, doclist: { n: 15, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] } }, # 1-15
            2: { id: 2, doclist: { n: 13, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] } }, # 1-13
            3: { id: 3, doclist: { n: 2, docids: [ 14, 15 ] } },                         # 14-15
            4: { id: 4, doclist: { n: 12, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] } }, # 1-12
            5: { id: 5, doclist: { n: 1, docids: [ 13 ] } },                             # 13
          }
          document_store.documents = {
            1: { id: 1, title: "doc1", tagids: [ 1 ] },
            2: { id: 2, title: "doc2", tagids: [ 2 ] },
            3: { id: 3, title: "doc3", tagids: [ 1 ] },
            4: { id: 4, title: "doc4", tagids: [ 2 ] },
            5: { id: 5, title: "doc5", tagids: [ 1 ] },
            6: { id: 6, title: "doc6", tagids: [] },
            7: { id: 7, title: "doc7", tagids: [ 1, 2 ] },
            8: { id: 8, title: "doc8", tagids: [ 2 ] },
            9: { id: 9, title: "doc9", tagids: [ 1 ] },
            10: { id: 10, title: "doc10", tagids: [ 2 ] },
            # 11 and 12 are untagged and not visible in any preloaded node lists
            13: { id: 13, title: "doc13", tagids: [ 2 ] },
            14: { id: 14, title: "doc14", tagids: [ 1 ] },
            15: { id: 15, title: "doc15", tagids: [ 2 ] },
          }
          tag_store.tags[0].doclist = { n: 6, docids: [ 1, 3, 5, 7, 9, 14 ] }
          tag_store.tags[1].doclist = { n: 7, docids: [ 2, 4, 7, 8, 10, 13, 15 ] }

        describe 'when selection is empty', ->
          it 'should not add the tag to any documents', ->
            tag = remote_tag_list.tags[0]
            remote_tag_list.add_tag_to_selection(tag, selection)
            expect(document_store.documents[2].tagids).toNotContain(1)

          it 'should not remove the tag from any documents', ->
            tag = remote_tag_list.tags[1]
            remote_tag_list.remove_tag_from_selection(tag, selection)
            expect(document_store.documents[2].tagids).toContain(2)

        describe 'after applying a tag to a node', ->
          beforeEach ->
            tag = remote_tag_list.tags[0]
            selection.nodes = [2]
            remote_tag_list.add_tag_to_selection(tag, selection)

          it 'should apply the tag to documents in that node\'s doclist', ->
            expect(document_store.documents[2].tagids).toContain(1)

          it 'should apply the tag to documents in that node\'s children\'s doclists', ->
            expect(document_store.documents[13].tagids).toContain(1)

          it 'should not apply the tag to other documents', ->
            expect(document_store.documents[15].tagids).not.toContain(1)

          it 'should not duplicate a tag on a document', ->
            expect(document_store.documents[1].tagids).toEqual([1])

          it 'should unset the tag\'s doclist', ->
            expect(tag_store.tags[0].doclist).toBeUndefined()

          it 'should remove the doclist from the DocumentStore', ->
            expect(document_store.removed_doclists[0]).toEqual({ n: 6, docids: [ 1, 3, 5, 7, 9, 14 ] })

          it 'should queue a server call', ->
            expect(transaction_queue.callbacks.length).toEqual(1)

          describe 'after making the server call', ->
            post = undefined

            beforeEach ->
              transaction_queue.next()
              post = server.posts[0]

            afterEach ->
              post = undefined

            it 'should have POSTed', ->
              expect(post).toBeDefined()

            it 'should post to tag_remove with tag name', ->
              expect(post[0]).toEqual('tag_add')
              expect(post[2].path_argument).toEqual('AA')

            it 'should post the proper selection', ->
              expect(post[1]).toEqual({ nodes: '2', tags: '', documents: '' })

            describe 'and receiving success', ->
              new_doclist = { n: 14, docids: [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ] }
              new_documents = {
                1: { id: 1, title: "doc1", tagids: [ 1 ] },
                2: { id: 2, title: "doc2", tagids: [ 1, 2 ] },
                3: { id: 3, title: "doc3", tagids: [ 1 ] },
                4: { id: 4, title: "doc4", tagids: [ 1, 2 ] },
                5: { id: 5, title: "doc5", tagids: [ 1 ] },
                6: { id: 6, title: "doc6", tagids: [ 1 ] },
                7: { id: 7, title: "doc7", tagids: [ 1, 2 ] },
                8: { id: 8, title: "doc8", tagids: [ 1, 2 ] },
                9: { id: 9, title: "doc9", tagids: [ 1 ] },
                10: { id: 10, title: "doc10", tagids: [ 1, 2 ] },
              }

              beforeEach ->
                server.deferreds[0].resolve({
                  num_added: 8,
                  tag: { id: 1, doclist: new_doclist },
                  documents: new_documents,
                })

              it 'should set the new tag count', ->
                expect(remote_tag_list.tags[0].doclist.n).toEqual(14)

              it 'should add the new doclist', ->
                expect(document_store.added_doclists[0]).toEqual([new_doclist, new_documents])

          it 'should make the server call with the selection given at call time, even if it has changed since', ->
            selection.tags.push(1)

            transaction_queue.next()
            expect(server.posts[0][1]).toEqual({ nodes: '2', tags: '', documents: '' })

          it 'should call with a new tag name, if the name changed since', ->
            remote_tag_list.tags[0].name = 'AA-changed'
            transaction_queue.next()
            expect(server.posts[0][2].path_argument).toEqual('AA-changed')

        describe 'after removing a tag from a node', ->
          beforeEach ->
            tag = remote_tag_list.tags[1]
            selection.nodes = [2]
            remote_tag_list.remove_tag_from_selection(tag, selection)

          it 'should remove the tag from documents in that node\'s doclist', ->
            expect(document_store.documents[2].tagids).not.toContain(2)

          it 'should remove the tag from documents in that node\'s children\'s doclists', ->
            expect(document_store.documents[13].tagids).not.toContain(2)

          it 'should not affect documents that do not include the tag', ->
            expect(document_store.documents[3].tagids).toEqual([1])

          it 'should not remove the tag from other documents', ->
            expect(document_store.documents[15].tagids).toContain(2)

          it 'should remove the tag\'s doclist from the DocumentStore', ->
            expect(document_store.removed_doclists[0]).toEqual({ n: 7, docids: [ 2, 4, 7, 8, 10, 13, 15 ] })

          it 'should unset the tag\'s doclist', ->
            expect(tag_store.tags[1].doclist).toBeUndefined()

          it 'should queue a server call', ->
            expect(transaction_queue.callbacks.length).toEqual(1)

          describe 'after making the server call', ->
            post = undefined

            beforeEach ->
              transaction_queue.next()
              post = server.posts[0]

            afterEach ->
              post = undefined

            it 'should have POSTed', ->
              expect(post).toBeDefined()

            it 'should post to tag_remove with tag name', ->
              expect(post[0]).toEqual('tag_remove')
              expect(post[2].path_argument).toEqual('BB')

            it 'should post the proper selection', ->
              expect(post[1]).toEqual({ nodes: '2', tags: '', documents: '' })

            describe 'and receiving success', ->
              new_doclist = { n: 1, docids: [ 15 ] }
              new_documents = {
                15: { id: 15, title: "doc15", tagids: [ 1, 2 ] },
              }

              beforeEach ->
                server.deferreds[0].resolve({
                  num_removed: 6,
                  tag: { id: 2, doclist: new_doclist },
                  documents: new_documents,
                })

              it 'should set the new tag count', ->
                expect(remote_tag_list.tags[1].doclist.n).toEqual(1)

              it 'should add the new doclist', ->
                expect(document_store.added_doclists[0]).toEqual([ new_doclist, new_documents ])
