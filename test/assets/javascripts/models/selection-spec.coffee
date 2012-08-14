Selection = require('models/selection').Selection

describe 'models/', ->
  describe 'Selection', ->
    it 'should begin empty', ->
      selection = new Selection()
      expect(selection.nodes).toEqual([])
      expect(selection.documents).toEqual([])
      expect(selection.tags).toEqual([])

    it 'should set nodes', ->
      selection = new Selection()
      selection.update({ nodes: [ 1, 2, 3 ] })
      expect(selection.nodes).toEqual([1, 2, 3])

    it 'should set a single node', ->
      selection = new Selection()
      selection.update({ node: 1 })
      expect(selection.nodes).toEqual([1])

    describe 'includes()', ->
      it 'should return true when appropriate', ->
        selection = new Selection()
        selection.update({ node: 1 })
        expect(selection.includes('node', 2)).toEqual(false)

      it 'should return true when appropriate', ->
        selection = new Selection()
        selection.update({ node: 1 })
        expect(selection.includes('node', 1)).toEqual(true)

    describe 'documents_from_cache', ->
      selection = undefined
      document_store = undefined
      on_demand_tree = undefined

      go = () ->
        selection.documents_from_cache({ document_store: document_store, on_demand_tree: on_demand_tree })

      beforeEach ->
        selection = new Selection()

        document_store = {
          documents: {
            1: { id: 1, title: 'doc1', tagids: [1] },
            2: { id: 2, title: 'doc2', tagids: [1, 2] },
            3: { id: 3, title: 'doc3', tagids: [2] },
            4: { id: 4, title: 'doc4', tagids: [1, 2] },
            5: { id: 5, title: 'doc5', tagids: [1] },
            6: { id: 6, title: 'doc6', tagids: [] },
            7: { id: 7, title: 'doc7', tagids: [1] },
          }
        }

        on_demand_tree = {
          id_tree: {
            root: 1,
            children: {
              1: [2,3],
              2: [4,5],
              3: [6,7],
            }
            parent: {
              2: 1,
              3: 1,
              4: 2,
              5: 2,
              6: 3,
              7: 3,
            }
          },
          nodes: {
            1: { doclist: { n: 7, docids: [ 1, 2, 3, 4, 5, 6, 7 ] } },
            2: { doclist: { n: 5, docids: [ 1, 2, 3, 4, 5 ] } },
            3: { doclist: { n: 2, docids: [ 6, 7 ] } },
            4: { doclist: { n: 3, docids: [ 1, 2, 3 ] } },
            5: { doclist: { n: 2, docids: [ 4, 5 ] } },
            6: { doclist: { n: 1, docids: [ 6 ] } },
            7: { doclist: { n: 1, docids: [ 7 ] } },
          },
        }

      afterEach ->
        selection = undefined
        document_store = undefined
        on_demand_tree = undefined

      it 'should select nothing when given a wrong node', ->
        selection.update({ nodes: [20] })
        v = go()
        expect(v).toEqual([])

      it 'should select all documents within a node', ->
        selection.update({ nodes: [2] })
        v = go()
        expect(v).toEqual([1, 2, 3, 4, 5].map((i) -> document_store.documents[i]))

      it 'should select all documents within two nodes', ->
        selection.update({ nodes: [ 3, 5 ] })
        v = go()
        expect(v).toEqual([4, 5, 6, 7].map((i) -> document_store.documents[i]))

      it 'should select all documents with a tag', ->
        selection.update({ tags: [ 2 ] })
        v = go()
        expect(v).toEqual([2, 3, 4].map((i) -> document_store.documents[i]))

      it 'should select documents by node and tag', ->
        selection.update({ nodes: [ 3, 5 ], tags: [ 1] })
        v = go()
        expect(v).toEqual([4, 5, 7].map((i) -> document_store.documents[i]))
