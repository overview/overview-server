DEFAULT_OPTIONS = {
  node_hpadding: 0.5, # per cent of a 1-doc node's width
  node_vpadding: 0.5, # per cent of a node's height
}

# A DrawableNode is a node that is ready to draw.
#
# It has the following properties:
#
# * animated_node: the node it's drawing
# * fraction: how complete the parent is, animation-wise (1.0 means animation
#   is complete)
# * width: How many units wide the node should be. 1 unit = 1 document.
# * height: How many units high the node should be. 1 unit = 1 fully-loaded
#   node. (If animation isn't complete, width and height can be lower than 1.)
# * width_with_padding: width when factoring in "padding". Each node has a
#   certain amount of horizontal padding on its left and right; this width
#   includes them both.
# * children: Child DrawableNodes, built using @animated_node's children.
#   Children may affect width_with_padding, but they won't affect width.
# * relative_x: set automatically on all children. Specifies how many units
#   to the left (negative) or right (positive) the center of a DrawableNode
#   should be, relative to its parent.
class DrawableNode
  constructor: (@animated_node, @fraction) ->
    num_documents = @animated_node.node.doclist.n
    @width = num_documents * @fraction

    hpadding = DEFAULT_OPTIONS.node_hpadding * @fraction

    animated_children = animated_node.children
    if !animated_children?.length
      # Leaf node (with the current configuration of open nodes)
      @width_with_padding = @width + (2 * hpadding)
      @height = @fraction
    else
      # Non-leaf node
      child_fraction = @fraction * @animated_node.loaded_fraction.current

      @children = animated_children.map((an) -> new DrawableNode(an, child_fraction))

      parent_width_with_padding = @width + (2 * hpadding)
      children_width_with_padding = @children.reduce(((s, dn) -> s + dn.width_with_padding), 0) + (@children.length + 1) * hpadding
      @width_with_padding = if parent_width_with_padding > children_width_with_padding
        parent_width_with_padding
      else
        children_width_with_padding

      @height = @fraction + _(dn.height for dn in @children).max()

      x = -0.5 * children_width_with_padding + hpadding
      for child in @children
        child.relative_x = x + child.width_with_padding * 0.5
        x += child.width_with_padding + hpadding

exports = require.make_export_object('models/drawable_node')
exports.DrawableNode = DrawableNode
