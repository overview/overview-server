Selection = require('models/selection').Selection

describe 'models/', ->
  describe 'Selection', ->
    it 'should begin empty', ->
      selection = new Selection()
      expect(selection.nodes).toEqual([])
      expect(selection.documents).toEqual([])
      expect(selection.tags).toEqual([])

    it 'should set objects to passed values', ->
      obj = { nodes: [1, 2, 3], tags: [4, 5, 6], documents: [7, 8, 9] }
      selection = new Selection(obj)
      for key in [ 'nodes', 'tags', 'documents' ]
        expect(selection[key]).toEqual(obj[key])

    it 'should store ID arrays by value, not by reference', ->
      obj = { nodes: [1, 2, 3], tags: [], documents: [] }
      selection = new Selection(obj)
      for key in [ 'nodes', 'tags', 'documents' ]
        obj[key].push(10)
        expect(selection[key]).not.toEqual(obj[key])

    it 'should plus() properly', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.plus({ tags: [ 4 ] })
      expect(selection2.nodes).toEqual([1, 2, 3])
      expect(selection2.tags).toEqual([4])

    it 'should plus() without overwriting values', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.plus({ nodes: [ 4 ] })
      expect(selection2.nodes).toEqual([1, 2, 3, 4])

    it 'should minus() properly', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.minus({ nodes: [ 3 ] })
      expect(selection2.nodes).toEqual([1, 2])

    it 'should ignore nonexistent values in minus()', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.minus({ nodes: [ 4 ] })
      expect(selection2.nodes).toEqual([1, 2, 3])

    it 'should replace() properly', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.replace({ nodes: [ 3 ] })
      expect(selection2.nodes).toEqual([3])

    it 'should replace() with an empty list', ->
      selection = new Selection({ nodes: [ 1, 2, 3] })
      selection2 = selection.replace({ nodes: [] })
      expect(selection2.nodes).toEqual([])

    it 'should equals() an equivalent selection', ->
      selection1 = new Selection({ nodes: [1], tags: [2], documents: [3] })
      selection2 = new Selection({ nodes: [1], tags: [2], documents: [3] })
      expect(selection1.equals(selection2)).toBe(true)

    it 'should not equals() a different selection', ->
      selection1 = new Selection({ nodes: [1], tags: [2], documents: [3] })
      selection2 = new Selection({ nodes: [1], tags: [2], documents: [] })
      expect(selection1.equals(selection2)).toBe(false)

    it 'should pick() properly', ->
      selection1 = new Selection({ nodes: [1], tags: [2], documents: [3] })
      selection2 = selection1.pick('nodes', 'tags')
      expect(selection2.nodes).toEqual([1])
      expect(selection2.tags).toEqual([2])
      expect(selection2.documents).toEqual([])

    describe 'allows_correct_tagcount_adjustments', ->
      it 'should return true for a node-only selection', ->
        selection = new Selection({ nodes: [1] })
        expect(selection.allows_correct_tagcount_adjustments()).toBe(true)

      it 'should return false for a selection with documents', ->
        selection = new Selection({ documents: [1] })
        expect(selection.allows_correct_tagcount_adjustments()).toBe(false)

      it 'should return false for a selection with tags', ->
        selection = new Selection({ tags: [1] })
        expect(selection.allows_correct_tagcount_adjustments()).toBe(false)

    describe 'documents_from_cache', ->
      selection = undefined
      document_store = undefined
      on_demand_tree = undefined

      extend = (rhs) ->
        selection = selection.plus(rhs)

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
        extend({ nodes: [20] })
        v = go()
        expect(v).toEqual([])

      it 'should select all documents within a node', ->
        extend({ nodes: [2] })
        v = go()
        expect(v).toEqual([1, 2, 3, 4, 5].map((i) -> document_store.documents[i]))

      it 'should select all documents within two nodes', ->
        extend({ nodes: [ 3, 5 ] })
        v = go()
        expect(v).toEqual([4, 5, 6, 7].map((i) -> document_store.documents[i]))

      it 'should select all documents with a tag', ->
        extend({ tags: [ 2 ] })
        v = go()
        expect(v).toEqual([2, 3, 4].map((i) -> document_store.documents[i]))

      it 'should select documents by node and tag', ->
        extend({ nodes: [ 3, 5 ], tags: [ 1] })
        v = go()
        expect(v).toEqual([4, 5, 7].map((i) -> document_store.documents[i]))

      it 'should select documents by document ID', ->
        extend({ documents: [1] })
        v = go()
        expect(v).toEqual([document_store.documents[1]])
