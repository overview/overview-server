app = require('app')
globals = require('globals')

store = new app.models.Store()
state = new app.models.State()

needs_resolver = new app.models.NeedsResolver(store)
tree = new app.models.PartialTree(needs_resolver)

jQuery ($) ->
  $('#tree').each () ->
    app.controllers.tree_controller(this, tree, state, needs_resolver)
  $('#document-list').each () ->
    app.controllers.document_list_controller(this, store, needs_resolver, state.selection)
  $('#document').each () ->
    app.controllers.document_contents_controller(this, state.selection, needs_resolver.server.router)
