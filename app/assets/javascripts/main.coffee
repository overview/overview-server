app = require('app')
globals = require('globals')

store = new app.models.Store()
state = new app.models.State()

needs_resolver = new app.models.NeedsResolver(store)
tree = new app.models.PartialTree(needs_resolver)

jQuery ($) ->
  $('#tree').each () ->
    app.controllers.tree_controller(this, tree, state)
