define [ 'underscore' ], (_) ->
  DEFAULT_OPTIONS = {
    vpadding: 0.5 # fraction of a node's height
    hpadding: 0.25 # fraction of the deepest leaf node's median width
    duration: 1000 # ms
  }

  NullSize = { width: 0, height: 0, hpadding: 0, vpadding: 0 }
  NullPosition = { xMiddle: 0, top: 0 } # rarely used

  # Sets AnimatedNode sizes and positions
  #
  # Follows http://dirk.jivas.de/papers/buchheim02improving.pdf but gives each
  # node a size and margin.
  class TreeLayout
    constructor: (options) ->
      @options = _.extend({}, DEFAULT_OPTIONS, options)

    # Set each node's .size, and .position based on .size1, .position1, .size2,
    # .position2 and .updatedAt
    #
    # If .size1 or .size2 is null, this method will pretend it is 0. This helps
    # when the node is appearing for the first time or disappearing:
    # .calculateSize2() and .calculatePosition2() always calculate the _target_
    # measurements, and some nodes in the AnimatedTree aren't in our target
    # layout at all. (Those nodes are all missing .size2.)
    calculateSizeAndPosition: (tree, animator, ms) ->
      duration = @options.duration
      fraction = animator.interpolator.start_time_to_current_eased_fraction.bind(animator.interpolator)

      # Return the position where we start animating
      findPosition1 = (node) ->
        if node.position1
          return node.position1

        # We're animating from nothing. Position should be bottom of parent
        while (node = node.parent) isnt null
          if (pos = node.position ? node.position2 ? node.position1)
            ret = _.clone(pos)
            ret.top += 1
            return ret

        NullPosition

      # Return the position where we'll stop animating
      findPosition2 = (node) ->
        if node.position2
          return node.position2

        # We're animating into oblivion. Position should be bottom of parent
        while (node = node.parent) isnt null
          if node.position2
            ret = _.clone(node.position2)
            ret.top += 1
            return ret

        NullPosition

      tree.root.walk (v) ->
        t = if v.updatedAt? then fraction(v.updatedAt, ms) else null

        if !t? || t >= 1
          v.size = v.size2? && _.clone(v.size2) || null
          v.position = v.position2? && _.clone(v.position2) || null
          v.updatedAt = null
        else
          u = 1 - t

          size = (v.size ||= {})
          size1 = v.size1 ? NullSize
          size2 = v.size2 ? NullSize
          for k, v1 of size1
            v2 = size2[k]
            size[k] = u * v1 + t * v2

          position = (v.position ||= {})
          position1 = findPosition1(v)
          position2 = findPosition2(v)
          for k, v1 of position1
            v2 = position2[k]
            position[k] = u * v1 + t * v2

        undefined

    # Set each node's .size2
    calculateSize2: (tree, ms) ->
      vpadding = @options.vpadding

      # Find hpadding for all nodes: the padding we need to make the deepest
      # nodes separate
      leafNodes = [] # leaf or collapsed node
      (->
        populateLeafNodes = (node, level) ->
          if node.children? && node.children.length && node.children[0].fraction2?
            populateLeafNodes(child) for child in node.children
          else
            leafNodes.push(node)
        populateLeafNodes(tree.root)
      )()

      hpadding = if leafNodes.length
        leafNodes.sort((a, b) -> a.json.size - b.json.size)
        medianIndex = leafNodes.length >>> 1
        medianSize = if leafNodes.length & 1
          leafNodes[medianIndex].json.size
        else
          (leafNodes[medianIndex - 1].json.size + leafNodes[medianIndex].json.size) * 0.5
        @options.hpadding * medianSize
      else
        0

      tree.root.walk (v) ->
        # We use fraction2 to determine whether to set/unset size2
        if v.fraction2?
          v.size2 ||=
            width: v.json.size
            height: 1
            hpadding: hpadding
            vpadding: vpadding
        else
          if v.size2?
            v._setUpdatedAt(ms) # after adjusting spacing, lots of nodes must be animated
            v.size2 = null
            v.position2 = null

        if v.size2? && v.size2.hpadding != hpadding
          v._setUpdatedAt(ms) # after adjusting spacing, lots of nodes must be animated
          v.size2.hpadding = hpadding

        undefined

      undefined

    # Sets position2 on all nodes in tree that have size2.
    #
    # We call node._setUpdatedAt(ms) for all nodes whose position2 changes.
    #
    # If a node previously has children with size2/position2 and the node
    # disappears, its children's size2 and position2 will also disappear.
    # calculateSizeAndPosition() will animate those into oblivion.
    calculatePosition2: (tree, ms) ->
      vpadding = @options.vpadding

      # FirstWalk(v)
      firstWalk = (v, leftSibling) ->
        if !v.children? || !v.children.length || !v.children[0].size2?
          # It's a leaf, either because its children aren't loaded, it has no
          # children, or it's being unloaded so we don't want to position the
          # children
          #
          # This diverges from the paper ... a bug? The paper says prelim=0
          v.scratch.prelim = if leftSibling?
            leftSibling.scratch.prelim + distance(leftSibling, v)
          else
            0
        else
          children = v.children

          defaultAncestor = children[0]
          wPrev = null # left sibling of w
          for w in v.children
            firstWalk(w, wPrev)
            defaultAncestor = apportion(w, wPrev, defaultAncestor)
            wPrev = w
          executeShifts(v)
          firstChild = children[0]
          lastChild = children[children.length - 1]
          midpoint = 0.5 * (
            firstChild.scratch.prelim - (firstChild.size2.width + firstChild.size2.hpadding) * 0.5 +
            lastChild.scratch.prelim + (lastChild.size2.width + lastChild.size2.hpadding) * 0.5
          )
          if leftSibling?
            v.scratch.prelim = leftSibling.scratch.prelim + distance(leftSibling, v)
            v.scratch.mod = v.scratch.prelim - midpoint
          else
            v.scratch.prelim = midpoint

      distance = (w, v) ->
        # Calculate distance between midpoints -- every node has its own width
        leftSize = w.size2
        rightSize = v.size2
        ret = 0.5 * (leftSize.width + rightSize.width + leftSize.hpadding + rightSize.hpadding)
        ret

      # Apportion. w is the left sibling of w, if one exists.
      #
      # Returns a replacement defaultAncestor
      apportion = (v, w, defaultAncestor) ->
        if w?
          vⁱₚ = vᵒₚ = v
          vⁱₘ = w
          vᵒₘ = vⁱₚ.parent.children[0]
          sⁱₚ = vⁱₚ.scratch.mod
          sᵒₚ = vᵒₚ.scratch.mod
          sⁱₘ = vⁱₘ.scratch.mod
          sᵒₘ = vᵒₘ.scratch.mod
          while nextRight(vⁱₘ)? && nextLeft(vⁱₚ)?
            vⁱₘ = nextRight(vⁱₘ)
            vⁱₚ = nextLeft(vⁱₚ)
            vᵒₘ = nextLeft(vᵒₘ)
            vᵒₚ = nextRight(vᵒₚ)
            vᵒₚ.scratch.ancestor = v
            shift = (vⁱₘ.scratch.prelim + sⁱₘ) - (vⁱₚ.scratch.prelim + sⁱₚ) + distance(vⁱₘ, vⁱₚ)
            if shift > 0
              moveSubtree(ancestor(vⁱₘ, v, defaultAncestor), v, shift)
              sⁱₚ += shift
              sᵒₚ += shift
            sⁱₘ += vⁱₘ.scratch.mod
            sⁱₚ += vⁱₚ.scratch.mod
            sᵒₘ += vᵒₘ.scratch.mod
            sᵒₚ += vᵒₚ.scratch.mod
          if nextRight(vⁱₘ)? && !nextRight(vᵒₚ)?
            vᵒₚ.scratch.thread = nextRight(vⁱₘ)
            vᵒₚ.scratch.mod += sⁱₘ - sᵒₚ
          if nextLeft(vⁱₚ)? && !nextLeft(vᵒₘ)?
            vᵒₘ.scratch.thread = nextLeft(vⁱₚ)
            vᵒₘ.scratch.mod += sⁱₚ - sᵒₘ
            defaultAncestor = v

        defaultAncestor

      nextLeft = (v) ->
        if v.children? && v.children.length && v.children[0].size2?
          # v has a child
          v.children[0]
        else
          v.scratch.thread

      nextRight = (v) ->
        if v.children? && v.children.length && v.children[0].size2?
          # v has a child
          v.children[v.children.length - 1]
        else
          v.scratch.thread

      moveSubtree = (wₘ, wₚ, shift) ->
        wₘ = wₘ.scratch
        wₚ = wₚ.scratch
        subtrees = wₚ.number - wₘ.number
        change = shift / subtrees
        wₘ.change += change
        wₚ.change -= change
        wₚ.shift += shift
        wₚ.prelim += shift
        wₚ.mod += shift
        undefined

      executeShifts = (v) ->
        shift = 0
        change = 0
        i = v.children.length
        while (i -= 1) >= 0
          w = v.children[i].scratch
          w.prelim += shift
          w.mod += shift
          change += w.change
          shift += w.shift + change
        undefined

      # Returns left of the greatest uncommon ancestors between vⁱₘ and v
      ancestor = (vⁱₘ, v, defaultAncestor) ->
        if vⁱₘ.scratch.ancestor.parent is v.parent
          # vⁱₘ and v are siblings
          vⁱₘ.scratch.ancestor
        else
          defaultAncestor

      # Compute relative coordinates top and xMiddle
      secondWalk = (v, m, level) ->
        vp = (v.position2 ||= {})
        xMiddle = v.scratch.prelim + m
        top = (level - 1) * (1 + vpadding)

        if top != vp.top || xMiddle != vp.xMiddle
          vp.top = top
          vp.xMiddle = xMiddle
          v._setUpdatedAt(ms)

        if v.children? && v.children.length && v.children[0].size2?
          for w in v.children
            secondWalk(w, m + v.scratch.mod, level + 1)
        undefined

      initialize = (v) ->
        scratch = (v.scratch ||= {})
        scratch.ancestor = v
        scratch.change = 0
        scratch.mod = 0
        scratch.prelim = 0
        scratch.shift = 0
        scratch.thread = null

        if v.children?
          for w, i in v.children
            initialize(w)
            w.scratch.number = i

        undefined

      # Okay, helpers are ready. Let's run them.
      initialize(tree.root)
      firstWalk(tree.root)
      secondWalk(tree.root, -tree.root.scratch.prelim, 1)
      undefined

    _calculateBounds: (node, positionProperty, sizeProperty) ->
      minX = Infinity
      maxX = -Infinity
      minY = Infinity
      maxY = -Infinity

      node.walk (v) ->
        position = v[positionProperty]
        size = v[sizeProperty]
        if position && size
          w = size.width
          h = size.height
          x1 = position.xMiddle - w * 0.5
          y1 = position.top
          x2 = x1 + w
          y2 = y1 + h

          minX = x1 if x1 < minX
          maxX = x2 if x2 > maxX
          minY = y1 if y1 < minY
          maxY = y2 if y2 > maxY

      left: minX
      right: maxX
      top: minY
      bottom: maxY

    calculateBounds: (node) -> @_calculateBounds(node, 'position', 'size')
    calculateBounds2: (node) -> @_calculateBounds(node, 'position2', 'size2')
