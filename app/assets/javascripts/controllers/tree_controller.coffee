TreeView = require('views/tree_view').TreeView
AnimatedTree = require('models/animated_tree').AnimatedTree
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

log = require('globals').logger.for_component('tree')

tree_controller = (div, on_demand_tree, state) ->
  interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
  animator = new Animator(interpolator)
  animated_tree = new AnimatedTree(on_demand_tree, state.selection, animator)
  view = new TreeView(div, animated_tree)

  interval = undefined
  maybe_update = () ->
    if view.needs_update()
      view.update()
    else
      window.clearInterval(interval)
  start_update_interval = () ->
    interval = window.setInterval(maybe_update, 20)

  view.observe('needs-update', start_update_interval)

  view.observe 'click', (nodeid) ->
    log('clicked node', "#{nodeid}")
    state.selection.update({ node: nodeid })
    on_demand_tree.demand_node(nodeid)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
