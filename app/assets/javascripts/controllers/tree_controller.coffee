TreeView = require('views/tree_view').TreeView
AnimatedTree = require('models/animated_tree').AnimatedTree
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

log = require('globals').logger.for_component('tree')

tree_controller = (div, on_demand_tree, focus, state) ->
  interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
  animator = new Animator(interpolator)
  animated_tree = new AnimatedTree(on_demand_tree, state, animator)
  view = new TreeView(div, animated_tree, focus)

  interval = undefined
  maybe_update = () ->
    if view.needs_update()
      view.update()
    else
      window.clearInterval(interval)
  start_update_interval = () ->
    interval = window.setInterval(maybe_update, 20)

  # XXX maybe move the focus tag into AnimatedTree?
  state.observe('focused_tag-changed', -> on_demand_tree.id_tree.edit(->))

  view.observe('needs-update', start_update_interval)

  view.observe 'click', (nodeid) ->
    return if !nodeid?
    log('clicked node', "#{nodeid}")
    new_selection = state.selection.replace({ nodes: [nodeid], tags: [], documents: [] })

    node = on_demand_tree.nodes[nodeid]
    if node?.doclist?.n == 1
      new_selection = new_selection.replace({ documents: [ node.doclist.docids[0] ] })

    state.set('selection', new_selection)

  view.observe 'expand', (nodeid) ->
    return if !nodeid?
    log('expanded node', "#{nodeid}")
    on_demand_tree.demand_node(nodeid)

  view.observe 'collapse', (nodeid) ->
    return if !nodeid?
    log('collapsed node', "#{nodeid}")
    on_demand_tree.unload_node_children(nodeid)

  view.observe 'zoom-pan', (obj) ->
    log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
    focus.set_zoom(obj.zoom)
    focus.set_pan(obj.pan)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
