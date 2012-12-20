DEFAULT_OPTIONS = {
  node_hpadding: 1, # fraction of a 1-doc node's width
  node_vpadding: 0.5, # fraction of a node's height
}

# A DrawableNode is a node that is ready to draw. It's constructed from a corresponding AnimatedNode,
# and is responsible for tree layout.
#
# It has the following properties:
#
# * animated_node: the node it's drawing
#
# * fraction: how complete the parent is, animation-wise (1.0 means animation
#   is complete)
#
# * width: How many units wide the node should be. 1 unit = 1 document.
#
# * height: How many units high the node should be. 1 unit = 1 fully-loaded
#   node. (If animation isn't complete, width and height can be lower than 1.)
#
# * left_contour / right_contour: an array which gives the coordinates of the 
#   left/right-most node at each level of the tree, including this one.
#   coordinates relative to left edge of current node.
#
# * children: Child DrawableNodes, built using @animated_node's children.
#
# * relative_x: this is the primary value that the layout algorithm sets.
#   Specifies how many units to the left (negative) or right (positive) the center 
#   of a DrawableNode should be, relative to center of its parent.
#   However, during intermediate computation this value is actually left edge of child 
#   relative to left edge of parent.
#
class DrawableNode

  # Combine contours of two sibling trees, to answer: 
  # how far does the merged tree stick out to the right/left, at each depth?
  # a and b are contours of possibly different lengths (if subtrees have different depth) 
  # f is a function to merge elements at same depth: Math.min for left boundaries, Math.max for right
  merge_contours: (a, b, f) ->
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
  		 
  # places moveable_node as far left as possible, so that nodes in subtrees have at least hpadding space between them at closest point
  # fixed_node is sibling to movable_node, immediately to left, right_contour is in coordinates of parent node.
  # fraction indicates where parent node is in opening animation
  pack_subtree: (fixed_node, moveable_node, right_contour, hpadding, fraction) ->
    left_contour = moveable_node.left_contour

    # positive separation means trees do not touch, negative indicates overlap on some level
    separation = Number.MAX_VALUE
    
    # walk down each level that exists in both subtrees
    for i in [0 .. Math.min(right_contour.length,left_contour.length)-1]
     separation = Math.min(separation, left_contour[i] - right_contour[i])    

    # set relative_x that will give hpadding spacing at closest point between trees
    # then interpolate with where node would be (right next to sibling fixed_node) if children not open
    fraction *= moveable_node.animated_node.loaded_fraction.current
    open_relative_x = hpadding - separation 
    closed_relative_x = fixed_node.relative_x + fixed_node.width + hpadding 
    moveable_node.relative_x = fraction*open_relative_x + (1-fraction)*closed_relative_x
    
    
  # build a tree of DrawableNodes out of a tree of AnimatedNodes, where the root node is @fraction opened
  constructor: (@animated_node, @fraction, level) ->
    num_documents = @animated_node.node.doclist.n
    @width = num_documents * @fraction

    hpadding = DEFAULT_OPTIONS.node_hpadding * @fraction

    animated_children = animated_node.children
    if !animated_children?.length
      # Leaf node (with the current configuration of open nodes)
      @height = @fraction
      @left_contour = [0]
      @right_contour = [@width]
      
    else
    	
      # Non-leaf node. Recurse, construct all child nodes
      child_fraction = @fraction * @animated_node.loaded_fraction.current
      l1 = level + 1 # doesn't work if I replace l1 in call below, but why?
      @children = animated_children.map((an) -> new DrawableNode(an, child_fraction, l1))
      @height = @fraction + _(child.height for child in @children).max()

      firstchild = @children[0]
      lastidx = @children.length - 1
      lastchild = @children[lastidx]

      # min spacing between subtrees decreases as we go down the tree
      decreasing_level = Math.max(6-level, 1)
      subtree_spacing = decreasing_level*decreasing_level * hpadding * child_fraction   # square to make spacing fall off non-linearly
      
      # start with our left and right contours equal to that of first subtree
      @left_contour = firstchild.left_contour
      @right_contour = firstchild.right_contour
      
      # pack child trees as close to each other as possible, from left to right, updating contours of all subtrees so far as we go
      firstchild.relative_x = 0
      for i in [1 ..lastidx]
        @pack_subtree(@children[i-1], @children[i], @right_contour, subtree_spacing, child_fraction)

        # update contours based on just-placed sub-tree
        child_left_contour = (@children[i].relative_x + x for x in  @children[i].left_contour)  
        child_right_contour = (@children[i].relative_x + x for x in  @children[i].right_contour)  
        @left_contour = @merge_contours(@left_contour, child_left_contour, Math.min)
        @right_contour = @merge_contours(@right_contour, child_right_contour, Math.max)

      # now center the packed child trees under this node, be adding offset to relative_x
      children_width = lastchild.relative_x + lastchild.width
      center_shift = @width/2 - children_width/2
      for child in @children
      	child.relative_x += center_shift 
      
      # create our final left and right contours by shifting to center, and adding extent of this node at top level
      @left_contour = (x + center_shift for x in @left_contour)
      @left_contour.unshift(0)
      @right_contour = (x + center_shift for x in @right_contour)
      @right_contour.unshift(@width)
      
      # unreference child contour lists since we're done with them
      for child in @children
        child.left_contour = null 
        child.right_contour = null
      
      # up to this point relative_x gives left edge of node relative to left edge of parent
      # convert to center of child node relative to center of parent, as draw code expects
      for child in @children
      	child.relative_x += child.width/2 - @width/2      
            	

exports = require.make_export_object('models/drawable_node')
exports.DrawableNode = DrawableNode
