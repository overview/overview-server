define [ 'underscore' ], (_) ->
  # Describes how to find a document list.
  #
  # A DocumentListParams is the intersection of the lists of documents found
  # from each of the following searches:
  #
  # * `objects`: { title: <String>, ids: <Array of Object IDs>, nodeIds: <Array of Node IDs> }
  # * `tags`: { ids: <Array of Tag IDs>, tagged: <Boolean>, operation: '<all|any|none, default any>' }
  # * `q`: <String -- empty string for no search>
  #
  # Each DocumentListParams is immutable.
  #
  # Example usage:
  #
  #     params = new DocumentListParams(
  #       objects: { title: '%s in folder "foo"', ids: [ 234 ] }
  #       q: 'bar'
  #     )
  #
  #     # Shortcut to build DocumentListParams(q=bar)
  #     params.withObjects(null)
  #
  #     # Shortcut to build a DocumentListParams that searches for documents
  #     # that have tag 345, tag 456 or no tags at all
  #     params.withTags(ids: [ 345, 456 ], tagged: false)
  #
  #     params.reset() # DocumentListParams()
  #
  #     # You can also specify tags as just an Array of IDs
  #     params.withTags([ 345, 455 ])
  #
  #     params.toJSON() # { objects: [ 234 ], q: 'bar' }
  #     params.toQueryString() # objects=234&q=bar
  #
  # The query strings (or JSON) may include some (or none) of these parameters:
  #
  # * q: a full-text search query string (toQueryString() url-encodes it)
  # * objects: an Array of StoreObject IDs
  # * nodes: Tree node IDs (deprecated)
  # * tags: an Array of Tag IDs
  # * tagged: a boolean
  # * tagOperation: 'all', 'none' or undefined (default, 'any')
  class DocumentListParams
    constructor: (options={}) ->
      @objects = @_parseObjects(options.objects)
      @tags = @_parseTags(options.tags)
      @q = @_parseQ(options.q)

    withQ: (q) -> new DocumentListParams(objects: @objects, tags: @tags, q: q)
    withTags: (tags) -> new DocumentListParams(objects: @objects, tags: tags, q: @q)
    withObjects: (objects) -> new DocumentListParams(objects: objects, tags: @tags, q: @q)

    # Convenience method: returns new DocumentListParams().
    #
    # This is handy in situations where you otherwise wouldn't need to require()
    # it at all.
    reset: -> new DocumentListParams()

    # Finds the query string from the constructor option `q`.
    #
    # The argument is trimmed; if it is empty, we use `null` instead.
    _parseQ: (q) ->
      q = q?.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, '') # FIXME: with PhantomJS 2, q = q.trim()
      q || null

    # Finds the tags from the constructor option `tags`.
    #
    # If the argument is an Array of IDs, we use those. Otherwise, we parse an
    # Object that contains any of `ids`, `tagged` and/or `operation`.
    _parseTags: (tags={}) ->
      tags = { ids: tags } if _.isArray(tags)

      for k, __ of tags when k not in [ 'ids', 'tagged', 'operation' ]
        throw new Error("Invalid option tags.#{k}")

      if tags.tagged? && !_.isBoolean(tags.tagged)
        throw new Error("Invalid option tags.tagged=#{JSON.stringify(tags.tagged)}")

      if tags.operation? && tags.operation not in [ 'any', 'all', 'none' ]
        throw new Error("Invalid option tags.operation=#{JSON.stringify(tags.operation)}")

      if !tags.ids && !tags.tagged?
        null
      else
        ret = {}
        ret.ids = tags.ids if tags.ids
        ret.tagged = tags.tagged if tags.tagged?
        ret.operation = tags.operation if tags.operation in [ 'all', 'none' ]
        ret

    # Finds the objects from the constructor option `objects`.
    #
    # We parse an Object that contains `title` and one of `ids` or `nodeIds`.
    _parseObjects: (objects) ->
      return null if !objects

      if _.isEmpty(objects.ids) && _.isEmpty(objects.nodeIds)
        throw new Error('Missing option objects.ids or objects.nodeIds')
      if !objects.title
        throw new Error('Missing option objects.title')

      ret = { title: objects.title }
      ret.ids = objects.ids if !_.isEmpty(objects.ids)
      ret.nodeIds = objects.nodeIds if !_.isEmpty(objects.nodeIds)
      ret

    # Returns a flattened representation, for querying the server.
    toJSON: ->
      ret = {}
      ret.objects = @objects.ids if !_.isEmpty(@objects?.ids)
      ret.nodes = @objects.nodeIds if !_.isEmpty(@objects?.nodeIds)
      ret.tags = @tags.ids if !_.isEmpty(@tags?.ids)
      ret.tagged = @tags.tagged if _.isBoolean(@tags?.tagged)
      ret.tagOperation = @tags.operation if _.isString(@tags?.operation) && @tags.operation != 'any'
      ret.q = @q if @q
      ret

    # Returns a String representation, for debugging.
    toString: ->
      parts = []
      for k, v of @toJSON()
        parts.push("#{k}=#{v.toString()}")
      if @objects?.title
        parts.push("title=#{@objects.title}")

      "DocumentListParams(#{parts.join(';')})"

    # Returns true iff rhs is certainly equivalent to this one.
    #
    # This means same documentSet, view, title and params.
    equals: (rhs) ->
      _.isEqual(@toJSON(), rhs.toJSON())

    toQueryString: ->
      arr = []
      for k, v of @toJSON()
        if _.isArray(v)
          arr.push("#{k}=#{v.toString()}")
        else
          arr.push("#{k}=#{encodeURIComponent(v)}")
      arr.join('&')
