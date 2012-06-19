app = require('app')
globals = require('globals')

state = new app.models.State()

needs_resolver = new app.models.NeedsResolver()
tree = new app.models.PartialTree(needs_resolver)

jQuery ($) ->
  $('#tree').each () ->
    app.controllers.tree_controller(this, tree, state)
