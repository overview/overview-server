define [ 'underscore', './observable', './AnimatedNode' ], (_, observable, AnimatedNode) ->
  # Given 3x3 matrices a and b, returns ab.
  #
  # Each matrix is assumed to look like this:
  #
  #   [ a1 c1 e1 ] [ a2 c2 e2 ]
  #   [ b1 d1 f1 ] [ b2 d2 f2 ]
  #   [  0  0  1 ] [  0  0  1 ]
  #
  # So their product is:
  #
  #   [ a1*a2+c1*b2   a1*c2+c1*d2   a1*e2+c1*f2+e1 ]
  #   [ b1*a2+d1*b2   b1*c2+d1*d2   b1*e2+d1*f2+f1 ]
  #   [           0             0                1 ]
  #
  # The parameters and return value are of the form [ a, b, c, d, e, f ].
  transformMatrixMultiply = (a, b) ->
    [
      a[0] * b[0] + a[2] * b[1]
      a[1] * b[0] + a[3] * b[1]
      a[0] * b[2] + a[2] * b[3]
      a[1] * b[2] + a[3] * b[3]
      a[0] * b[4] + a[2] * b[5] + a[4]
      a[1] * b[4] + a[3] * b[5] + a[5]
    ]

  # A tree of animation nodes which tracks an OnDemandTree
  #
  # This class serves the following functions:
  #
  # * It maintains a top-down tree: nodes have `.children` and `.parent`, and
  #   the tree has a `.root`.
  # * It points to the OnDemandTree structures: nodes have `.json`.
  # * It adds a "loaded" concept: a node has `.loaded_fraction.current=1` if its
  #   `.children` is defined (even if empty).
  # * It tracks "selected": a node is `.selected_fraction.current=1` if it is
  #   selected in the state.
  #
  # Callers need only create the AnimatedTree, pointed at an OnDemandTree,
  # and they'll get the following:
  #
  # * update(): runs an animation step.
  # * needsUpdate(): says whether update() will do something.
  # * calculatePixels(): returns an Array of [{ isLeaf, left, width, top,
  #                         height, hmid, node, json, parent }]
  #                         objects. (node tracks whether the node is
  #                         selected; json is the OnDemandTree node; parent is
  #                         another element of the Array, or null.)
  # * A :needs-update signal, fired when needsUpdate() goes from false to true
  # * getAnimatedNode(node): returns an AnimatedNode for a node. Callers may be
  #                          interested in size, position, size2 and position2.
  # * bounds: { left, right, top, bottom } in arbitrary units. bottom > top.
  # * transform: { translateX, translateY, scaleX, scaleY } to get to pixels
  class AnimatedTree
    observable(this)

    # Create an AnimatedTree.
    #
    # * on_demand_tree: an OnDemandTree (for the nodes, and to track when they
    #                   added/removed).
    # * state: a State (for the selection)
    # * animator: an Animator (to animate the selection)
    # * layout: a Layout (to set sizes and positions of nodes)
    # * width, height: pixel width and height
    constructor: (@on_demand_tree, @state, @animator, @layout) ->
      @id_tree = @on_demand_tree.id_tree
      @root = undefined
      @nodes = {} # id -> AnimatedNode
      @_needsUpdate = false
      @_lastSelection = undefined

      this._attachIdTree()
      this._attachState()

    needsUpdate: ->
      @_needsUpdate || @root?.updatedAt? || @animator.needs_update()

    update: ->
      @animator.update() # update fractions
      @_tick() # use fractions to update node sizes/positions
      @_needsUpdate = false # needsUpdate will check @root.updatedAt
      undefined

    _getNode: (id) -> @on_demand_tree.getNode(id)
    getAnimatedNode: (id) -> @nodes[id]

    _attachIdTree: ->
      if @id_tree.root
        ids = [] # ordered list of nodes
        @id_tree.walkPreorder((id) -> ids.push(id))
        @_startSetRoot(ids[0], 0)
        @_startAdd(ids.slice(1), 0)

        @_updateLayout()

      @id_tree.observe 'reset', =>
        @_maybe_notifying_needs_update =>
          @_startSetRoot(null)

      @id_tree.observe 'change', (changes) =>
        @_maybe_notifying_needs_update =>
          if 'root' of changes
            @_startSetRoot(changes.root)
            @_startAdd(changes.added.slice(1)) # assume root is first element
          else if 'added' of changes
            @_startAdd(changes.added)
          else if 'removed' of changes
            @_startRemove(changes.removed)

          @_updateLayout()

      undefined

    _attachState: ->
      selectedNodeIds = []

      @state.on 'change:documentList', (__, documentList) =>
        @_maybe_notifying_needs_update =>
          time = Date.now()

          for nodeId in selectedNodeIds
            @nodes[nodeId]?.setSelected(false, @animator, time)

          selectedNodeIds = documentList?.params?.params?.nodes || []

          for nodeId in selectedNodeIds
            @nodes[nodeId]?.setSelected(true, @animator, time)

          undefined
        undefined
      undefined

    _startAdd: (ids, ms=undefined) ->
      ms ?= Date.now()
      id_tree = @on_demand_tree.id_tree
      p = id_tree.parent
      c = id_tree.children
      nodes = @on_demand_tree.nodes
      animatedNodes = @nodes
      selectedNodeId = null
      if (params = @state.get('documentList')?.params)? && params.type == 'node'
        selectedNodeId = params.node.id

      # When ids are added to the tree, their parents become open. Assume their
      # parents were unopened before (because siblings are always added all at
      # once in a single change).
      #
      # Also, parents appear in order. So if we iterate in order, assume the
      # parent (of a non-root node) exists and is unopened before this method
      # is called.
      parentIds = _.uniq(p[id] for id in ids)

      for parentId in parentIds when parentId isnt null
        parentNode = animatedNodes[parentId]
        siblingNodes = for id in c[parentId]
          animatedNodes[id] = new AnimatedNode(nodes[id], parentNode, id == selectedNodeId, ms)
        parentNode.setChildren(siblingNodes, @animator, undefined, ms)

      undefined

    _startRemove: (ids, ms=undefined) ->
      ms ?= Date.now()
      nodes = @nodes

      # When ids are removed from the tree, their parents become closed.
      # Assume their parents were opened before (because siblings are always
      # removed all at once in a single change).
      parentNodes = _.uniq(nodes[id].parent for id in ids)

      onNodeRemoved = (id) => delete @nodes[id]

      for parentNode in parentNodes
        if parentNode is null
          @root = undefined
          @nodes = {}
          break
        else
          parentNode.setChildren(undefined, @animator, onNodeRemoved, ms)

      undefined

    _startSetRoot: (rootId, ms=undefined) ->
      if rootId?
        ms ?= Date.now()

        if (params = @state.get('documentList')?.params)? && params.type == 'node'
          selectedNodeId = params.node.id

        json = @_getNode(rootId)
        @root = new AnimatedNode(json, null, rootId == selectedNodeId, ms)
        @nodes[rootId] = @root
      else
        @root = null
        @nodes = {}
        @_lastSelection = undefined

      undefined

    _maybe_notifying_needs_update: (callback) ->
      old_needs_update = this.needsUpdate()
      callback.apply(this)
      @_needsUpdate = true
      this._notify('needs-update') if !old_needs_update && this.needsUpdate()

    # Returns the { top, left, right, bottom } in relative units of the given
    # AnimatedNode and its children, as calculated at the previous tick.
    calculateBounds: (node) ->
      @layout.calculateBounds(node)

    # Returns the { top, left, right, bottom } in relative units of the given
    # AnimatedNode and its children, as it will be in the eventual layout.
    calculateBounds2: (node) ->
      @layout.calculateBounds2(node)

    _updateLayout: ->
      return if !@root?

      ms = Date.now()
      @layout.calculateSize2(this, ms)
      @layout.calculatePosition2(this, ms)
      @bounds2 = @layout.calculateBounds2(@root)
      @_tick()

    _tick: ->
      return if !@root?

      ms = Date.now()
      @layout.calculateSizeAndPosition(this, @animator, ms)
      @bounds = @layout.calculateBounds(@root)

      undefined

    # Calculates the transform from relative coordinates to [ 0 .. 1 ]
    getNormalizeTransform: ->
      # y = (yIn - yTop) / h
      #   = yIn / h - yTop / h
      #
      # x = (xIn - xLeft) / w
      #   = xIn / w - xLeft / w
      w = @bounds.right - @bounds.left
      x0 = @bounds.left
      h = @bounds.bottom - @bounds.top
      y0 = @bounds.top

      [
        1 / w
        0
        0
        1 / h
        - x0 / w
        - y0 / h
      ]

    # Calculates the transform from [ 0 .. 1 ] to [ tx .. tx + width ] (same with y)
    getScaleTransform: (w, h, x0, y0) ->
      # y = yIn * h + y0
      # x = xIn * w + x0
      [
        w
        0
        0
        h
        x0
        y0
      ]


    # Calculates the transform from relative coordinates to
    # x ∈ [ tx .. tx + width ], y ∈ [ ty .. ty + height ]
    #
    # The result is a matrix described as [ a, b, c, d, e, f ] as in
    # http://www.whatwg.org/specs/web-apps/current-work/multipage/the-canvas-element.html#transformations:
    #
    #     a c e
    #     b d f
    #     0 0 1
    #
    # Here, b and c will be 0, but we'll return them anyway for API conformity.
    getTransform: (animatedFocus, width, height, tx, ty, ms=undefined) ->
      ms ?= Date.now()

      normalizeTransform = @getNormalizeTransform()
      zoomPanTransform = animatedFocus.getTransform(this, ms)
      scaleTransform = @getScaleTransform(width, height, tx, ty)

      # We want to do this:
      #
      # scaleTransform(zoomPanTransform(normalizeTransform(coord)))
      #
      # By the associative property, that is:
      #
      # (scaleTransform * zoomPanTransform * normalizeTransform)(coord)
      transformMatrixMultiply(
        transformMatrixMultiply(scaleTransform, zoomPanTransform),
        normalizeTransform
      )

    # Returns an Array of drawable objects.
    #
    # The array elements have these properties:
    #
    #     isLeaf: Boolean
    #     left: left pixel of the node (say, a rectangle left)
    #     width: width in pixels of the node (say, a rectangle width)
    #     top: top pixel of the node (say, a rectangle top)
    #     height: height in pixels of the node (say, a rectangle height)
    #     hmid: middle X pixel of the node (important to layout)
    #     node: the AnimatedNode
    #     json: the OnDemandTree's JSON object for the node
    #     parent: the array element corresponding to this AnimatedNode's parent
    #
    # These properties should be all that is needed to draw the tree. Rendering
    # consists of iterating over this Array, rather than walking a tree.
    #
    # The parameters are:
    #
    # * animatedFocus: AnimatedFocus, used to find zoom/pan transform
    # * width: width the rectangle being returned
    # * height: height of the rectangle being returned
    # * tx: left of the rectangle being returned
    # * ty: top of the rectangle being returned
    # * zoom: zoom, from 0.000001 to 1
    # * pan: pan, from -0.5 to 0.5
    calculatePixels: (animatedFocus, width, height, tx, ty) ->
      ret = []
      byId = {} # id -> pixels, for finding parents

      return ret if !@root

      ms = Date.now()

      transform = @getTransform(animatedFocus, width, height, tx, ty)
      round = if @root.updatedAt?
        # we're animating. Use fractions for smooth animations
        (x) -> x
      else
        # we're done animating. Snap to pixels for crispness
        Math.round

      # assume b and c are 0
      sx = transform[0]
      sy = transform[3]
      tx = transform[4]
      ty = transform[5]

      # Calculate pixels
      @root.walk (node) ->
        if node.size? && node.position?
          w = node.size.width * sx
          h = node.size.height * sy
          hmid = node.position.xMiddle * sx + tx
          top = node.position.top * sy + ty

          o =
            isLeaf: node.json.isLeaf
            left: round(hmid - w * 0.5)
            width: round(w)
            top: round(top)
            height: round(h)
            hmid: round(hmid) # it's okay if rounding means hmid != left+width/2
            node: node
            json: node.json
            parent: null

          byId[o.json.id] = o
          ret.push(o)
        undefined

      # point to parents
      ret.forEach (o) ->
        parentId = o.json.parentId
        o.parent = byId[parentId] if parentId
        undefined

      ret
