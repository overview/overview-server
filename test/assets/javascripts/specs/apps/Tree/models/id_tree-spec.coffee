require [
  'apps/Tree/models/id_tree'
], (IdTree) ->
  describe 'models/id_tree', ->
    describe 'IdTree', ->
      id_tree = undefined

      do_add = (id, children) ->
        id_tree.batchAdd (add) -> add(id, children)

      do_remove = (id) ->
        id_tree.batchRemove (remove) -> remove(id)

      beforeEach ->
        id_tree = new IdTree()

      it 'should have a @root of undefined', ->
        expect(id_tree.root).toEqual(null)

      it 'should have no @children', ->
        expect(_.keys(id_tree.children)).toEqual([])

      it 'should have no @parent', ->
        expect(_.keys(id_tree.parent)).toEqual([])

      it 'should make the first-added node the root', ->
        do_add(null, 1)
        expect(id_tree.root).toEqual(1)

      it 'should add to @children', ->
        do_add(null, 1)
        do_add(1, 2)
        do_add(1, 3)
        expect(id_tree.children[1]).toEqual([2, 3])

      it 'should add to @parent', ->
        do_add(null, 1)
        do_add(1, [2, 3])
        expect(id_tree.parent[1]).toBe(null)
        expect(id_tree.parent[2]).toEqual(1)
        expect(id_tree.parent[3]).toEqual(1)

      it 'should make has() work', ->
        expect(id_tree.has(1)).toBe(false)
        do_add(null, 1)
        do_add(1, [2, 3])
        expect(id_tree.has(1)).toBe(true)
        expect(id_tree.has(2)).toBe(false)

      it 'should make all() work', ->
        do_add(null, 1)
        do_add(1, [2, 3])
        do_add(2, [4, 5])
        expect(id_tree.all()).toEqual([1, 2, 3, 4, 5])

      it 'should trigger :root', ->
        spy = jasmine.createSpy()
        id_tree.observe('change', spy)
        do_add(null, 1)
        expect(spy).toHaveBeenCalledWith({ added: [1], root: 1 })

      describe 'Starting with a simple tree', ->
        beforeEach ->
          id_tree.batchAdd (add) ->
            add(null, 1)
            add(1, [2, 3])
            add(2, [4, 5])
            add(3, [6, 7])
            add(4, [8, 9])

        it 'should walk() postorder', ->
          arr = []
          id_tree.walk((x) -> arr.push(x))
          expect(arr).toEqual([ 8, 9, 4, 5, 2, 6, 7, 3, 1 ])

        it 'should walkFrom() postorder', ->
          arr = []
          id_tree.walkFrom(2, (x) -> arr.push(x))
          expect(arr).toEqual([ 8, 9, 4, 5, 2 ])

        it 'should throw InvalidWalkRoot when calling walkFrom on an invalid id', ->
          expect(-> id_tree.walkFrom(10, ->)).toThrow('InvalidWalkRoot')

        it 'should notify on removal', ->
          spy = jasmine.createSpy()
          id_tree.observe('change', spy)
          id_tree.batchRemove((remove) -> remove([8, 9]))
          expect(spy).toHaveBeenCalledWith({ removed: [ 8, 9 ] })

        it 'should return undefined when calling loaded_descendents() on an unloaded node', ->
          expect(id_tree.loaded_descendents(8)).toEqual(undefined)

        it 'should return an empty list when calling loaded_descendents() on a leaf', ->
          expect(id_tree.loaded_descendents(4)).toEqual([])

        it 'should return loaded_descendents() from deepest to shallowest', ->
          do_add(6, [10, 11])
          expect(id_tree.loaded_descendents(1)).toEqual([ 6, 4, 3, 2 ])

        describe 'is_id_ancestor_of_id', ->
          it 'should throw MissingNode if id1 is not in the tree', ->
            expect(->
              id_tree.is_id_ancestor_of_id(10, 4)
            ).toThrow('MissingNode')

          it 'should throw MissingNode if id2 is not in the tree', ->
            expect(->
              id_tree.is_id_ancestor_of_id(1, 10)
            ).toThrow('MissingNode')

          it 'should return false if id2 is the root', ->
            ret = id_tree.is_id_ancestor_of_id(2, 1)
            expect(ret).toBe(false)

          it 'should return false if id1 is a leaf', ->
            ret = id_tree.is_id_ancestor_of_id(9, 3)
            expect(ret).toBe(false)

          it 'should return false if id1 == i2', ->
            ret = id_tree.is_id_ancestor_of_id(2, 2)
            expect(ret).toBe(false)

          it 'should return true if id1 is parent of id2', ->
            ret = id_tree.is_id_ancestor_of_id(1, 2)
            expect(ret).toBe(true)

          it 'should return true if id1 is a grandparent of id2', ->
            ret = id_tree.is_id_ancestor_of_id(2, 9)
            expect(ret).toBe(true)

          it 'should return false if id1 is not an ancestor of id2', ->
            ret = id_tree.is_id_ancestor_of_id(3, 9)
            expect(ret).toBe(false)

      describe 'after removing a node', ->
        beforeEach ->
          id_tree.batchAdd (add) ->
            add(null, 1)
            add(1, [2, 3])
            add(2, [4, 5])
          do_remove([5, 4])

        it 'should not has() the node', ->
          expect(id_tree.has(4)).toBe(false)

        it 'should delete the parent entry for the node', ->
          expect(id_tree.parent[4]).toBeUndefined()

        it 'should delete the child entries from the parent node', ->
          expect(id_tree.children[2]).toBeUndefined()

        it 'should set @root to null when removing the root node', ->
          do_remove([3, 2, 1])
          expect(id_tree.root).toBe(null)

        it 'should trigger change with root when removing the root node', ->
          spy = jasmine.createSpy()
          id_tree.observe('change', spy)
          do_remove([3, 2, 1])
          expect(spy).toHaveBeenCalledWith({ removed: [ 3, 2, 1 ], root: null })

      describe 'starting with a bigger tree', ->
        beforeEach ->
          id_tree.batchAdd (add) ->
            add(null, 1)
            add(1, [2, 3])
            add(2, [4, 5])
            add(3, [6, 7])
            add(4, [8, 9])

        it 'should throw an error when removing a missing node', ->
          expect(() ->
            do_remove(10)
          ).toThrow('NoNodeToRemove')

        it 'should throw an error when adding an unexpected node', ->
          expect(() ->
            do_add(10, 11)
          ).toThrow('MissingParent')
