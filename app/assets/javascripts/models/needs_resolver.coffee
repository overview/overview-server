Server = require('models/server').Server

resolve_root = (needs_resolver) ->
  needs_resolver.server.get('root')

RESOLVERS = {
  root: resolve_root,
}

class NeedsResolver
  constructor: (@store, server=undefined) ->
    @server = server || new Server()

    @needs = {}

  get_deferred: (type, arg=undefined) ->
    key = "#{type}-#{arg}"

    return @needs[key] if key in @needs

    @needs[key] = RESOLVERS[type](this, arg)

exports = require.make_export_object('models/needs_resolver')
exports.NeedsResolver = NeedsResolver
