define [ 'backbone' ], (Backbone) ->
  MIN_ZOOM = 0.00001 # above 0

  # Transforms coordinates for viewing.
  #
  # The AnimatedFocus focuses on either an AnimatedNode or on a pan/zoom pair.
  #
  # Pan and zoom are specified on a scale of [ -0.5 .. 0.5 ]. Imagine -0.5 is
  # the leftmost point in the AnimatedTree and 0.5 is the rightmost point. If
  # pan is 0.25 and zoom is 0.25, then the center of the zoomed portion will
  # be 0.25 and only 0.25 of the tree will be visible: the portion at
  # [ 0.125 .. 0.375 ] will zoom to fill the entire draw area.
  #
  # A pan of 0.5 is not a sane value: it implies that we will see (zoom/2) to the
  # right of 0.5, which is not okay. Sane zoom values are in ( 0 .. 1 ]. Sane pan
  # values are in [ -0.5+zoom/2 .. 0.5-zoom/2 ].
  #
  # Usage:
  #
  #   focus = new AnimatedFocus(animator)
  #   ms = Date.now()
  #   focus.setPanAndZoom(0.25, 0.375) # pan and zoom immediately
  #   focus.setNode(node) # pan/zoom to the given node immediately
  #   focus.animatePanAndZoom(0.25, -0.375, ms) # pan to 0.25, zoom to -0.375
  #   focus.animatePan(0.25, ms) # helper
  #   focus.animateZoom(-0.375, ms) # helper
  #   focus.animateNode(node) # pan/zoom to the given node
  #   focus.update(animatedTree, ms) # updates pan/zoom properties
  #   focus.getTransform(animatedTree, ms)
  #       # returns a transform mapping [ 0 .. 1 ] to [ 0 .. 1 ].
  #       # The transform is [ a, b, c, d, e, f ] like an SVG transform.
  #       # This method calls update() first
  #   focus.needsUpdate() # returns true if animation is not complete
  #
  # Callers cannot find out where the focus is _going_ to end up: they can only
  # see where it is currently. The pan and zoom may change if we're focusing on
  # an AnimatedNode and any node expands or collapses; but in that case (and any
  # other edge case) getTransform() will be called and so callers can listen for
  # changes to the 'pan' and 'zoom' properties.
  Backbone.Model.extend
    defaults:
      # pan and zoom used in rendering, set during update()
      pan: 0
      zoom: 1

    initialize: (attributes, options) ->
      throw 'must pass options.animator, an Animator' if !options.animator
      @animator = options.animator

      # How far between @prevPanZoom and @nextObject
      @fraction = { current: 1 }

      # focus when fraction=0 (always pan/zoom)
      @prevPanZoom = @_getPanZoom()
      # focus when fraction=1 (pan/zoom or node)
      @nextObject = @_getPanZoom()

    # Returns a new { pan: pan, zoom: zoom } Object.
    _getPanZoom: ->
      pan: @get('pan')
      zoom: @get('zoom')

    _setNextObject: (nextObject, setOrAnimate, ms) ->
      @prevPanZoom.pan = @get('pan')
      @prevPanZoom.zoom = @get('zoom')
      @nextObject = nextObject

      # make the animator aware of the change
      if setOrAnimate == 'set'
        @animator.set_object_properties(this, { fraction: 1 })
      else
        ms ?= Date.now()
        @animator.set_object_properties(this, { fraction: 0 })
        @animator.animate_object_properties(this, { fraction: 1 }, undefined, ms)

      # trigger 'change' event
      @set('nextObject', nextObject)

    _sanifyPanAndZoom: (pan, zoom) ->
      zoom: @_sanifyZoom(zoom)
      pan: @_sanifyPanAtZoom(pan, zoom)

    _sanifyZoom: (zoom) ->
      if zoom > 1
        1
      else if zoom < MIN_ZOOM
        MIN_ZOOM
      else
        zoom

    _sanifyPanAtZoom: (pan, zoom) ->
      if 2 * pan < zoom - 1
        pan = (zoom - 1) * 0.5
      else if 2 * pan > 1 - zoom
        pan = (1 - zoom) * 0.5
      else
        pan

    setPanAndZoom: (pan, zoom) ->
      panZoom = @_sanifyPanAndZoom(pan, zoom)
      @_setNextObject(panZoom, 'set')

    animatePanAndZoom: (pan, zoom, ms) ->
      panZoom = @_sanifyPanAndZoom(pan, zoom)
      @_setNextObject(panZoom, 'animate', ms)

    setPan: (pan) -> @setPanAndZoom(pan, @get('zoom'))
    setZoom: (zoom) -> @setPanAndZoom(@get('pan'), zoom)
    animatePan: (pan) -> @animatePanAndZoom(pan, @get('zoom'))
    animateZoom: (zoom) -> @animatePanAndZoom(@get('pan'), zoom)
    setNode: (node) -> @_setNextObject({ node: node }, 'set')
    animateNode: (node) -> @_setNextObject({ node: node }, 'animate')

    isZoomedInFully: -> @get('zoom') <= MIN_ZOOM
    isZoomedOutFully: -> @get('zoom') >= 1

    # Updates 'pan' and 'zoom' properties. Call this during render.
    update: (animatedTree, ms=undefined) ->
      ms ?= Date.now()
      @animator.update(ms)

      t = @fraction.current
      u = 1 - t
      nextPanAndZoom = if (node = @nextObject.node)?
        # We animate to bounds, not bounds2. This way, the zoom stays locked
        # where it belongs when the node moves after zooming is complete.
        bounds = animatedTree.bounds
        fullWidth = bounds.right - bounds.left
        fullLeft = bounds.left
        nodeBounds = animatedTree.calculateBounds(node)
        nodeWidth = nodeBounds.right - nodeBounds.left
        nodeMiddle = (nodeBounds.left + nodeBounds.right) * 0.5

        pan = (nodeMiddle - fullLeft) / fullWidth - 0.5

        if node.narrow?
          zoom = nodeWidth * 4 / fullWidth

          # if the node is near the right edge, slide the tree to the left to avoid wasting space
          if node.position.xMiddle + (nodeWidth / 2) > bounds.right * .95
            pan = (nodeMiddle - nodeWidth * 1.5 - fullLeft) / fullWidth - 0.5

          # same goes for the left
          if node.position.xMiddle - (nodeWidth / 2) < bounds.left * 1.05
            pan = (nodeMiddle + nodeWidth * 1.5 - fullLeft) / fullWidth - 0.5

        else
          zoom = nodeWidth / fullWidth

        pan: pan
        zoom: zoom
      else
        @nextObject

      pan = u * @prevPanZoom.pan + t * nextPanAndZoom.pan
      zoom = u * @prevPanZoom.zoom + t * nextPanAndZoom.zoom

      @set(
        pan: pan
        zoom: zoom
      )

    getTransform: (animatedTree, ms=undefined) ->
      @update(animatedTree, ms)

      zoom = @get('zoom')
      pan = @get('pan')

      # The return matrix is:
      #
      # [ a c e ] [x]
      # [ b d f ] [y]
      # [ 0 0 1 ] [1]
      #
      # xOut - 0.5 = (xIn - 0.5) / zoom - pan / zoom
      # xOut = xIn / zoom - (pan + 0.5) / zoom + 0.5
      #      = xIn / zoom + (0.5 * zoom - pan - 0.5) / zoom

      [
        1 / zoom
        0
        0
        1
        (0.5 * zoom - pan - 0.5) / zoom
        0
      ]

    needsUpdate: -> @animator.needs_update()
