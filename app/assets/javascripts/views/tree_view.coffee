$ = jQuery

tree_view = (div, partial_tree) ->
  node_to_div = (node) ->
    $ret = $('<div><span class="description"></span></div>')
    $ret.children('.description').text(node.description)
    if node.children?.length
      $ul = $('<ul></ul>')
      for child_node in node.children
        $li = $('<li></li>')
        if child_node?.id? # it's a node
          $li.append(node_to_div(child_node))
        else # it's a nodeid -- unresolved
          $li.append("(unresolved Node ##{child_node})")
        $ul.append($li)
      $ret.append($ul)
    $ret[0]

  redraw = () ->
    $div = $(div)

    $div.empty()

    $div.append(node_to_div(partial_tree.root)) if partial_tree?.root?



  {
    redraw: redraw,
    # Insert public methods here
  }

exports = require.make_export_object('views/tree_view')
exports.tree_view = tree_view
