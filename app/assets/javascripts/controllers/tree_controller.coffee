tree_view = require('views').tree_view

tree_controller = (div, partial_tree, state) ->
  view = tree_view(div, partial_tree, state)

  view.on 'node_clicked', (node) ->
    state.selection.update({ node: node })

  view.redraw()
  partial_tree.observe('change', view.redraw)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
