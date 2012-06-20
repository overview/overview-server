$ = jQuery

tree_view = (div, partial_tree, state) ->
  node_to_div = (node) ->
    $ret = $('<div><span class="description"></span></div>')
    $ret.children('.description').text(node.description)
    $ret.attr('id', "tree-node-#{node.id}")

    if node.children?.length
      $ul = $('<ul></ul>')
      for child_node in node.children
        $li = $('<li></li>')
        if child_node?.id? # it's a node
          $li.append(node_to_div(child_node))
        else # it's a nodeid -- unresolved
          $li.append("<div><span class=\"description\">(unresolved Node ##{child_node})</span></div>")
          $li.children('div').attr('id', "tree-node-#{child_node}")
        $ul.append($li)
      $ret.append($ul)
    $ret[0]

  refresh_selection = () ->
    $div = $(div)
    $div.find('[id]').removeClass('selected')
    for node in state.selection.nodes
      nodeid = node.id? && node.id || node
      $div.find("#tree-node-#{nodeid}").addClass('selected')

  redraw = () ->
    $div = $(div)

    $div.empty()

    $div.append(node_to_div(partial_tree.root)) if partial_tree?.root?
    refresh_selection()

  $(div).on 'click', (e) ->
    $elem = $(e.target).closest('[id]')
    id = $elem.attr('id')
    id_parts = id.split(/-/g)

    nodeid = + id_parts[id_parts.length - 1]
    node = partial_tree.get_node(nodeid)

    $(div).trigger('tree_view:node_clicked', [node || nodeid])

  on_ = (event, callback) ->
    $(div).on("tree_view:#{event}", (e, node) -> callback(node))

  state.on_change 'selection', () ->
    refresh_selection()

  {
    redraw: redraw,
    on: on_,
    # Insert public methods here
  }

exports = require.make_export_object('views/tree_view')
exports.tree_view = tree_view
