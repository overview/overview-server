define [
  'apps/Tree/models/selection'
], (Selection) ->
  describe 'models/selection', ->
    describe 'Selection', ->
      it 'should begin empty', ->
        selection = new Selection()
        expect(selection.nodes).toEqual([])
        expect(selection.documents).toEqual([])
        expect(selection.tags).toEqual([])
        expect(selection.searchResults).toEqual([])

      it 'should set objects to passed values', ->
        obj = { nodes: [1, 2, 3], tags: [4, 5, 6], documents: [7, 8, 9] }
        selection = new Selection(obj)
        for key in [ 'nodes', 'tags', 'documents' ]
          expect(selection[key]).toEqual(obj[key])

      it 'should store ID arrays by value', ->
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

      testNonEmpty = (msg, attrs, expectedValue) ->
        it "should have nonEmpty=#{expectedValue} if #{msg}", ->
          selection = new Selection(attrs)
          expect(selection.nonEmpty()).toBe(expectedValue)

      testNonEmpty('there is a node', { nodes: [1] }, true)
      testNonEmpty('there is a document', { documents: [1] }, true)
      testNonEmpty('there is a tag', { tags: [1] }, true)
      testNonEmpty('there is a searchResult', { searchResults: [1] }, true)
      testNonEmpty('the selection is emtpy', {}, false)

      it 'should return isEmpty=true', ->
        selection = new Selection({})
        expect(selection.isEmpty()).toBe(true)

      it 'should return isEmpty=false', ->
        selection = new Selection({ nodes: [1] })
        expect(selection.isEmpty()).toBe(false)

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
