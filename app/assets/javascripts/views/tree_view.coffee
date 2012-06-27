$ = jQuery

COLOR_UNSELECTED = '#ccd'
COLOR_SELECTED = '#bbb'

DomIdGenerator = require('models/dom_id_generator').DomIdGenerator
tree_view_id_generator = new DomIdGenerator('tree-view')

tree_view = (div, partial_tree, state) ->
  icicle = undefined

  tree_node_to_icicle_node = (node) ->
    if node?.id?
      {
        id: node.id,
        name: node.description,
        data: {
          '$area': node.doclist?.n || 1,
          '$dim': node.doclist?.n || 1,
          '$color': COLOR_UNSELECTED,
        },
        children: (tree_node_to_icicle_node(child_node) for child_node in (node.children || [])),
      }
    else
      {
        id: node || 0,
        name: '(not yet loaded)',
        data: {
          '$area': 1,
          '$dim': 1,
          '$color': COLOR_UNSELECTED,
        },
        children: []
      }

  selected_nodeids = []

  refresh_selection = () ->
    for nodeid in selected_nodeids
      node = icicle.graph.getNode(nodeid)
      if node
        node.setData('color', COLOR_UNSELECTED)

    selected_nodeids = ((node?.id? && node.id || node) for node in state.selection.nodes)

    for nodeid in selected_nodeids
      node = icicle.graph.getNode(nodeid)
      if node
        node.setData('color', COLOR_SELECTED)

    icicle.fx.animate({ modes: ['node-property:color'] })

  redraw = () ->
    $div = $(div)

    $div.empty()
    $child = $('<div></div>')
    $child.css({
      position: 'absolute',
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
    })
    $div.append($child)

    icicle = new $jit.Icicle({
      injectInto: tree_view_id_generator.node_to_guaranteed_dom_id($child[0]),
      orientation: 'v',
      offset: 1,
      cushion: false,
      Events: {
        enable: true,
        onClick: (icicle_node) ->
          node = icicle_node?.id && partial_tree.get_node(icicle_node.id) || icicle_node
          $(div).trigger('tree_view:node_clicked', [node])
      },
    })

    icicle_data = tree_node_to_icicle_node(partial_tree.root)
    icicle.loadJSON(icicle_data)
    icicle.refresh()

  on_ = (event, callback) ->
    $(div).on("tree_view:#{event}", (e, node) -> callback(node))

  state.observe 'selection', () ->
    refresh_selection()

  {
    redraw: redraw,
    on: on_,
    # Insert public methods here
  }

exports = require.make_export_object('views/tree_view')
exports.tree_view = tree_view
