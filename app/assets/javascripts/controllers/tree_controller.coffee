TreeView = require('views/tree_view').TreeView
AnimatedTree = require('models/animated_tree').AnimatedTree
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

log = require('globals').logger.for_component('tree')

# Shim window.requestAnimationFrame(), as per
# http://my.opera.com/emoller/blog/2011/12/20/requestanimationframe-for-smart-er-animating
(->
  last_time = 0
  vendors = [ 'ms', 'moz', 'webkit', 'o' ]
  for vendor in vendors
    break if window.requestAnimationFrame
    window.requestAnimationFrame = window["#{vendor}RequestAnimationFrame"]

  if !window.requestAnimationFrame
    window.requestAnimationFrame = (callback) ->
      cur_time = new Date().getTime()
      time_to_call = Math.max(0, 16 - (cur_time - last_time))
      id = window.setTimeout((-> callback(cur_time + time_to_call)), time_to_call)
      last_time = cur_time + time_to_call
      id
)()

tree_controller = (div, cache, focus, state) ->
  interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
  animator = new Animator(interpolator)
  animated_tree = new AnimatedTree(cache.on_demand_tree, state, animator)
  view = new TreeView(div, cache, animated_tree, focus)

  animating = false
  animate_frame = () ->
    if view.needs_update()
      view.update()
      requestAnimationFrame(animate_frame)
    else
      animating = false
  animate = () ->
    if !animating
      animating = true
      requestAnimationFrame(animate_frame)

  # XXX maybe move the focus tag into AnimatedTree?
  state.observe('focused_tag-changed', -> cache.on_demand_tree.id_tree.edit(->))

  view.observe('needs-update', animate)

  view.observe 'click', (nodeid) ->
    return if !nodeid?
    log('clicked node', "#{nodeid}")
    new_selection = state.selection.replace({ nodes: [nodeid], tags: [], documents: [] })

    node = cache.on_demand_tree.nodes[nodeid]
    if node?.doclist?.n == 1
      new_selection = new_selection.replace({ documents: [ node.doclist.docids[0] ] })

    state.set('selection', new_selection)

  view.observe 'expand', (nodeid) ->
    return if !nodeid?
    log('expanded node', "#{nodeid}")
    cache.on_demand_tree.demand_node(nodeid)

  view.observe 'collapse', (nodeid) ->
    return if !nodeid?
    log('collapsed node', "#{nodeid}")
    cache.on_demand_tree.unload_node_children(nodeid)

  view.observe 'zoom-pan', (obj) ->
    log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
    focus.set_zoom(obj.zoom)
    focus.set_pan(obj.pan)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
