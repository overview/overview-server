app = require('app')

OnDemandTree = require('models/on_demand_tree').OnDemandTree
AnimatedFocus = require('models/animated_focus').AnimatedFocus
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

focus_controller = require('controllers/focus_controller').focus_controller

store = new app.models.Store()
state = new app.models.State()

server = new app.models.Server()
needs_resolver = new app.models.NeedsResolver(store, server)

log = require('globals').logger
app.controllers.log_controller(log, server)

tree = new OnDemandTree(needs_resolver)
tree.demand_root()

interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
animator = new Animator(interpolator)
focus = new AnimatedFocus(animator)

jQuery ($) ->
  $('#focus').each () ->
    focus_controller(this, focus)
  $('#tree').each () ->
    app.controllers.tree_controller(this, tree, focus, state.selection)
  $('#document-list').each () ->
    app.controllers.document_list_controller(this, store, needs_resolver, state.selection)
  $('#document').each () ->
    app.controllers.document_contents_controller(this, state.selection, server.router)
