DocumentList = require('models/document_list').DocumentList

class MockStore
  constructor: (properties={}) ->
    _nodes = (properties.nodes || {})
    _tags = (properties.tags || {})
    _documents = (properties.documents || {})

    @nodes = { get: (id) -> _nodes[id] }
    @tags = { get: (id) -> _tags[id] }
    @documents = { get: (id) -> _documents[id] }

describe 'models/document_list', ->
  describe 'DocumentList', ->
    empty_selection = {
      nodes: [],
      documents: [],
      tags: [],
    }

    describe 'get_placeholder_documents', ->
      it 'should be empty with an empty store and selection', ->
        dl = new DocumentList(new MockStore(), empty_selection)
        documents = dl.get_placeholder_documents()
        expect(documents).toBeDefined()
        expect(documents).toEqual([])

      it 'should return empty when nodes are selected but not loaded', ->
        dl = new DocumentList(new MockStore(), { nodes: [1], documents: [], tags: []})
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual([])

      it 'should return a document when a node is selected', ->
        dl = new DocumentList(new MockStore({
          documents: { 1: 'foo' }
        }), {
          nodes: [{ id: 1, doclist: { docids: [1], n: 1 }}],
          documents: [],
          tags: [],
        })
        documents = dl.get_placeholder_documents()
        expect(documents).toEqual(['foo'])
