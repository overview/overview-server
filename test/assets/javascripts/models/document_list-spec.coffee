DocumentList = require('models/document_list').DocumentList

Deferred = jQuery.Deferred

class MockStore
  constructor: (properties={}) ->
    _nodes = (properties.nodes || {})
    _tags = (properties.tags || {})
    _documents = (properties.documents || {})

    @nodes = { get: (id) -> _nodes[id] }
    @tags = { get: (id) -> _tags[id] }
    @documents = { get: (id) -> _documents[id] }

class MockSelection
  constructor: (properties={}) ->
    @nodes = (properties.nodes || {})
    @tags = (properties.tags || {})
    @documents = (properties.documents || {})

class MockResolver
  constructor: () ->
    @deferreds = []

  get_deferred: (key, obj) ->
    @deferreds.push(ret = new Deferred())
    ret

describe 'models/document_list', ->
  describe 'DocumentList', ->
    resolver = undefined

    beforeEach ->
      resolver = new MockResolver()

    describe 'get_placeholder_documents', ->
      it 'should be empty with an empty store and selection', ->
        dl = new DocumentList(new MockStore(), new MockSelection(), resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toBeDefined()
        expect(documents).toEqual([])

      it 'should return empty when nodes are selected but not loaded', ->
        dl = new DocumentList(new MockStore(), { nodes: [1], documents: [], tags: []}, resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual([])

      it 'should return a document when a node is selected', ->
        dl = new DocumentList(new MockStore({
          documents: { 1: 'foo' }
        }), new MockSelection({
          nodes: [{ id: 1, doclist: { docids: [1], n: 1 }}],
        }), resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual(['foo'])

      it 'should return a document when a tag is selected', ->
        dl = new DocumentList(new MockStore({
          documents: { 1: 'foo' },
        }), new MockSelection({
          tags: [{ id: 1, count: 1, doclist: { docids: [1], n: 1 }}],
        }), resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual(['foo'])

      it 'should return no documents when the tag and nodes point to different documents', ->
        dl = new DocumentList(new MockStore({
          documents: { 1: 'foo', 2: 'bar' }
        }), new MockSelection({
          nodes: [{id: 1, doclist: { docids: [1], n: 1 }}],
          tags: [{ id: 1, count: 1, doclist: { docids: [2], n: 1 }}],
        }), resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual([])

      it 'should return a document when both tags and nodes match it', ->
        dl = new DocumentList(new MockStore({
          documents: { 1: 'foo', 2: 'bar' }
        }), new MockSelection({
          nodes: [{id: 1, doclist: { docids: [2], n: 1 }}],
          tags: [{ id: 1, count: 1, doclist: { docids: [2], n: 1 }}],
        }), resolver)
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual(['bar'])

    describe 'slice', ->
      store = new MockStore()

      it 'should return documents it already has, without a server call', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        dl.documents = [ '1', '2', '3', '4', '5' ]
        dl.n = 5
        deferred = dl.slice(0, 3)
        expect(deferred.isResolved()).toBe(true)
        arr = undefined
        deferred.done((x) -> arr = x)
        expect(arr).toEqual(['1', '2', '3'])

      it 'should call Resolver.get_deferred(selection_documents_slice)', ->
        selection = new MockSelection({ documents: [ 1, 2, 3 ] })
        dl = new DocumentList(store, selection, resolver)
        spyOn(resolver, 'get_deferred').andCallThrough()
        deferred = dl.slice(0, 2)
        expect(resolver.get_deferred).toHaveBeenCalledWith('selection_documents_slice', { selection: selection, start: 0, end: 2 })

      it 'should give the return value of get_selection_documents_slice', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        expect(deferred.isResolved()).toBe(false)
        arr = undefined
        deferred.done((x) -> arr = x)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(arr).toEqual(['1', '2'])

      it 'should cache unresolved values', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        d2 = dl.slice(0, 2)
        expect(d2).toBe(d1)

      it 'should cache resolved values', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        d2 = dl.slice(0, 2)
        expect(d2).toBe(d1)

      it 'should populate @documents', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(dl.documents).toEqual(['1', '2'])

      it 'should populate @documents with undefined if a later request comes in before an earlier one', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        d2 = dl.slice(2, 4)
        resolver.deferreds[1].resolve({ documents: [ '3', '4' ] })
        expect(dl.documents).toEqual([undefined, undefined, '3', '4'])
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(dl.documents).toEqual([ '1', '2', '3', '4' ])

      it 'should populate @n', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ], total_items: 2 })
        expect(dl.n).toEqual(2)

      it 'should notify observers', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        called = false
        dl.observe(-> called = true)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ], total_items: 2 })
        expect(called).toBeTruthy()

    describe 'n', ->
      store = new MockStore()

      it 'should begin undefined', ->
        dl = new DocumentList(store, new MockSelection(), resolver)
        expect(dl.n).toBeUndefined()
