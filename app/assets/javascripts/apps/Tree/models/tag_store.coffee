define [ './TagLikeStore', './color_table' ], (TagLikeStore, ColorTable) ->
  class TagStore extends TagLikeStore
    constructor: () ->
      super('name', 'name')
      @_color_table = new ColorTable()
      @tags = @objects

    parse: (tag) ->
      # Beware sure to handle when we're given { name: 'blah', color: undefined }
      if !tag.color?
        tag.color = @_color_table.get(tag.name)

      tag

    find_by_name: (name) ->
      @find_by_key(name)
