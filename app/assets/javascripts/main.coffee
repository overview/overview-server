app = require('app')

OnDemandTree = require('models/on_demand_tree').OnDemandTree

store = new app.models.Store()
state = new app.models.State()

server = new app.models.Server()
needs_resolver = new app.models.NeedsResolver(store, server)

log = require('globals').logger
app.controllers.log_controller(log, server)

tree = new OnDemandTree(needs_resolver)
tree.demand_root()

jQuery ($) ->
  $('#tree').each () ->
    app.controllers.tree_controller(this, tree, state, needs_resolver)
  $('#document-list').each () ->
    app.controllers.document_list_controller(this, store, needs_resolver, state.selection)
  $('#document').each () ->
    app.controllers.document_contents_controller(this, state.selection, server.router)
