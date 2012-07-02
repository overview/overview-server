observable = require('models/observable').observable

class PartialTree
  observable(this)

  constructor: (@needs_resolver) ->
    @root = undefined
    @_nodes = {}
    @_child_id_to_parent_id = {}

    @needs_resolver.get_deferred('root').done (json) =>
      nodes_json = json.nodes
      this.add_nodes_json(nodes_json)

  add_nodes_json: (nodes_json) ->
    for node_json in nodes_json
      continue if @_nodes[node_json.id]

      node = {
        id: node_json.id,
        children: node_json.children,
        description: node_json.description,
        doclist: node_json.doclist,
        taglist: node_json.taglist,
      }
      @_nodes[node.id] = node

      for child_id in node.children
        @_child_id_to_parent_id[child_id] = node.id

    for _, node of @_nodes
      node.children = (@_nodes[node_or_id] || node_or_id for node_or_id in node.children)

    rootid = nodes_json[0].id
    @root ||= @_nodes[rootid]

    this._notify('change')

  get_node: (nodeid) ->
    @_nodes[nodeid]

exports = require.make_export_object('models/partial_tree')
exports.PartialTree = PartialTree
