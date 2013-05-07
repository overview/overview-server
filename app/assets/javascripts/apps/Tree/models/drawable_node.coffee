define ->
  DEFAULT_OPTIONS = {
    node_vpadding: 0.5, # fraction of a node's height
    min_spacing_level: 8 # all levels >= this have minimum inter-subtree spacing
    reference_spacing_docset_size : 1000 # fully zoomed out tree should have spacing between nodes that we get with docset of this size
  }

  # Extends the contour in-place using information from the new one.
  #
  # The contour will be returned.
  #
  # For instance, if contour is [ 1, 2, 3 ], other_contour is [ 2, -1 ],
  # other_contour_offset is 1 and left_or_right is 'right', contour will
  # [ 3, 2, 3 ].
  #
  # The contour determines how much a subtree "sticks out" with respect to its
  # root, at each level.
  extend_contour = (contour, other_contour, other_contour_offset, left_or_right) ->
    func = left_or_right == 'right' && Math.max || Math.min

    for i in [ 0 ... contour.length ]
      if i < other_contour.length
        contour[i] = func(contour[i], other_contour[i] + other_contour_offset)

    if contour.length < other_contour.length
      for i in [ contour.length ... other_contour.length ]
        contour.push(other_contour[i] + other_contour_offset)

    contour

  # Moves each element in contour by the given offset, in-place.
  #
  # Returns the contour.
  move_contour = (contour, offset) ->
    for i in [ 0 ... contour.length ]
      contour[i] = contour[i] + offset

    contour

  # places movable_node as far left as possible, so that nodes in subtrees have at least hpadding space between them at closest point
  # fixed_node is sibling to movable_node, immediately to left, right_contour is in coordinates of parent node.
  #
  # right_contour is what movable_node must be to the right of.
  #
  # fraction indicates where parent node is in opening animation
  pack_subtree = (fixed_node, movable_node, right_contour) ->
    left_contour = movable_node.__relative_position_info.left_contour
    spacing = movable_node.__spacing

    # positive separation means trees do not touch, negative indicates overlap on some level
    # walk down each level that exists in both subtrees
    separation = Number.MAX_VALUE
    for i in [0 ... Math.min(right_contour.length, left_contour.length)]
      separation = Math.min(separation, left_contour[i] - right_contour[i])

    # set relative_x that will give hpadding spacing at closest point between trees
    # then interpolate with where node would be (right next to sibling fixed_node) if children not open
    fraction = movable_node._fraction * movable_node.animated_node.loaded_fraction.current
    prev_relative_x = fixed_node.__relative_position_info.relative_x # previous node
    open_relative_x = spacing - separation
    closed_relative_x = prev_relative_x + fixed_node.__inner_width + spacing
    new_x = fraction * open_relative_x + (1 - fraction) * closed_relative_x
    movable_node.__relative_position_info.relative_x = new_x

  # A DrawableNode consists of an AnimatedNode, plus internal variables used for
  # drawing.
  #
  # Callers can ignore the internal variables and concentrate on the methods that
  # create (and cache) them:
  #
  # * animated_node: the node
  # * parent: parent DrawableNode, or undefined (set automatically)
  # * children(): the child DrawableNodes, `undefined` if not loaded, `[]` if
  #   leaf.
  # * walk_preorder(f): call `f` on all nodes in the sub-tree, parent first.
  # * walk_postorder(f): call `f` on all nodes in the sub-tree, children first.
  # * level(): distance from node to root, plus one
  # * outer_dims(), inner_dims(): objects with { left: Number, top: Number,
  #   width: Number, height: Number, hmid: Number }
  # * set_px_measurements(px_per_hunit, px_per_vunit, hpan_px): call before
  #   inner_px()
  # * inner_px: object with { left: Number, top: Number, width: Number,
  #   height: Number, hmid: Number }
  #
  # `dims` units are counted in documents (horizontally) and levels (vertically).
  #
  # `outer` units include padding. `inner` units are padding-free.
  #
  # `width` is usually proportional to the number of documents in the node, and
  # `height` is usually proportional to the node's level. But when the
  # animated_node's loaded_fraction isn't 1, that's not the case.
  #
  # It all sounds complicated, but the end result is:
  #
  #   root = DrawableNode(root_animated_node)
  #   width_in_units = root.outer_dims().width # width of whole tree
  #   root.set_px_measurements(px_per_hunit, px_per_vunit, hpan_px)
  #   root.walk_preorder (drawable_node) ->
  #     draw_node_at_px(drawable_node.animated_node, drawable_node.inner_px())
  #
  # The algorithm used for drawing is similar to that described at
  # http://emr.cs.iit.edu/~reingold/tidier-drawings.pdf
  class DrawableNode
    constructor: (@animated_node, @parent = undefined) ->

    children: () ->
      if @_done_children?
        @_children
      else
        @_done_children = true
        @_children = if @animated_node.children?
          new DrawableNode(an, this) for an in @animated_node.children
        else
          undefined

    # Calls the given function on all nodes, root first.
    walk_preorder: (f) ->
      f.call(this, this)
      children = @children()
      if children?
        c.walk_preorder(f) for c in children
      undefined

    # Calls the given function on all nodes, children first.
    walk_postorder: (f) ->
      children = @children()
      if children?
        c.walk_postorder(f) for c in children
      f.call(this, this)
      undefined

    # Calls the given function on all nodes, in undefined order.
    walk: (f) ->
      @walk_preorder(f)

    # Returns the topmost parent
    root: () ->
      return @_root if @_root?

      root = this
      root = root.parent while root.parent?

      @_root = root

    # Fraction complete this node is
    #
    # FIXME this variable is wonky and doesn't mean what it's supposed to.
    # Figure out what should happen and do that.
    fraction: () ->
      return @_fraction if @_fraction

      @root().walk_preorder (dn) ->
        dn._fraction = if dn.parent?
          dn.parent._fraction * dn.parent.animated_node.loaded_fraction.current
        else
          1 # root node is always loaded

      @_fraction

    # Distance from the root to the node, plus one.
    #
    # The root node's level is 1, its children are 2, etc.
    level: () ->
      return @_level if @_level?

      # After calling this method, @_level will be set on all nodes.
      @root().walk_preorder (dn) ->
        dn._level = (dn.parent?._level || 0) + 1

      @_level

    # Distance from the node to any sibling node.
    #
    # Further down the tree, the nodes get tighter together nonlinearly, reaches min at level 
    # DEFAULT_OPTIONS.min_spacing_level. Multiplies by number of nodes in the root relative to
    # EFAULT_OPTIONS.reference_spacing_docset_size, so that the nodes on level k have 
    # same spacing when tree viewed fully zoomed out, regardless of document set size.
    _spacing: () ->
      return @__spacing if @__spacing?

      # After calling this method, @__spacing will be set on all nodes.
      @level()
      @fraction()
      @walk (dn) ->
        decreasing_level = Math.max(1 + DEFAULT_OPTIONS.min_spacing_level - dn._level, 1)
        size_factor = @root().animated_node.node.doclist.n / DEFAULT_OPTIONS.reference_spacing_docset_size
        dn.__spacing = Math.pow(decreasing_level, 1.5) * dn._fraction * size_factor
       
      @__spacing

    # Width of the node itself, with no padding.
    _inner_width: () ->
      return @__inner_width if @__inner_width?

      # After calling this method, @__inner_width will be set on all nodes.
      @fraction()
      @walk (dn) ->
        dn.__inner_width = dn.animated_node.node.doclist.n * dn._fraction

      @__inner_width

    # Height of the node itself, with no padding.
    _inner_height: () ->
      return @__inner_height if @__inner_height?

      # After calling this method, @__inner_height will be set on all nodes.
      @fraction()
      @walk (dn) ->
        #children = dn.children()
        #dn.__inner_height = if children?
        #  Math.max((c.__inner_height for c in children)...) + dn._fraction
        #else
        #  dn._fraction
        dn.__inner_height = dn._fraction

      @__inner_height

    # Relative positions, with respect to parent.
    #
    # Algorithm variant of that in Reingold and Tilford, "Tidier Drawings of Trees", 1981 
    # http://emr.cs.iit.edu/~reingold/tidier-drawings.pdf
    #
    # This method adds a @__relative_position_info object to each node. The
    # Object has the following properties:
    #
    #   {
    #     relative_x: offset relative to parent. 0 is the middle.
    #     left_coutour: array of offsets relative to parent, all the way to the
    #                   bottom-most leaf, used for spreading subtrees apart.
    #     right_contour: ditto
    #   }
    _relative_position_info: () ->
      return @__relative_position_info if @__relative_position_info?

      # XXX should contours be linked lists instead of Arrays?

      @fraction()
      @_spacing()
      @_inner_width()
      @_inner_height()
      @walk_postorder (dn) ->
        children = dn.children()
        if !children?.length
          # Leaf node
          dn.__relative_position_info = {
            relative_x: undefined # parent will set it
            left_contour: [0]
            right_contour: [dn.__inner_width]
          }
        else
          # This node has children. Each child has __relative_position_info.
          left_child = children[0]
          right_child = children[children.length - 1]

          # Start with contours from the children. These are missing the root
          # node's contour, which we'll add later.
          left_contour = left_child.__relative_position_info.left_contour.slice(0)
          right_contour = left_child.__relative_position_info.right_contour.slice(0)

          # Pack child trees as close together as possible.
          # * set relative_x on all children (relative to the *left* of the
          #   leftmost child node -- this will change later!)
          # * adjust left_contour and right_contour to incorporate all children's
          #   contours and relative_x's
          left_child.__relative_position_info.relative_x = 0
          for i in [ 1 ... children.length ]
            fixed_node = children[i - 1]
            movable_node = children[i]

            pack_subtree(fixed_node, movable_node, right_contour) # TODO make this less confusing. It sets relative_x on movable_node.

            info = movable_node.__relative_position_info
            new_relative_x = info.relative_x
            extend_contour(left_contour, info.left_contour, new_relative_x, 'left')
            extend_contour(right_contour, info.right_contour, new_relative_x, 'right')

          # Width of direct descendents, used for repositioning
          children_width = right_child.__relative_position_info.relative_x + right_child.__inner_width

          # Center the packed child trees under this node
          center_shift = (dn.__inner_width - children_width) * 0.5
          move_contour(left_contour, center_shift)
          left_contour.unshift(0)
          move_contour(right_contour, center_shift)
          right_contour.unshift(dn.__inner_width)

          # Fix children's relative_x values. Now they'll point to the middle of
          # each node.
          for child in children
            child.__relative_position_info.relative_x += (child.__inner_width - children_width) * 0.5

          dn.__relative_position_info = {
            relative_x: undefined # parent will set it
            left_contour: left_contour
            right_contour: right_contour
          }

      # Finally, set the root's relative_x. Since all these numbers are
      # relative, we can set anything here.
      @__relative_position_info.relative_x = 0
      @__relative_position_info

    # Returns width that includes all children.
    _outer_width: () ->
      return @__outer_width if @__outer_width?

      @_relative_position_info()
      relative_leftmost = Math.min(@__relative_position_info.left_contour...)
      relative_rightmost = Math.max(@__relative_position_info.right_contour...)

      @__outer_width = relative_rightmost - relative_leftmost

    # Returns an object like this:
    #
    #   {
    #     left: Number
    #     width: Number
    #     top: Number
    #     height: Number
    #     hmid: Number (horizontal midpoint)
    #   }
    #
    # All numbers are positive. The left and top of the drawing are 0.
    absolute_position: () ->
      return @_absolute_position if @_absolute_position?

      # Precompute the entire tree
      @root()._inner_width()
      @root()._inner_height()
      @root()._relative_position_info()
      # Then walk the entire tree
      @root().walk_preorder (dn) ->
        hmid = if !dn.parent?
          # Root: use contour to decide where to start.
          leftmost = Math.min(dn.__relative_position_info.left_contour.slice(0)...) || 0
          root_left = dn.__relative_position_info.left_contour[0]
          -leftmost + root_left + dn.__inner_width * 0.5
        else
          dn.parent._absolute_position.hmid + dn.__relative_position_info.relative_x

        top = if !dn.parent?
          DEFAULT_OPTIONS.node_vpadding # root
        else
          parent_position = dn.parent._absolute_position
          parent_position.top +  parent_position.height + (DEFAULT_OPTIONS.node_vpadding * dn._fraction)

        dn._absolute_position = {
          hmid: hmid
          width: dn.__inner_width
          left: hmid - dn.__inner_width * 0.5
          top: top
          height: dn.__inner_height
        }

      @_absolute_position

    # Returns the height of the entire subtree, including padding.
    _outer_height: () ->
      return @__outer_height if @__outer_height?

      @fraction()
      @absolute_position()

      max_bottom = 0
      @walk (dn) ->
        absolute_position = dn._absolute_position
        bottom = absolute_position.top + absolute_position.height
        max_bottom = bottom if bottom > max_bottom

      @_outer_height = max_bottom - @_absolute_position.top + DEFAULT_OPTIONS.node_vpadding * 2

    # Width of the node, including all children and padding.
    #
    # Units: a width of 1 means, roughly, 1 document.
    outer_width: () ->
      @_outer_width()

    # Height of the node, including all children.
    #
    # Units: a height of 1 means, roughly, 1 node.
    outer_height: () ->
      @_outer_height()

    # Deletes @_px for this entire tree.
    clear_px: () ->
      @walk (dn) ->
        delete dn._px

    # Returns an object containing pixel coordinates:
    #
    #   {
    #     hmid: Number
    #     left: Number
    #     width: Number
    #     top: Number
    #     height: Number
    #   }
    #
    # Call this with a 2D transform, and the DrawableNode will cache the results
    # recursively. Use clear_px_coordinates() to recursively clear them.
    px: (px_per_hunit, px_per_vunit, left_hunits, top_vunits) ->
      return @_px if @_px?

      left_px = left_hunits * px_per_hunit
      top_px = top_vunits * px_per_vunit

      w = (abs_w) -> abs_w * px_per_hunit
      h = (abs_h) -> abs_h * px_per_vunit
      x = (abs_x) -> w(abs_x) - left_px
      y = (abs_y) -> h(abs_y) - top_px

      @root().absolute_position()
      @root().walk (dn) ->
        a = dn._absolute_position
        dn._px = {
          hmid: x(a.hmid)
          left: x(a.left)
          width: w(a.width)
          top: y(a.top)
          height: h(a.height)
        }

      @_px
