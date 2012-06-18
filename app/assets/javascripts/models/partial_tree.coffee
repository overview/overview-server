class PartialTree
  constructor: () ->
    @root = undefined

  addNodesJson: (nodes_json) ->
    @root = nodes_json[0]

exports = require.make_export_object('models/partial_tree')
exports.PartialTree = PartialTree
