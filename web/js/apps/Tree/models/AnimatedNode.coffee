define [ 'underscore' ], (_) ->
  # A node in an AnimatedTree.
  #
  # Each AnimatedNode has a `.json` property that corresponds to a node in an
  # OnDemandTree. But they aren't exactly the same: the AnimatedTree will add
  # and remove nodes here, not necessarily at the same times as they are added
  # and removed from the OnDemandTree. All we know for sure is that each
  # AnimatedNode has a valid `.json`. We don't care whether that node is still
  # in the OnDemandTree: children of unopened nodes will disappear soon enough.
  #
  # Here are some properties of the node that concern animations:
  #
  # * parent: AnimatedNode parent.
  # * children: Array of child AnimatedNode instances, or undefined.
  # * fraction1: size of the node, full-size being 1. Starts null, which means the
  #              node does not affect layout at all.
  # * fraction2: eventual size of the node, full-size being 1. May be null.
  # * updatedAt: ms at which size2 changed on this node or a descendent. null
  #              iff size ~= size2
  # * size: current { width, height, hMargin, vMargin } calculated by Layout
  #         each tick, using fraction.
  # * size2: eventual { width, height, hMargin, vMargin } calculated by Layout
  #          each change, using size2.
  # * position: current { xMiddle, top } calculated
  #             by Layout each tick, using size.
  # * position2: eventual { xMiddle, top } calculated
  #             by Layout each change, using size2.
  # * opened_fraction: Follows the Animator pattern. Iff
  #                    `.opened_fraction.current` is non-zero, `.children` is
  #                    defined. Use this for animation effects.
  # * selected_fraction: Follows the Animator pattern. Iff
  #                      `.selected_fraction.current` is 1, the node is
  #                      selected.
  #
  # Each time a change occurs, size2 and position2 will be adjusted. Ticks
  # will occur, updating size and position, until the root node's
  # descendentUpdatedAt is null.
  class AnimatedNode
    constructor: (@json, @parent, selected=false, updatedAt=undefined) ->
      @fraction1 = null
      @fraction2 = 1
      @children = undefined

      @_setUpdatedAt(updatedAt ? Date.now())
      @opened_fraction = { current: 0 }
      @selected_fraction = { current: selected && 1 || 0 }
      @_load_count = 0 # @children == undefined iff @_load_count == 0

    setSelected: (selected, animator, time=undefined) ->
      animator.animate_object_properties(this, { selected_fraction: selected && 1 || 0 }, undefined, time)

    # Sets the new Array of child nodes, or undefined.
    #
    # Parameters:
    # * children: Array of AnimatedNodes. Each must have children=undefined.
    # * animator: An Animator
    # * onNodeRemoved: callback to call for each ID of a child that is removed.
    #                  Callbacks cannot assume they will be called in order:
    #                  for instance, if a node's parents and grandparents are
    #                  both closed at the same time, there's no telling which
    #                  nodes onNodeRemoved will be called with first.
    setChildren: (children, animator, onNodeRemoved, time=undefined) ->
      return if children == @children

      time ?= Date.now()

      # Keep _load_count: if we delete children then add them again, the
      # onUnload should be a no-op
      @_load_count += (children && 1 || -1)

      if children?
        @children = children
        child._setFraction2(1, time) for child in children
        animator.animate_object_properties(this, { opened_fraction: 1 }, undefined, time)
      else
        child._setFraction2(null, time) for child in @children
        onUnload = =>
          if @_load_count == 0
            for child in @children
              onNodeRemoved(child.json.id)
            @children = undefined
        animator.animate_object_properties(this, { opened_fraction: 0 }, onUnload, time)

      undefined

    # Visit all nodes, in any order
    walk: (callback) -> @walkPreorder(callback)

    walkPreorder: (callback) ->
      stack = [ this ]

      while stack.length
        next = stack.pop()
        if next.children?
          stack.splice(stack.length, 0, next.children...)
        callback(next)

      undefined

    walkPostorder: (callback) ->
      if @children?
        @walkPostorder(child) for child in @children
      callback(this)
      undefined

    _setFraction2: (fraction2, ms) ->
      @fraction1 = @fraction
      @fraction2 = fraction2
      @_setUpdatedAt(ms)

    _setUpdatedAt: (ms) ->
      n = this
      # Walk up the tree setting updatedAt.
      # It's actually O(n) to call this on n nodes: we break out of the loop
      # when we visit a node we've already visited.
      while n isnt null
        break if n.updatedAt == ms
        n.size1 = n.size? && _.clone(n.size) || null
        n.position1 = n.position? && _.clone(n.position) || null
        n.updatedAt = ms
        n = n.parent
      undefined
