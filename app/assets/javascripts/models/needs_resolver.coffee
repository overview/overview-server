Server = require('models/server').Server

resolve_root = (needs_resolver) ->
  needs_resolver.server.get('root').done (obj) ->
    store = needs_resolver.store
    for node in obj.nodes
      store.nodes.add(node)
    for tag in obj.tags
      store.tags.add(tag)
    for document in obj.documents
      store.documents.add(document)
    console.log(store)

resolve_selection_documents_slice = (needs_resolver, obj) ->
  list_to_param = (list) ->
    (item.id? && item.id || item for item in list).join(',')

  params = {
    start: obj.start,
    end: obj.end,
  }
  for key in [ 'nodes', 'tags', 'documents' ]
    list = obj.selection[key]
    if list?.length
      params[key] = list_to_param(obj.selection[key])

  needs_resolver.server.get('documents', { data: params })

RESOLVERS = {
  root: resolve_root,
  selection_documents_slice: resolve_selection_documents_slice,
}

class NeedsResolver
  constructor: (@store, server=undefined) ->
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

exports = require.make_export_object('models/needs_resolver')
exports.NeedsResolver = NeedsResolver
