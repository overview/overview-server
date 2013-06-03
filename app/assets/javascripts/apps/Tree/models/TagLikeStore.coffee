define [ 'underscore', './observable', './color_table' ], (_, observable, ColorTable) ->
  # A store of Tags or SearchResults.
  #
  # Each store has a @_key field, set in the constructor, which is a unique
  # String each object has. For a Tag, that's "name"; for a SearchResult,
  # it's "query". Objects will be sorted by this field, using case-insensitive
  # String.localeCompare(). Objects will also have a transient "position"
  # property set by this class, indicating their position in the Array.
  #
  # Each store has an @objects Array; each Object in that Array is a tag-like.
  #
  # The following methods are available:
  #
  # * add(attributes): adds a tagLike. If no ID exists, assigns a temporary,
  #   negative-integer ID. Emits added(tagLike). Calls parse() to translate
  #   the given attributes into a tagLike.
  # * remove(tagLike): removes a tagLike. Emits removed(tagLike).
  # * change(tagLike, attrs): assigns new attributes to tagLike. Emits
  #   id-changed(oldId, tagLike) if appropriate; emits changed(tagLike).
  # * find_by_key(key): returns a tagLike or undefined.
  # * find_by_id(key): returns a tagLike or undefined.
  #
  # Each store emits the following signals:
  #
  # * added(tagLike): after a tagLike is added
  # * removed(tagLike): after a tagLike is removed
  # * id-changed(oldId, tagLike): after a tagLike's ID changes
  # * changed(tagLike): after some properties of the tagLike have changed (after id-changed, if id-changed is also being called)
  #
  # Extending classes must implement:
  #
  # * parse(attrs): translates attributes into a valid tagLike. (A valid
  #   tagLike _must_ have an "id" attribute and the appropriate "key"
  #   attributes.)
  class TagLikeStore
    observable(this)

    constructor: (@_key) ->
      @objects = []
      @_last_unsaved_id = 0

    _sort: ->
      key = @_key
      @objects.sort((a, b) -> a[key].toLocaleLowerCase().localeCompare(b[key].toLocaleLowerCase()))
      tl.position = i for tl, i in @objects
      undefined

    add: (attributes) ->
      tagLike = @parse(attributes)

      if !tagLike.id?
        id = @_last_unsaved_id -= 1
        tagLike.id = id

      @objects.push(tagLike)
      @_sort()

      @_notify('added', tagLike)
      tagLike

    remove: (tagLike) ->
      position = @objects.indexOf(tagLike)
      throw 'tagLikeNotFound' if position == -1

      @objects.splice(position, 1)
      tl.position = i for tl, i in @objects

      @_notify('removed', tagLike)
      tagLike

    change: (tagLike, attributes) ->
      oldId = tagLike.id

      for k, v of attributes
        if !v?
          tagLike[k] = undefined
        else
          tagLike[k] = JSON.parse(JSON.stringify(v))

      @_sort()

      @_notify('id-changed', oldId, tagLike) if oldId != tagLike.id
      @_notify('changed', tagLike)
      tagLike

    find_by_key: (value) ->
      key = @_key
      _.find(@objects, (v) -> v[key] == value)

    find_by_id: (id) ->
      ret = _.find(@objects, (v) -> v.id == id)
      throw 'tagLikeNotFound' if !ret
      ret
