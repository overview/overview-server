require [
  'for-view/DocumentSet/_show/models/list_selection'
], (ListSelection) ->
  describe 'models/list_selection', ->
    describe 'ListSelection', ->
      selection = undefined

      beforeEach ->
        selection = new ListSelection()

      it 'should start empty', ->
        expect(selection.get_indices()).toEqual([])

      it 'should set to a single index', ->
        selection.set_index(4)
        expect(selection.get_indices()).toEqual([4])

      it 'should add_or_remove_index to add a single index', ->
        selection.set_index(4)
        selection.add_or_remove_index(6)
        expect(selection.get_indices()).toEqual([4, 6])

      it 'should add_or_remove_index to remove a single index', ->
        selection.set_index(4)
        selection.add_or_remove_index(4)
        expect(selection.get_indices()).toEqual([])

      it 'should add a range', ->
        selection.set_index(4)
        selection.add_or_expand_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should select a single item when trying to add a range but nothing was clicked previously', ->
        selection.add_or_expand_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([6])

      it 'should add a range properly after calling add-range that really just set a single index', ->
        selection.add_or_expand_range_from_last_index_to_index(4)
        selection.add_or_expand_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should select a single item when trying to add a range but the last click was a deselect', ->
        selection.set_index(4)
        selection.add_or_remove_index(4)
        selection.add_or_expand_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([6])

      it 'should expand a range', ->
        selection.set_index(4)
        selection.add_or_expand_range_from_last_index_to_index(5)
        selection.add_or_expand_range_from_last_index_to_index(7)
        expect(selection.get_indices()).toEqual([4, 5, 6, 7])

      it 'should deselect an index when trying to add a range on a selected index', ->
        selection.set_index(4)
        selection.add_or_remove_index(3)
        selection.add_or_expand_range_from_last_index_to_index(4)
        expect(selection.get_indices()).toEqual([3])

      it 'should set a range', ->
        selection.set_index(4)
        selection.set_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should set a range when there is an existing selection', ->
        selection.set_index(2)
        selection.add_or_remove_index(4)
        selection.set_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should set a single item when trying to set a range but nothing was clicked previously', ->
        selection.set_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([6])

      it 'should set a range properly after calling a set-range that really set a single index', ->
        selection.set_range_from_last_index_to_index(4)
        selection.set_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should shrink a selection when setting a range after setting a larger range', ->
        selection.set_index(4)
        selection.set_range_from_last_index_to_index(8)
        selection.set_range_from_last_index_to_index(6)
        expect(selection.get_indices()).toEqual([4, 5, 6])

      it 'should add a range when the second index is to the left of the first', ->
        selection.set_index(4)
        selection.add_or_expand_range_from_last_index_to_index(2)
        expect(selection.get_indices()).toEqual([2, 3, 4])

      it 'should set a range when the second index is to the left of the first', ->
        selection.set_index(4)
        selection.set_range_from_last_index_to_index(2)
        expect(selection.get_indices()).toEqual([2, 3, 4])

      it 'should not deselect an index when trying to set a range on a selected index', ->
        selection.set_index(4)
        selection.add_or_remove_index(3)
        selection.set_range_from_last_index_to_index(4)
        expect(selection.get_indices()).toEqual([3, 4])

      it 'should allow unset', ->
        selection.set_index(4)
        selection.unset()
        expect(selection.get_indices()).toEqual([])
