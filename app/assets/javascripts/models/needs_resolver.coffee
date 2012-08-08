Server = require('models/server').Server

list_to_hash = (list_of_objects) ->
  ret = {}
  ret[o.id] = o for o in list_of_objects
  ret

handle_node_resolved = (needs_resolver, obj) ->
  document_store = needs_resolver.document_store
  documents_hash = list_to_hash(obj.documents)
  for node in obj.nodes
    document_store.add_doclist(node.doclist, documents_hash)

resolve_root = (needs_resolver) ->
  needs_resolver.server.get('root').done (obj) ->
    handle_node_resolved(needs_resolver, obj)

    documents_hash = list_to_hash(obj.documents)

    tag_store = needs_resolver.tag_store
    document_store = needs_resolver.document_store

    for tag in obj.tags
      document_store.add_doclist(tag.doclist, documents_hash)
      tag_store.add(tag)

resolve_node = (needs_resolver, id) ->
  needs_resolver.server.get('node', { path_argument: id }).done (obj) ->
    handle_node_resolved(needs_resolver, obj)

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
  node: resolve_node,
  selection_documents_slice: resolve_selection_documents_slice,
}

class NeedsResolver
  constructor: (@document_store, @tag_store, server=undefined) ->
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
