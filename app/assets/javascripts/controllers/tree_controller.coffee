tree_view = require('views').tree_view

tree_controller = (div, partial_tree) ->
  view = tree_view(div, partial_tree)

  view.redraw()
  partial_tree.on('change', view.redraw)

exports = require.make_export_object('controllers/tree_controller')
exports.tree_controller = tree_controller
