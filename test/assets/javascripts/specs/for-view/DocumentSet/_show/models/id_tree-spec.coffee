require [
  'for-view/DocumentSet/_show/models/id_tree'
], (IdTree) ->
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

      it 'should make has() work even with child-less nodes', ->
        do_add(1, [])
        expect(id_tree.has(1)).toBe(true)

      it 'should make all() work', ->
        do_add(1, [2, 3])
        do_add(2, [4, 5])
        expect(id_tree.all()).toEqual([1, 2])

      it 'should trigger :root', ->
        root = undefined
        id_tree.observe('root', (r) -> root = r)
        do_add(1, [])
        expect(root).toEqual(1)

      it 'should notify :root before/after :add/:remove such that notifications make a consistent tree', ->
        calls = []
        id_tree.observe('root', () -> calls.push('root'))
        id_tree.observe('add', () -> calls.push('add'))
        id_tree.observe('remove', () -> calls.push('remove'))
        do_add(1, [2, 3]) # add, root
        do_add(2, []) # add
        do_remove(2) # remove
        do_remove(1) # root, remove
        expect(calls).toEqual(['add', 'root', 'add', 'remove', 'root', 'remove'])

      describe 'Starting with a simple tree', ->
        beforeEach ->
          do_add(1, [2, 3])
          do_add(2, [4, 5])
          do_add(3, [6, 7])
          do_add(4, [8, 9])

        it 'should notify :remove from deepest to shallowest', ->
          args = []
          id_tree.observe('remove', (ids) -> args.push(ids))
          do_remove(1)
          expect(args).toEqual([[4, 3, 2, 1]])

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

        it 'should trigger :root when removing the root node', ->
          root = id_tree.root
          id_tree.observe('root', (r) -> root = r)
          do_remove(1)
          expect(root).toEqual(-1)

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

        it 'should throw an error when adding an existing node', ->
          expect(() ->
            do_add(4, [8, 9])
          ).toThrow('NodeAlreadyExists')

        it 'should not notify :root when removing a non-root node', ->
          called = false
          id_tree.observe('root', () -> called = true)
          do_remove(2)
          expect(called).toBeFalsy()

        it 'should notify :add with an ID when adding', ->
          args = []
          id_tree.observe('add', (ids) -> args.push(ids))
          do_add(6, [])
          expect(args).toEqual([[6]])

        it 'should notify :remove with an ID when removing', ->
          args = []
          id_tree.observe('remove', (ids) -> args.push(ids))
          do_remove(4)
          expect(args).toEqual([[4]])

        it 'should notify :remove with multiple IDs when removing', ->
          args = []
          id_tree.observe('remove', (ids) -> args.push(ids))
          do_remove(2)
          expect(args).toEqual([[5, 4, 2]]) # order doesn't matter

        it 'should notify :remove-undefined when removing', ->
          args = []
          id_tree.observe('remove-undefined', (ids) -> args.push(ids))
          do_remove(4)
          expect(args).toEqual([[9, 8]])

        it 'should notify :remove-undefined before :remove', ->
          args = []
          id_tree.observe('remove', (ids) -> args.push(ids))
          id_tree.observe('remove-undefined', (ids) -> args.push(ids))
          do_remove(4)
          expect(args).toEqual([[9, 8], [4]])

        it 'should notify :add with multiple IDs when adding in a single edit', ->
          args = []
          id_tree.observe('add', (ids) -> args.push(ids))
          id_tree.edit (editable) ->
            editable.add(6, [7, 8])
            editable.add(7, [])
            editable.add(8, [])
            expect(args).toEqual([])
          expect(args).toEqual([[6, 7, 8]])

        it 'should notify :add and :remove when changing in the same edit', ->
          args = []
          id_tree.observe('add', (ids) -> args.push(ids))
          id_tree.observe('remove', (ids) -> args.push(ids))
          id_tree.edit (editable) ->
            editable.add(6, [7, 8])
            editable.add(7, [])
            editable.remove(2)
          expect(args).toEqual([[6, 7], [5, 4, 2]])

        it 'should notify :edit after an edit', ->
          obj = undefined
          id_tree.observe('edit', (o) -> obj = o)
          id_tree.edit (editable) ->
            editable.add(6, [7, 8])
          expect(obj).toEqual({
            add: [6],
            remove: [],
            remove_undefined: [],
            root: undefined,
          })

        it 'should notify :edit even if nothing has changed', ->
          called = false
          id_tree.observe('edit', () -> called = true)
          id_tree.edit(->)
          expect(called).toBeTruthy()
