class PartialTree
  constructor: (@needs_resolver) ->
    @root = undefined
    @callbacks = { change: [] }

    @needs_resolver.get_deferred('root').done (json) =>
      nodes_json = json.nodes
      this.add_nodes_json(nodes_json)

  add_nodes_json: (nodes_json) ->
    nodes = {}

    for node_json in nodes_json
      node = {
        id: node_json.id,
        children: node_json.children,
        description: node_json.description,
        doclist: node_json.doclist,
        taglist: node_json.taglist,
      }
      nodes[node.id] = node

    for _, node of nodes
      node.children = (nodes[id] || id for id in node.children)

    rootid = nodes_json[0].id
    @root = nodes[rootid]
    console.log(@root)
    this._trigger('change')

  on: (event_name, callback) ->
    @callbacks[event_name].push(callback)

  _trigger: (event_name) ->
    for callback in @callbacks[event_name]
      callback()

exports = require.make_export_object('models/partial_tree')
exports.PartialTree = PartialTree
