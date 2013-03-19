define ->
  # Helps select a bunch of indices in an Array.
  #
  # This is meant to mimic the operating system's list-selection mechanism. It
  # should behave the same way the file manager's selection does.
  #
  # The key method is get_indices(), which returns an ordered list of all
  # selected indices.
  #
  # Other methods map to user actions as you'd expect in a file manager. Some
  # rely on the concept of a "last index", which is best explained by looking at
  # what file managers do:
  #
  # * set_index(index): a click
  # * add_or_remove_index(index): Ctrl-click on Windows, Option-click on Mac
  # * add_or_replace_range_from_last_index_to_index(index): Shift-click on Mac,
  #   Shift-Ctrl-Click on Windows
  # * set_range_from_last_index_to_index(index): Shift-click on Windows
  #
  # This class does not detect the operating system; the caller should decide
  # when to add a range and when to set it.
  class ListSelection
    constructor: () ->
      @_selected_by_index = []
      @_last_index = undefined

    get_indices: () ->
      index for selected, index in @_selected_by_index when selected

    unset: () ->
      @_selected_by_index = []
      @_last_index = undefined

    set_index: (index) ->
      @_selected_by_index = []
      @_selected_by_index[index] = true
      @_last_index = index
      undefined

    add_or_remove_index: (index) ->
      selected = @_selected_by_index[index] = !@_selected_by_index[index]

      @_last_index = if selected
        index
      else
        undefined

    _select_range: (start_index, end_index) ->
      @_selected_by_index[i] = true for i in [ start_index .. end_index ]
      undefined

    add_or_expand_range_from_last_index_to_index: (index) ->
      if @_selected_by_index[index]
        this.add_or_remove_index(index)
      else
        @_last_index ||= index
        this._select_range(@_last_index, index)

    set_range_from_last_index_to_index: (index) ->
      @_last_index = index if !@_last_index?

      this.set_index(@_last_index)
      this._select_range(@_last_index, index)
