define [
  'apps/Tree/controllers/ListSelectionController'
], (ListSelectionController) ->
  describe 'apps/Tree/controllers/ListSelectionController', ->
    selection = undefined
    controller = undefined

    init = (cursorIndex, platform) ->
      selection = jasmine.createSpyObj('ListSelection', [
        'get_indices'
        'unset'
        'set_index'
        'add_or_remove_index'
        'add_or_expand_range_from_last_index_to_index'
        'set_range_from_last_index_to_index'
      ])
      controller = new ListSelectionController({
        selection: selection
        cursorIndex: cursorIndex
        platform: platform
      })

    describe 'starting from scratch on any platform', ->
      beforeEach -> init(undefined, 'nonexistent-platform')

      it 'should have an undefined cursorIndex', ->
        expect(controller.get('cursorIndex')).toBeUndefined()

      it 'should have no selected indices', ->
        expect(controller.get('selectedIndices')).toEqual([])

      describe 'on click', ->
        beforeEach ->
          selection.get_indices.andReturn([0])
          controller.onClick(0)

        it 'should call selection.set_index', ->
          expect(selection.set_index).toHaveBeenCalledWith(0)

        it 'should update selectedIndices', ->
          expect(controller.get('selectedIndices')).toEqual([0])

        it 'should set cursorIndex when clicking', ->
          expect(controller.get('cursorIndex')).toBe(0)

      describe 'on down', ->
        beforeEach ->
          selection.get_indices.andReturn([0])
          controller.onDown()

        it 'should call selection.set_index(0)', ->
          expect(selection.set_index).toHaveBeenCalledWith(0)

        it 'should update selectedIndices', ->
          expect(controller.get('selectedIndices')).toEqual([0])

        it 'should set cursorIndex', ->
          expect(controller.get('cursorIndex')).toEqual(0)

      it 'should not call selection.set_index on down when list is empty', ->
        controller.set('isValidIndex', -> false)
        controller.onDown()
        expect(selection.set_index).not.toHaveBeenCalled()

    describe 'starting from scratch on mac', ->
      beforeEach -> init(undefined, 'mac')

      it 'should call selection.set_range_from_last_index_to_index on shift-click', ->
        controller.onClick(0, { shift: true })
        expect(selection.set_range_from_last_index_to_index).toHaveBeenCalledWith(0)

      it 'should call selection.add_or_remove_index on meta-click', ->
        controller.onClick(0, { meta: true })
        expect(selection.add_or_remove_index).toHaveBeenCalledWith(0)

      it 'should call selection.add_or_remove_index on shift-meta-click', ->
        controller.onClick(0, { meta: true, shift: true })
        expect(selection.add_or_remove_index).toHaveBeenCalledWith(0)

    describe 'starting from scratch on windows', ->
      beforeEach -> init(undefined, 'windows')

      it 'should call selection.set_range_from_last_index_to_index on shift-click', ->
        controller.onClick(0, { shift: true })
        expect(selection.set_range_from_last_index_to_index).toHaveBeenCalledWith(0)

      it 'should call selection.add_or_remove_index on ctrl-click', ->
        controller.onClick(0, { meta: true })
        expect(selection.add_or_remove_index).toHaveBeenCalledWith(0)

      it 'should call selection.add_or_expand_range_from_last_index_to_index on shift-ctrl-click', ->
        controller.onClick(0, { meta: true, shift: true })
        expect(selection.add_or_expand_range_from_last_index_to_index).toHaveBeenCalledWith(0)

    describe 'at index 5 of 10 documents', ->
      beforeEach ->
        init(5, 'nonexistent-platform')
        controller.set('isValidIndex', (i) -> i < 10)

      it 'should not call selection.set_index when clicking an invalid index', ->
        controller.onClick(10)
        expect(selection.set_index).not.toHaveBeenCalled()

      describe 'on down', ->
        beforeEach ->
          selection.get_indices.andReturn([6])
          controller.onDown()

        it 'should call selection.set_index', ->
          expect(selection.set_index).toHaveBeenCalledWith(6)

        it 'should update selectedIndices', ->
          expect(controller.get('selectedIndices')).toEqual([6])

        it 'should set cursorIndex', ->
          expect(controller.get('cursorIndex')).toEqual(6)

      describe 'on select-all', ->
        beforeEach ->
          selection.get_indices.andReturn([])
          controller.onSelectAll()

        it 'should call selection.unset', ->
          expect(selection.unset).toHaveBeenCalled()

        it 'should update selectedIndices', ->
          expect(controller.get('selectedIndices')).toEqual([])

        it 'should unset cursorIndex', ->
          expect(controller.get('cursorIndex')).toBeUndefined()

      describe 'on up', ->
        beforeEach ->
          selection.get_indices.andReturn([4])
          controller.onUp()

        it 'should call selection.set_index', ->
          expect(selection.set_index).toHaveBeenCalledWith(4)

        it 'should update selectedIndices', ->
          expect(controller.get('selectedIndices')).toEqual([4])

        it 'should set cursorIndex', ->
          expect(controller.get('cursorIndex')).toEqual(4)

      it 'should call selection.set_range_from_last_index_to_index on shift-up', ->
        controller.onUp({ shift: true })
        expect(selection.set_range_from_last_index_to_index).toHaveBeenCalledWith(4)

    it 'should unset selection when going up past top document', ->
      init(0, 'nonexistent-platform')
      controller.onUp()
      expect(selection.unset).toHaveBeenCalled()
