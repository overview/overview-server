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
