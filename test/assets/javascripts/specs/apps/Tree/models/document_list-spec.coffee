define [
  'jquery'
  'apps/Tree/models/document_list'
], ($, DocumentList) ->
  class MockDocumentStore
    constructor: () ->
      @documents = []

    reset: (@documents) ->

  class MockCache
    constructor: () ->
      @document_store = new MockDocumentStore()
      @on_demand_tree = { id_tree: { root: 1 } }
      @deferreds = []

    resolve_deferred: (key, obj) ->
      @deferreds.push(ret = new $.Deferred())
      ret

  class MockParams
    constructor: (@apiParams) ->
    toApiParams: (@filter) -> @apiParams

  describe 'models/document_list', ->
    describe 'DocumentList', ->
      doc = (i) -> { id: i, title: "doc#{i}", searchResultIds: [] }
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

      describe 'slice', ->
        it 'should return documents it already has without a server call', ->
          dl = new DocumentList(cache, new MockParams({}))
          dl.documents = doclist(1, 6).documents
          dl.n = 5
          deferred = dl.slice(0, 3)
          expect(deferred.state()).to.eq('resolved')
          arr = undefined
          deferred.done((x) -> arr = x)
          expect(arr).to.deep.eq(doclist(1, 4).documents)

        it 'should filter for the given document set', ->
          params = new MockParams({})
          params.toApiParams = sinon.stub().returns({ nodes: 1 })
          dl = new DocumentList(cache, params)
          dl.slice(0, 2)
          expect(params.toApiParams).to.have.been.calledWith(nodes: 1)

        it 'should call Cache.resolve_deferred(selection_documents_slice)', ->
          cache.resolve_deferred = sinon.stub().returns($.Deferred())
          dl = new DocumentList(cache, new MockParams(nodes: '2'))
          deferred = dl.slice(0, 2)
          expect(cache.resolve_deferred).to.have.been.calledWith('selection_documents_slice', { nodes: '2', pageSize: 2, page: 1 })

        it 'should give the return value of get_selection_documents_slice', ->
          dl = new DocumentList(cache, new MockParams({}))
          deferred = dl.slice(0, 2)
          expect(deferred.state()).to.eq('pending')
          arr = undefined
          deferred.done((x) -> arr = x)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(arr).to.deep.eq(doclist(1,3).documents)

        it 'should cache unresolved values', ->
          dl = new DocumentList(cache, new MockParams({}))
          d1 = dl.slice(0, 2)
          d2 = dl.slice(0, 2)
          expect(d2).to.be(d1)

        it 'should cache resolved values', ->
          dl = new DocumentList(cache, new MockParams({}))
          d1 = dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          d2 = dl.slice(0, 2)
          expect(d2).to.be(d1)

        it 'should populate @documents', ->
          dl = new DocumentList(cache, new MockParams({}))
          deferred = dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.documents).to.deep.eq(doclist(1,3).documents)

        it 'should populate @documents with undefined if a later request comes in before an earlier one', ->
          dl = new DocumentList(cache, new MockParams({}))
          d1 = dl.slice(0, 2)
          d2 = dl.slice(2, 4)
          cache.deferreds[1].resolve(doclist(3, 5))
          expect(dl.documents[0]).to.be.undefined
          expect(dl.documents[1]).to.be.undefined
          expect(dl.documents[2]).to.deep.eq(doc(3))
          expect(dl.documents[3]).to.deep.eq(doc(4))
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.documents).to.deep.eq(doclist(1, 5).documents)

        it 'should populate @n', ->
          dl = new DocumentList(cache, new MockParams({}))
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(dl.n).to.eq(2)

        it 'should notify observers', ->
          dl = new DocumentList(cache, new MockParams({}))
          deferred = dl.slice(0, 2)
          called = false
          dl.observe(-> called = true)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(called).to.be.ok

        it 'should call DocumentStore.reset()', ->
          dl = new DocumentList(cache, new MockParams({}))
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          expect(document_store.documents).to.deep.eq([ doc(1), doc(2) ])

      describe 'n', ->
        it 'should begin undefined', ->
          dl = new DocumentList(cache, new MockParams({}))
          expect(dl.n).to.be.undefined

      describe 'destroy', ->
        it 'should call DocumentStore.reset()', ->
          dl = new DocumentList(cache, new MockParams({}))
          dl.slice(0, 2)
          cache.deferreds[0].resolve(doclist(1, 3))
          dl.destroy()
          expect(document_store.documents).to.deep.eq([])

    describe 'describeParameters', ->
      it 'should describe all', ->
        dl = new DocumentList({}, { type: 'all' })
        expect(dl.describeParameters()).to.deep.eq([ 'all' ])

      it 'should describe untagged', ->
        dl = new DocumentList({}, { type: 'untagged' })
        expect(dl.describeParameters()).to.deep.eq([ 'untagged' ])

      it 'should describe a node', ->
        dl = new DocumentList({ on_demand_tree: { nodes: { 2: { description: 'foo' } } } }, { type: 'node', nodeId: 2 })
        expect(dl.describeParameters()).to.deep.eq([ 'node', 'foo' ])

      it 'should describe a search result', ->
        searchResultStore = { find_by_id: sinon.stub().returns({ query: 'foo' }) }
        dl = new DocumentList(
          { search_result_store: searchResultStore },
          { type: 'searchResult', searchResultId: 1 }
        )
        expect(dl.describeParameters()).to.deep.eq([ 'searchResult', 'foo' ])
        expect(searchResultStore.find_by_id).to.have.been.calledWith(1)

      it 'should describe a tag', ->
        tagStore = { find_by_id: sinon.stub().returns({ name: 'foo' }) }
        dl = new DocumentList({ tag_store: tagStore }, { type: 'tag', tagId: 1 })
        expect(dl.describeParameters()).to.deep.eq([ 'tag', 'foo' ])
        expect(tagStore.find_by_id).to.have.been.calledWith(1)
