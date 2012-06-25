Server = require('models/server').Server

resolve_root = (needs_resolver) ->
  needs_resolver.server.get('root')

resolve_documents = (needs_resolver, selection) ->
  needs_resolver.server.get(
    'documents',
    (node.id? && node.id || node for node in selection.nodes),
    (document.id? && document.id || document for document in selection.documents),
    (tag.id? && tag.id || tag for tag in selection.tags)
  )

RESOLVERS = {
  root: resolve_root,
  documents: resolve_documents,
}

class NeedsResolver
  constructor: (@store, server=undefined) ->
    @server = server || new Server()

    @needs = {}

  get_deferred: (type, key=undefined, arg=undefined) ->
    key = "#{type}-#{key}"

    return @needs[key] if key in @needs

    @needs[key] = RESOLVERS[type](this, arg)

exports = require.make_export_object('models/needs_resolver')
exports.NeedsResolver = NeedsResolver
