define [ 'backbone' ], (Backbone) ->
  # Handles click-on-list-items logic, cross-platform.
  #
  # This is really a model, not a controller; but since it behaves differently
  # across platforms, and it deals in clicks and keyboard shortcuts, we lump it
  # together with controllers.
  #
  # Usage:
  #
  #   listSelection = new ListSelection()
  #   controller = new ListSelectionController({
  #     selection: listSelection
  #     cursorIndex: 0
  #     isValidIndex: -> true
  #     platform: 'mac' # or linux or windows
  #   })
  #
  #   # Manipulating...
  #   controller.onClick(4) # clicks item 4
  #   controller.onClick(4, { meta: true, shift: true }) # clicks item 4
  #   controller.onUp({ shift: true })
  #   controller.onSelectAll()
  #
  #   # Reading values...
  #   controller.get('selectedIndices') # may be an empty Array
  #   controller.get('cursorIndex') # may be undefined
  #   controller.on('change:selectedIndices', -> ...)
  #   controller.on('change:cursorIndex', -> ...)
  Backbone.Model.extend
    defaults: {
      selection: undefined
      cursorIndex: undefined
      selectedIndices: []
      platform: undefined
      isValidIndex: undefined
    }

    initialize: (attrs, options) ->

    # Handles click/up/down. They behave the same, mostly.
    #
    # index: index. If it's invalid (according to isValidIndex), nothing will
    #        happen. If it's undefined, nothing will happen.
    # options: { meta: true-or-false, shift: true-or-false }
    _onActionAtIndex: (index, options) ->
      selection = @get('selection')
      isValidIndex = @get('isValidIndex')

      return if !index? || (isValidIndex? && !isValidIndex(index))

      if !options?.meta && !options?.shift
        selection.set_index(index)
      else if @get('platform') == 'mac'
        # Mac OS: Command key overrides Shift key, and one or both are down
        if options?.meta
          selection.add_or_remove_index(index)
        else
          selection.set_range_from_last_index_to_index(index)
      else
        # Windows/Linux: Shift+Command adds ranges
        if options.shift
          if options.meta # (Shift+Ctrl)
            selection.add_or_expand_range_from_last_index_to_index(index)
          else # (Shift only)
            selection.set_range_from_last_index_to_index(index)
        else # (Ctrl only)
          selection.add_or_remove_index(index)

      @set
        cursorIndex: index
        selectedIndices: selection.get_indices()

    onClick: (index, options) ->
      @_onActionAtIndex(index, options)

    onUp: (options) ->
      lastIndex = @get('cursorIndex')
      if lastIndex > 0
        @_onActionAtIndex(lastIndex - 1, options)
      else
        @onSelectAll()

    onDown: (options) ->
      lastIndex = @get('cursorIndex')
      if lastIndex?
        @_onActionAtIndex(lastIndex + 1, options)
      else
        @_onActionAtIndex(0, options)

    onSelectAll: ->
      selection = @get('selection')
      selection.unset()
      @set
        cursorIndex: undefined
        selectedIndices: selection.get_indices()
