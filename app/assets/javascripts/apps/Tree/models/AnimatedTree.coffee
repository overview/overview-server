define [ 'underscore', './observable', './AnimatedNode' ], (_, observable, AnimatedNode) ->
  # A tree of animation nodes which tracks an OnDemandTree
  #
  # This class serves the following functions:
  #
  # * It maintains a top-down tree: nodes have `.children` and `.parent`, and
  #   the tree has a `.root`.
  # * It points to the OnDemandTree structures: nodes have `.json`.
  # * It adds a "loaded" concept: a node has `.loaded_fraction.current=1` if its
  #   `.children` is defined (even if empty).
  # * It tracks "selected": a node is `.selected_fraction.current=1` if it is in
  #   `state.selection.nodes`.
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
  # * setSizeInPixels(width, height): used to adjust transform
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
    constructor: (@on_demand_tree, @state, @animator, @layout, @width, @height) ->
      @id_tree = @on_demand_tree.id_tree
      @root = undefined
      @nodes = {} # id -> AnimatedNode
      @_needsUpdate = false
      @_lastSelection = undefined

      this._attachIdTree()
      this._attachState()

    setSizeInPixels: (width, height) ->
      return if width == @width && height == @height
      @_maybe_notifying_needs_update =>
        @width = width
        @height = height

    needsUpdate: ->
      @_needsUpdate || @root?.updatedAt? || @animator.needs_update()

    update: ->
      @animator.update() # update fractions
      @_tick() # use fractions to update node sizes/positions
      @_needsUpdate = false # needsUpdate will check @root.rootedTreeUpdatedAt
      undefined

    _getNode: (id) -> @on_demand_tree.getNode(id)
    getAnimatedNode: (id) -> @nodes[id]

    _attachIdTree: ->
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
      nodes = @nodes
      state = @state
      lastSelectedIdSet = {}
      animator = @animator

      @state.observe 'selection-changed', =>
        @_maybe_notifying_needs_update =>
          time = Date.now()

          selectedIdSet = {}
          (selectedIdSet[id] = null) for id in (state.selection?.nodes || [])

          time = Date.now()

          for id, __ of lastSelectedIdSet
            if id not of selectedIdSet
              nodes[id]?.setSelected(false, animator, time)

          for id, __ of selectedIdSet
            if id not of lastSelectedIdSet
              nodes[id]?.setSelected(true, animator, time)

          lastSelectedIdSet = selectedIdSet

          undefined
        undefined
      undefined

    _startAdd: (ids, ms=undefined) ->
      ms ?= Date.now()
      id_tree = @on_demand_tree.id_tree
      p = id_tree.parent
      c = id_tree.children
      selected = {}
      nodes = @on_demand_tree.nodes
      animatedNodes = @nodes
      (selected[id] = null) for id in @state.selection.nodes || []

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
          animatedNodes[id] = new AnimatedNode(nodes[id], parentNode, id of selected, ms)
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
      ms ?= Date.now()

      json = @_getNode(rootId)
      @root = new AnimatedNode(json, null, rootId in (@state.selection.nodes || []), ms)
      @nodes[rootId] = @root

      undefined

    _maybe_notifying_needs_update: (callback) ->
      old_needs_update = this.needsUpdate()
      callback.apply(this)
      @_needsUpdate = true
      this._notify('needs-update') if !old_needs_update && this.needsUpdate()

    _updateLayout: ->
      return if !@root?

      ms = Date.now()
      @layout.calculateSize2(this, ms)
      @layout.calculatePosition2(this, ms)
      @_tick()

    _tick: ->
      return if !@root?

      ms = Date.now()
      @layout.calculateSizeAndPosition(this, @animator, ms)
      @bounds = @layout.calculateBounds(this)

      undefined

    # Calculates the transform from relative coordinates to view coordinates.
    #
    # The result is a matrix described as [ a, b, c, d, e, f ] as in
    # http://www.whatwg.org/specs/web-apps/current-work/multipage/the-canvas-element.html#transformations:
    #
    #     a c e
    #     b d f
    #     0 0 1
    #
    # Here, b and c will be 0, but we'll return them anyway for API conformity.
    #
    # The Y transform is simple: multiply relative y by height / bounds.bottom.
    # The X transform is more complex: first convert X to a relative
    # [ -0.5 .. 0.5 ] scale, then apply pan and zoom, then scale to width.
    calculateTransform: (width, height, zoom, pan, tx, ty) ->
      # Y is easy enough:
      #
      # yDesired = yRelative scaled to [ 0 .. height ]
      #   = (yRelative - yTop) / hRelative * height
      # yTop = 0
      # d = height / hRelative
      # f = 0
      hRelative = (@bounds.bottom - @bounds.top) || 1

      # For X, let's do some math. Define:
      #
      # xNormalized = (xRelative scaled to [ -0.5 .. 0.5 ])
      # xDesired = ((xNormalized - pan) / zoom) scaled to [ 0 .. width ]
      #
      # Remember, the useful portion of (xNormalized + pan) / zoom
      # spans [ -0.5 .. 0.5 ].
      #
      # Now derive a and e:
      #
      # xMid = (xMin + xMax) * 0.5
      # wRelative = xMax - xMin
      # xNormalized = (x - xMid) / wRelative
      # xDesired = (((x - xMid) / wRelative - pan) / zoom + 0.5) * width
      # xDesired = ((x - xMid) / wRelative - pan) / zoom * width + 0.5 * width
      # xDesired = (x - xMid) / wRelative / zoom * width - pan / zoom * width + 0.5 * width
      # xDesired = x / wRelative / zoom * width - xMid / wRelative / zoom * width - pan / zoom * width + 0.5 * width
      # a = 1 / wRelative / zoom * width
      #   = width / wRelative / zoom
      # e = -xMid / wRelative / zoom * width - pan / zoom * width + 0.5 * width
      #   = width * (-xMid / wRelative / zoom - pan / zoom + 0.5)
      #   = width * (0.5 + (-xMid / wRelative - pan) / zoom)
      wRelative = (@bounds.right - @bounds.left)
      xMid = (@bounds.left + @bounds.right) * 0.5

      [
        width / wRelative / zoom
        0
        0
        height / hRelative
        width * (0.5 + (-xMid / wRelative - pan) / zoom) + tx
        ty
      ]

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
    # * width: width the rectangle being returned
    # * height: height of the rectangle being returned
    # * tx: left of the rectangle being returned
    # * ty: top of the rectangle being returned
    # * zoom: zoom, from 0.000001 to 1
    # * pan: pan, from -0.5 to 0.5
    calculatePixels: (width, height, zoom, pan, tx, ty) ->
      ret = []
      byId = {} # id -> pixels, for finding parents

      return ret if !@root

      transform = @calculateTransform(width, height, zoom, pan, tx, ty)

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
            left: hmid - w * 0.5
            width: w
            top: top
            height: h
            hmid: hmid
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
