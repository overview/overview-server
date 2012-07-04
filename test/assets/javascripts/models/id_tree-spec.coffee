IdTree = require('models/id_tree').IdTree

describe 'models/id_tree', ->
  describe 'IdTree', ->
    id_tree = undefined

    do_add = (id, children) ->
      id_tree.edit (editable) ->
        editable.add(id, children)

    do_remove = (id) ->
      id_tree.edit (editable) ->
        editable.remove(id)

    beforeEach ->
      id_tree = new IdTree()

    it 'should have a @root of -1', ->
      expect(id_tree.root).toEqual(-1)

    it 'should have no @children', ->
      expect(_.keys(id_tree.children)).toEqual([])

    it 'should have no @parent', ->
      expect(_.keys(id_tree.parent)).toEqual([])

    it 'should make the first-added node the root', ->
      do_add(1, [2, 3])
      expect(id_tree.root).toEqual(1)

    it 'should add to @children', ->
      do_add(1, [2, 3])
      expect(id_tree.children[1]).toEqual([2, 3])

    it 'should add to @parent', ->
      do_add(1, [2, 3])
      expect(id_tree.parent[1]).toBeUndefined()
      expect(id_tree.parent[2]).toEqual(1)
      expect(id_tree.parent[3]).toEqual(1)

    it 'should make has() work', ->
      expect(id_tree.has(1)).toBe(false)
      do_add(1, [2, 3])
      expect(id_tree.has(1)).toBe(true)
      expect(id_tree.has(2)).toBe(false)

    it 'should make has() work, even with child-less nodes', ->
      do_add(1, [])
      expect(id_tree.has(1)).toBe(true)

    it 'should make all() work', ->
      do_add(1, [2, 3])
      do_add(2, [4, 5])
      expect(id_tree.all()).toEqual([1, 2])

    describe 'after removing a child', ->
      beforeEach ->
        do_add(1, [2, 3])
        do_add(2, [4, 5])
        do_remove(2)

      it 'should not has() the node', ->
        expect(id_tree.has(2)).toBeFalsy()

      it 'should keep a parent entry for the node', ->
        expect(id_tree.parent[2]).toEqual(1)

      it 'should delete parent entries for sub-nodes', ->
        expect(id_tree.parent[4]).toBeUndefined()
        expect(id_tree.parent[5]).toBeUndefined()

      it 'should keep the child entry from the parent node', ->
        expect(id_tree.children[1]).toEqual([2, 3])

      it 'should set @root to -1 when removing the root node', ->
        do_remove(1)
        expect(id_tree.root).toEqual(-1)

    describe 'starting with a bigger tree', ->
      beforeEach ->
        do_add(1, [2, 3])
        do_add(2, [4, 5])
        do_add(3, [6, 7])
        do_add(4, [8, 9])
        do_add(5, [])

      it 'should remove everything when removing the root', ->
        do_remove(1)
        expect(id_tree.children).toEqual({})
        expect(id_tree.parent).toEqual({})

      it 'should remove sub-nodes when removing', ->
        do_remove(2)
        expect(id_tree.children[5]).toBeUndefined()
        expect(id_tree.parent[9]).toBeUndefined()

      it 'should throw an error when removing a missing node', ->
        expect(() ->
          do_remove(9)
        ).toThrow('MissingNode')

      it 'should throw an error when adding an unexpected node', ->
        expect(() ->
          do_add(10, [])
        ).toThrow('MissingNode')
