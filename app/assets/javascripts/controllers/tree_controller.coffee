TreeView = require('views').TreeView
log = require('globals').logger.for_component('tree')

tree_controller = (div, on_demand_tree, state) ->
  view = new TreeView(div, on_demand_tree)

  view.observe 'click', (nodeid) ->
    log('clicked node', "#{nodeid}")
    state.selection.update({ node: nodeid })
    on_demand_tree.demand_node(nodeid)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
