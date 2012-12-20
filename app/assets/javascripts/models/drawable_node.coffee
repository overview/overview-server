DEFAULT_OPTIONS = {
  node_hpadding: 2, # per cent of a 1-doc node's width
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
#   should be, relative to center of its parent.
class DrawableNode

  # Combine boundary lists of two sibling trees, to answer: 
  # how far does the merged tree stick out to the right/left, at each depth?
  # a and b are boundary lists of possibly different lengths (if subtrees have different depth) 
  # f is a function to merge elements at same depth: Math.min for left boundaries, Math.max for right
  merge_boundaries: (a, b, f) ->
  	max_a = a.length
  	max_b = b.length
  	merged = []
  	for i in [0 .. Math.max(max_a, max_b)-1]
      if i<max_a && i<max_b
        m = f(a[i], b[i])
      else if i<max_a
      	m = a[i]
      else
      	m = b[i]
      merged.push(m)
    merged   	
  		 
  # places moveable_subtree as far left as possible, so that nodes have at least hpadding space between them at closest point
  # takes right_boundary contour, in coordinates of parent node, and returns new right_boundary 
  pack_subtree: (right_boundary, moveable_subtree, hpadding) ->
    left_boundary = moveable_subtree.left_boundary

    # positive means trees do not touch, negative indicates overlap on some level
    separation = Number.MAX_VALUE
    
    # walk down each level that exists in both subtrees
    for i in [0 .. Math.min(right_boundary.length,left_boundary.length)-1]
     separation = Math.min(separation, left_boundary[i] - right_boundary[i])    

    # set relative_x that will give hpadding spacing at closest point between trees
    moveable_subtree.relative_x = hpadding - separation 
    
    # return new right boundary, by merging in new right boundary of moved tree
    moved_right_boundary = (moveable_subtree.relative_x + x for x in  moveable_subtree.right_boundary)  
    @merge_boundaries(right_boundary, moved_right_boundary, Math.max)
    

  constructor: (@animated_node, @fraction) ->
    num_documents = @animated_node.node.doclist.n
    @width = num_documents * @fraction

    hpadding = DEFAULT_OPTIONS.node_hpadding * @fraction

    animated_children = animated_node.children
    if !animated_children?.length
      # Leaf node (with the current configuration of open nodes)
      @height = @fraction
      @left_boundary = [0]
      @right_boundary = [@width]
      
    else
      # Non-leaf node. Recurse, construct all child nodes
      child_fraction = @fraction * @animated_node.loaded_fraction.current
      @children = animated_children.map((an) -> new DrawableNode(an, child_fraction))
      @height = @fraction + _(dn.height for dn in @children).max()

      firstchild = @children[0]
      lastidx = @children.length - 1
      lastchild = @children[lastidx]

      # spacing between subtrees at this level is a fraction of current node width (but don't get too small)
      #subtree_spacing = Math.max(hpadding * @width/16, hpadding)
      subtree_spacing = hpadding
      
      # pack child trees as close to each other as possible, from left to right 
      firstchild.relative_x = 0
      children_right_boundary = firstchild.right_boundary
      for i in [1 ..lastidx]
      	children_right_boundary = @pack_subtree(children_right_boundary, @children[i], subtree_spacing)

      # center the packed child tress under this node, by adding appropriate offset to relative_x 
      children_width = lastchild.relative_x + lastchild.width
      shift = @width/2 - children_width/2
      child.relative_x += shift for child in @children
      
      # create our left and right boundary lists
      @left_boundary = @children.reduce(( (cumulative, current) -> @merge_boundaries(cumulative, (x + shift for x in current), Math.min) ), [])
      @left_boundary.unshift(0)
      @right_boundary = (x + shift for x in children_right_boundary)
      @right_boundary.unshift(@width)
      
      # unreference child boundary lists since we're done with them
      for child in @children
        child.left_boundary = null 
        child.right_boundary = null
      
      # convert relative_x to give coordinate of center of child node relative to center of parent
      for child in @children
      	child.relative_x += child.width/2 - @width/2      
            	

exports = require.make_export_object('models/drawable_node')
exports.DrawableNode = DrawableNode
