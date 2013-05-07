require [
  'jquery'
  'apps/Tree/models/document_list'
], ($, DocumentList) ->
  class MockSelection
    constructor: (properties={}) ->
      @nodes = (properties.nodes || {})
      @tags = (properties.tags || {})
      @documents = (properties.documents || {})

    documents_from_cache: (cache) -> []

  class MockDocumentStore
    constructor: () ->
      @adds = []
      @removes = []
      @documents = {}

    add_doclist: (doclist, documents) ->
      @adds.push({ doclist: doclist, documents: documents })
      for docid in doclist.docids
        @documents[docid] = documents[docid]
      undefined

    remove_doclist: (doclist) ->
      @removes.push(doclist)

  class MockCache
    constructor: () ->
      @document_store = new MockDocumentStore()
      @deferreds = []

    resolve_deferred: (key, obj) ->
      @deferreds.push(ret = new $.Deferred())
      ret

  describe 'models/document_list', ->
    describe 'DocumentList', ->
      doc = (i) -> { id: i, title: "doc#{i}" }
      doclist = (start, end) ->
        {
          documents: doc(i) for i in [ start ... end ],
          total_items: end - start
        }

      cache = undefined
      document_store = undefined

      beforeEach ->
        cache = new MockCache()
        document_store = cache.document_store

      it 'should pass get_placeholder_documents() to selection.documents_from_cache()', ->
        selection = new MockSelection()
        dl = new DocumentList(cache, selection)
        spyOn(selection, 'documents_from_cache')
        dl.get_placeholder_documents()
        expect(selection.documents_from_cache).toHaveBeenCalledWith(cache)

      describe 'slice', ->
        it 'should return documents it already has without a server call', ->
          dl = new DocumentList(cache, new MockSelection())
          dl.documents = doclist(1, 6).documents
          dl.n = 5
          deferred = dl.slice(0, 3)
          expect(deferred.state()).toEqual('resolved')
          arr = undefined
          deferred.done((x) -> arr = x)
          expect(arr).toEqual(doclist(1, 4).documents)

        it 'should call Cache.resolve_deferred(selection_documents_slice)', ->
          selection = new MockSelection({ documents: [ 1, 2, 3 ] })
          dl = new DocumentList(cache, selection)
          spyOn(cache, 'resolve_deferred').andCallThrough()
          deferred = dl.slice(0, 2)
          expect(cache.resolve_deferred).toHaveBeenCalledWith('selection_documents_slice', { selection: selection, start: 0, end: 2 })

        it 'should give the return value of get_selection_documents_slice', ->
          dl = new DocumentList(cache, new MockSelection())
          deferred = dl.slice(0, 2)
          expect(deferred.state()).toEqual('pending')
          arr = undefined
          deferred.done((x) -> arr = x)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(arr).toEqual(doclist(1,3).documents)

        it 'should cache unresolved values', ->
          dl = new DocumentList(cache, new MockSelection())
          d1 = dl.slice(0, 2)
          d2 = dl.slice(0, 2)
          expect(d2).toBe(d1)

        it 'should cache resolved values', ->
          dl = new DocumentList(cache, new MockSelection())
          d1 = dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          d2 = dl.slice(0, 2)
          expect(d2).toBe(d1)

        it 'should populate @documents', ->
          dl = new DocumentList(cache, new MockSelection())
          deferred = dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.documents).toEqual(doclist(1,3).documents)

        it 'should populate @documents with undefined if a later request comes in before an earlier one', ->
          dl = new DocumentList(cache, new MockSelection())
          d1 = dl.slice(0, 2)
          d2 = dl.slice(2, 4)
          cache.deferreds[1].resolve(doclist(3, 5))
          expect(dl.documents).toEqual([undefined, undefined, doc(3), doc(4)])
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.documents).toEqual(doclist(1, 5).documents)

        it 'should populate @n', ->
          dl = new DocumentList(cache, new MockSelection())
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.n).toEqual(2)

        it 'should notify observers', ->
          dl = new DocumentList(cache, new MockSelection())
          deferred = dl.slice(0, 2)
          called = false
          dl.observe(-> called = true)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(called).toBeTruthy()

        it 'should call DocumentStore.add_doclist()', ->
          dl = new DocumentList(cache, new MockSelection())
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(document_store.adds).toEqual([{ doclist: { docids: [ 1, 2 ] }, documents: { '1': doc(1), '2': doc(2) }}])

      describe 'n', ->
        it 'should begin undefined', ->
          dl = new DocumentList(cache, new MockSelection())
          expect(dl.n).toBeUndefined()

      describe 'destroy', ->
        it 'should call DocumentStore.remove_doclist()', ->
          dl = new DocumentList(cache, new MockSelection())
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          dl.destroy()
          expect(document_store.removes).toEqual([{ docids: [ 1, 2 ] }])
