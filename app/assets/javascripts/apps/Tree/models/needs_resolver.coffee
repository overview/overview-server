define [ './server' ], (Server) ->
  resolve_root = (needs_resolver) ->
    needs_resolver.server.get('root').done (obj) ->
      tag_store = needs_resolver.tag_store
      for tag in obj.tags
        tag_store.add(tag)

      search_result_store = needs_resolver.search_result_store
      for search_result in obj.searchResults
        if search_result.state == 'InProgress'
          search_result_store.addAndPoll(search_result)
        else
          search_result_store.add(search_result)

  resolve_node = (needs_resolver, id) ->
    needs_resolver.server.get('node', { path_argument: id })

  resolve_selection_documents_slice = (needs_resolver, obj) ->
    needs_resolver.server.get('documents', { data: obj })

  RESOLVERS = {
    root: resolve_root,
    node: resolve_node,
    selection_documents_slice: resolve_selection_documents_slice,
  }

  class NeedsResolver
    constructor: (@tag_store, @search_result_store, server=undefined) ->
      @server = server || new Server()

      @needs = {}

    get_deferred: (type, key=undefined, arg=undefined) ->
      full_key = if key instanceof String || !key?
        "#{type}-#{key}"
      else
        arg = key
        undefined

      return @needs[full_key] if full_key? && full_key in @needs

      ret = RESOLVERS[type](this, arg)

      @needs[full_key] = ret if full_key?
      ret
