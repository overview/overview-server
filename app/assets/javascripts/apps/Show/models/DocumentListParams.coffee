define [ 'underscore', 'i18n' ], (_, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListParams')

  string =
    toParam: (s) -> String(s)
    toString: (s) -> s
    toQueryParam: (s) -> s

  intArray =
    toParam: (arr) ->
      if _.isEmpty(arr)
        null
      else
        arr = [ arr ] if !_.isArray(arr)
        (Math.round(Number(n))) for n in arr
    toString: (arr) -> String(arr)
    toQueryParam: (arr) -> String(arr)

  boolean =
    toParam: (b) ->
      if b == true
        true
      else
        false
    toString: (b) -> String(b)
    toQueryParam: (b) -> String(b)

  Attributes =
    q: string
    nodes: intArray
    tagged: boolean
    objects: intArray
    nodes: intArray
    tags: intArray

  # Describes how to find a document list.
  #
  # Each DocumentListParams is immutable.
  #
  # Example usage:
  #
  #     params = new DocumentListParams(state, view, nodes: [ 123 ], title: '%s in topic “blah”')
  #     params.toString()        # "DocumentListParams(nodes=123)"
  #     params2 = params.reset(tags: [ 2,3 ], title: '%s tagged “foo”')
  #     params2.toString()       # "DocumentListParams(tags=2,3)"
  #     params3 = params2.withView(view2).reset(nodes: 234, tags: [2,3], title: '%s in topic “bleh”)
  #     params3.toString()       # "DocumentListParams(tags=2,3;nodes=234)"
  #     params3.equals(params3)  # true
  #     params3.equals(params2)  # false
  #     params3.title            # '%s in topic “bleh”' ... %s will be replaced elsewhere
  #
  #     params3.toJSON()         # { tags: [ 2, 3 ], nodes: 234 }
  #     params3.toQueryString()  # tags=2,3&nodes=234&q=foo with no initial '?'
  #                              # if view2.addScopeToQueryParams(tags: [ 2, 3 ], nodes: 234)
  #                              # returns { tags: [ 2, 3 ], nodes: 234, q: 'foo' }
  #     params3.toQueryParams()  # { tags: '2,3', nodes: '234', q: 'foo' }
  #
  # Here are the possible attributes, which become query string parameters:
  #
  # * q: a full-text search query string (toQueryString() url-encodes it)
  # * tags: an Array of Tag IDs, or null
  # * objects: an Array of StoreObject IDs, or null
  # * tagged: a boolean, or null
  # * nodes: Tree nodes (TODO: nix and use objects instead)
  #
  # You're expected to use .reset() and .withView() instead of constructing
  # DocumentListParams from scratch. Here are some helper methods:
  #
  #     params.reset.byNode(node) # nodes: [ node.id ], title: '%s in topic “#{node.description}”
  #     params.reset.byTag(tag)   # tags: [ tag.id ], title: '%s tagged “#{tag.name}”
  #     params.reset.byUntagged() # tagged: false, title: '%s without any tags'
  #     params.reset.byQ(q)       # q: q, title: '%s matching “#{q}”
  #     params.reset.all()        # title: '%s in document set
  #
  # If you call reset() without a title, some calls will auto-generate titles
  #
  #     params.reset(nodes: [ 2 ])  # nodes: [ 2 ], title: '%s in topic “#{node.description}”'
  #     params.reset(tags: [ 1 ])   # tags: [ 1 ], title: '%s tagged “#{tag.name}”
  #     params.reset(tagged: false) # tagged: false, title: '%s without any tags'
  #     params.reset(q: 'foo')      # q: 'foo', title: '%s matching “foo”
  #     params.reset({})            # title: '%s in document set'
  #
  # These five title strings come from i18n constants defined here. Plugins can
  # pass their own i18n strings.
  class DocumentListParams
    constructor: (@state, @view, options={}) ->
      @title = options.title || t('all')
      @params = {}
      for k, type of Attributes
        @params[k] = type.toParam(options[k]) if k of options

      @reset = @_buildReset()

    # Return some params with a different view
    withView: (view) ->
      return @ if view == @view
      newParams = if @params.nodes
        # Switching to a new view will _always_ break the Tree
        {}
      else
        _.extend({ title: @title }, @params)
      new DocumentListParams(@state, view, newParams)

    # Returns a String representation, useful for debugging
    toString: ->
      parts = [ 'DocumentListParams(' ]
      for k, type of Attributes
        parts.push("#{k}=#{type.toString(@params[k])}") if k of @params
      parts.push(')')
      parts.join('')

    # Returns a JSON representation, useful for debugging.
    #
    # This is simply @params.
    toJSON: -> @params

    # Returns true iff rhs is certainly equivalent to this one.
    #
    # This means same state, view, title and params.
    equals: (rhs) ->
      @state == rhs.state && @view == rhs.view && @title == rhs.title && _.isEqual(@params, rhs.params)

    # Returns the parameters such that Overview servers can understand them.
    #
    # For instance, if we want ".../tag/34/add?nodes=2", then this method
    # should return `{ nodes: '2' }`.
    #
    # Overview's servers expect each ID array to be a single String, with
    # commas delimiting each ID.
    #
    # If there is a View, this selection's return value will be passed through
    # View.addScopeToQueryParams(). For instance, a Tree view can add a `node`
    # property to selections that don't already have one.
    #
    # Note that query params may be rather verbose. You should use POST
    # requests where these parameters are involved.
    toQueryParams: ->
      queryParams = {}
      for k, type of Attributes
        queryParams[k] = type.toQueryParam(@params[k]) if k of @params

      if @view?
        @view.addScopeToQueryParams(queryParams)
      else
        queryParams

    toQueryString: ->
      arr = []
      for k, v of @toQueryParams()
        arr.push("#{encodeURIComponent(k)}=#{encodeURIComponent(v)}")
      arr.join('&')

    _buildReset: ->
      reset = (options={}) =>
        realOptions = _.extend({}, options)
        if 'title' not of realOptions
          keys = Object.keys(realOptions)
          realOptions.title = if keys.length == 0
            t('all')
          else if keys.length == 1
            if keys[0] == 'nodes' && options.nodes.length == 1
              t('node', @view?.onDemandTree?.getNode?(options.nodes[0])?.description)
            else if keys[0] == 'tags' && options.tags.length == 1
              t('tag', @state?.tags?.get?(options.tags[0])?.attributes?.name)
            else if keys[0] == 'tagged' && !options.tagged
              t('untagged')
            else if keys[0] == 'q'
              t('q', options.q)
            else
              undefined
          else
            undefined

        new DocumentListParams(@state, @view, realOptions)

      reset.byNode = (node) -> reset(nodes: [ node.id ], title: t('node', node.description))
      reset.byTag = (tag) -> reset(tags: [ tag.id ], title: t('tag', tag.attributes.name))
      reset.byUntagged = -> reset(tagged: false, title: t('untagged'))
      reset.byQ = (q) -> reset(q: q, title: t('q', q))
      reset.all = -> reset(title: t('all'))
      reset
