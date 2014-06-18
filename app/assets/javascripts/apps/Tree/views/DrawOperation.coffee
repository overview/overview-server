define [], ->
  # In given canvas 2d context, draws a rectangle with corner radius r
  drawRoundedRect = (ctx, x, y, w, h, r) ->
    r = Math.min(r, w * 0.5, h * 0.5)
    ctx.moveTo(x + r, y)
    ctx.lineTo(x + w - r, y)
    ctx.quadraticCurveTo(x + w, y, x + w, y + r)
    ctx.lineTo(x + w, y + h - r)
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
    ctx.lineTo(x + r, y + h)
    ctx.quadraticCurveTo(x, y + h, x, y + h - r)
    ctx.lineTo(x, y + r)
    ctx.quadraticCurveTo(x, y, x + r, y)
    ctx.closePath()

  # Draws a rectangle with corner radius r on the bottom two corners
  drawBottomRoundedRect = (ctx, x, y, w, h, r) ->
    r = Math.min(r, w * 0.5, h * 0.5)
    ctx.moveTo(x, y)
    ctx.lineTo(x + w, y)
    ctx.lineTo(x + w, y + h - r)
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
    ctx.lineTo(x + r, y + h)
    ctx.quadraticCurveTo(x, y + h, x, y + h - r)
    ctx.lineTo(x, y)
    ctx.closePath()

  class DrawOperation
    constructor: (@canvas, @tree, @colorLogic, @highlightedNodeIds, @hoverNodeId, @focus, @options) ->
      parent = @canvas.parentNode
      style = parent.ownerDocument.defaultView.getComputedStyle(parent, null)
      @canvas.width = @width = parseInt(style.width)
      @canvas.height = @height = parseInt(style.height)

      @ctx = @canvas.getContext('2d')

      @drawableNodes = [] # in case user hovers before tree is loaded

      # HDPI stuff: http://www.html5rocks.com/en/tutorials/canvas/hidpi/
      device_pixel_ratio = window.devicePixelRatio || 1
      backing_store_ratio = @ctx.webkitBackingStorePixelRatio ||
                            @ctx.mozBackingStorePixelRatio ||
                            @ctx.msBackingStorePixelRatio ||
                            @ctx.oBackingStorePixelRatio ||
                            @ctx.backingStorePixelRatio ||
                            1

      @device_to_backing_store_ratio = ratio = device_pixel_ratio / backing_store_ratio
      if ratio != 1
        old_width = @canvas.width
        old_height = @canvas.height

        @canvas.width = Math.floor(old_width * ratio)
        @canvas.height = Math.floor(old_height * ratio)

        @canvas.style.width = "#{old_width}px"
        @canvas.style.height = "#{old_height}px"

        @ctx.scale(ratio, ratio)

      @ctx.font = '12px "Open Sans", Helvetica, Arial, sans-serif'
      @ctx.textBaseline = 'top'

    clear: () ->
      @ctx.clearRect(0, 0, @width, @height)
      @root = undefined # will be overwritten if the tree isn't empty

    draw: () ->
      this.clear()
      return if !@tree.root?

      # Give AnimatedTree a thinner width/height than the canvas has. When we
      # stroke that's _centered_ on the pixels we give: if we stroke from 0,0
      # to 0,width with a stroke width of 2, then half of that stroke will
      # appear above y=0. We need that top half (and left, and right, and
      # bottom) to show up.

      margin =
        left: @options.maxNodeBorderWidth * 0.5
        right: @options.maxNodeBorderWidth * 0.5
        top: @options.maxNodeBorderWidth * 0.5
        bottom: @options.node_expand_radius + @options.node_expand_width * 0.5

      drawableNodes = @drawableNodes = @tree.calculatePixels(
        @focus,
        @width - margin.left - margin.right,
        @height - margin.top - margin.bottom,
        margin.left,
        margin.top
      )

      #@_auto_fit_pan() # TODO make this use @tree somehow?
      classifiedDrawableNodes = @_classifyDrawableNodes(drawableNodes)

      this._drawNodeFills(classifiedDrawableNodes)
      this._draw_colors(drawableNodes)
      this._drawNodeBorders(classifiedDrawableNodes)
      this._draw_labels(drawableNodes)
      this._draw_lines_from_parents_to_children(drawableNodes)
      this._draw_expand_and_collapse_for_tree(drawableNodes)

    pixel_to_node: (x, y) ->
      for px in @drawableNodes
        if x >= px.left && x <= px.left + px.width && y >= px.top && y <= px.top + px.height
          return px
      undefined

    _pixel_to_expand_or_collapse_node: (x, y, list_of_circles) ->
      maxR2 = @options.node_expand_click_radius * @options.node_expand_click_radius
      for entry in (list_of_circles || []) # [ xy, px ] pairs
        xy = entry[0]
        dx = xy.x - x
        dy = xy.y - y
        r2 = dx * dx + dy * dy
        return entry[1] if r2 < maxR2
      return undefined

    pixel_to_expand_node: (x, y) ->
      @_pixel_to_expand_or_collapse_node(x, y, @expand_circles || [])

    pixel_to_collapse_node: (x, y) ->
      @_pixel_to_expand_or_collapse_node(x, y, @collapse_circles || [])

    pixel_to_action: (x, y) ->
      event = (key, node) ->
        { event: key, node: node.json }

      if node = @pixel_to_expand_node(x, y)
        event('expand', node)
      else if node = @pixel_to_collapse_node(x, y)
        event('collapse', node)
      else if node = @pixel_to_node(x, y)
        event('click', node)
      else
        undefined

    _draw_tagcount: (left, top, width, height, color, fraction) ->
      return if fraction == 0

      slant_offset = 0 # disable slant drawing, for greater legibility of fraction of folder tagged
      tagwidth = 1.0 * (width + slant_offset) * fraction
      if (width != 0)
        if (tagwidth < 5)
          tagwidth = 5 # always make the non-empty tag at least this wide on display, so visible

      ctx = @ctx

      ctx.fillStyle = color
      ctx.beginPath()
      drawBottomRoundedRect(ctx, left, top + height * 0.75, tagwidth, height * 0.25, @options.nodeCornerRadius)
      ctx.fill()

    _draw_colors: (drawableNodes) ->
      ctx = @ctx

      ctx.save()

      for px in drawableNodes
        json = px.json

        # Use the first tagid for which there's a count
        count = 0
        color = null

        if @colorLogic?
          if @colorLogic.searchResultIds?
            for id in @colorLogic.searchResultIds
              count = json.searchResultCounts?[id]
              if count
                color = @colorLogic.color
                break
          else if @colorLogic.tagIds?
            for id in @colorLogic.tagIds
              count = json.tagCounts?[id]
              if count
                color = @colorLogic.color
                break

        if color
          @_draw_tagcount(px.left, px.top, px.width, px.height, color, count / json.size)

      ctx.restore()
      undefined

    # Returns { 'highlighted': [ px1, px2, ... ], 'highlighted.hover': [ px3 ], ... }
    #
    # px1, px2, px3 etc. are objects in the passed `drawableNodes` array.
    #
    # Out-of-bounds nodes are filtered out of the return value.
    #
    # Each node can have one or several of the following classes:
    # * selected: a selected node
    # * highlighted: (when viewing a document) a node containing the document
    # * hover: a node the user is hovering over with a pointing device
    # * leaf: a leaf node
    #
    # If a node has no classes, it will be keyed by the empty string.
    #
    # Why classify like this? Because then we can set the canvas style once per
    # list instead of once per node. That makes fewer draw operations.
    _classifyDrawableNodes: (drawableNodes) ->
      ret = {}

      buffer = Math.max(@options.node_line_width, @options.node_line_width_selected, @options.node_line_width_leaf) * 0.5
      minX = 0 - buffer
      maxX = @width + buffer

      nodeTypes = [] # avoid extra object collection by reusing the same array
      for px in drawableNodes
        continue if px.left + px.width < minX || px.left > maxX

        node = px.node
        nodeId = node.json.id

        if (node.selected_fraction.v2? && node.selected_fraction.v2 == 1) || node.selected_fraction.current == 1
          nodeTypes.push('selected')

        if px.isLeaf
          nodeTypes.push('leaf')

        if nodeId == @hoverNodeId
          nodeTypes.push('hover')

        if nodeId of @highlightedNodeIds
          nodeTypes.push('highlighted')

        nodeType = nodeTypes.join('.')
        (ret[nodeType] ||= []).push(px)
        nodeTypes.splice(0, 10) # empty the entire array

      ret

    _getStyle: (type) ->
      types = {}
      (types[x] = null) for x in type.split('.')

      ret = {}

      for rule in @options.nodeStyleRules
        if rule.type == '' || rule.type of types
          for k, v of rule when k != 'type'
            ret[k] = v

      ret

    _drawNodeFills: (classifiedDrawableNodes) ->
      ctx = @ctx

      ctx.save()

      for type, nodes of classifiedDrawableNodes
        style = @_getStyle(type)
        ctx.fillStyle = style.fillStyle

        ctx.beginPath()
        for px in nodes
          drawRoundedRect(ctx, px.left, px.top, px.width, px.height, @options.nodeCornerRadius)
        ctx.fill()

      ctx.restore()

    _drawNodeBorders: (classifiedDrawableNodes) ->
      ctx = @ctx

      ctx.save()

      for type, nodes of classifiedDrawableNodes
        style = @_getStyle(type)
        ctx.strokeStyle = style.strokeStyle
        ctx.lineWidth = style.lineWidth

        ctx.beginPath()
        for px in nodes
          drawRoundedRect(ctx, px.left, px.top, px.width, px.height, @options.nodeCornerRadius)
        ctx.stroke()

      ctx.restore()

      undefined

    _draw_labels: (drawableNodes) ->
      ctx = @ctx

      ctx.save()

      ctx.fillStyle = '#333333'

      maxX = @width

      for px in drawableNodes
        width = px.width - 12 # border + padding

        continue if width < 15
        continue if px.left + px.width < 0
        continue if px.left > maxX

        description = px.json.description
        continue if !description

        top = px.top + 3
        left = px.left + 6

        ctx.save()
        ctx.beginPath()
        ctx.rect(left, top, width, 20)
        ctx.clip()
        ctx.fillText(description, left, top)
        ctx.restore()

      ctx.restore()

    _draw_lines_from_parents_to_children: (drawableNodes) ->
      ctx = @ctx

      minX = -@options.connector_line_width
      maxX = @width + @options.connector_line_width

      drawLineToParent = (px) ->
        parentPx = px.parent

        x1 = parentPx.hmid
        x2 = px.hmid

        return if x1 < minX && x2 < minX
        return if x1 > maxX && x2 > maxX

        y1 = parentPx.top + parentPx.height
        y2 = px.top
        mid_y = 0.5 * (y1 + y2)

        ctx.moveTo(x1, y1)
        ctx.bezierCurveTo(x1, mid_y + (0.1 * px.height), x2, mid_y - (0.1 * px.height), x2, y2)

      ctx.save()

      lineWidth = ctx.lineWidth = @options.connector_line_width
      if @focus.get('zoom') > 0.05
        # setLineDash() is pretty, but at high zoom levels these lines are
        # extremely long and so it costs lots and lots of CPU. Only enable it
        # when zoomed further out.
        ctx.setLineDash?([ Math.ceil(@options.connector_line_width), Math.ceil(@options.connector_line_width) ])

      ctx.strokeStyle = @options.lineStyles.normal
      ctx.beginPath()
      for px in drawableNodes when px.parent? && (px.json.id not of @highlightedNodeIds || px.parent.json.id not of @highlightedNodeIds)
        drawLineToParent(px)
      ctx.stroke()

      ctx.strokeStyle = @options.lineStyles.highlighted
      ctx.beginPath()
      for px in drawableNodes when px.parent? && (px.json.id of @highlightedNodeIds && px.parent.json.id of @highlightedNodeIds)
        drawLineToParent(px)
      ctx.stroke()

      ctx.restore()

      undefined

    _draw_expand_and_collapse_for_tree: (drawableNodes) ->
      ctx = @ctx
      ctx.save()

      lineWidth = ctx.lineWidth = @options.node_expand_width
      ctx.strokeStyle = '#666666'
      ctx.fillStyle = '#ffffff'
      halfLineWidth = lineWidth * 0.5
      radius = @options.node_expand_radius
      glyphRadius = @options.node_expand_glyph_radius
      outerRadius = radius + halfLineWidth
      minX = 0
      maxX = @width

      px_to_useful_xy = (px) ->
        if px.width > 20 && px.hmid + outerRadius >= minX && px.hmid - outerRadius <= maxX
          x: px.hmid
          y: px.top + px.height - halfLineWidth
        else
          undefined

      expandCircles = @expand_circles = []
      collapseCircles = @collapse_circles = []

      for px in drawableNodes when !px.isLeaf
        xy = px_to_useful_xy(px)
        if xy?
          if px.node.opened_fraction.current == 1
            collapseCircles.push([ xy, px ])
          else if px.node.opened_fraction.current == 0
            expandCircles.push([ xy, px ])

      # Draw circle fills
      ctx.beginPath()
      for circle in collapseCircles.concat(expandCircles)
        xy = circle[0]
        ctx.arc(xy.x, xy.y, radius, 0, Math.PI * 2, true)
        ctx.closePath()
      ctx.fill()

      ctx.beginPath()
      ctx.fillStyle = '#f4f4f4' # hover style
      hasHoverStyle = (px) =>
        node = px.node
        px.json.id == @hoverNodeId ||
          node.selected_fraction.current == 1 ||
          (node.selected_fraction.v2? && node.selected_fraction.v2 == 1)
      for circle in expandCircles when hasHoverStyle(circle[1])
        xy = circle[0]
        ctx.arc(xy.x, xy.y, radius, 0, Math.PI * 2, true)
        ctx.closePath()
      ctx.fill()

      # Draw circle borders -- semicircles for expand, because users can click
      # anywhere on the node to expand.
      ctx.beginPath()
      for circle in collapseCircles
        xy = circle[0]
        ctx.moveTo(xy.x + radius, xy.y)
        ctx.arc(xy.x, xy.y, radius, 0, Math.PI * 2, true)
      ctx.stroke()

      ctx.beginPath()
      for circle in expandCircles
        xy = circle[0]
        ctx.moveTo(xy.x - radius, xy.y)
        ctx.arc(xy.x, xy.y, radius, Math.PI, Math.PI * 2, true)
      ctx.stroke()

      # Draw + and -'s
      ctx.beginPath()
      for circle in collapseCircles
        xy = circle[0]
        ctx.moveTo(xy.x - glyphRadius, xy.y)
        ctx.lineTo(xy.x + glyphRadius, xy.y)
      for circle in expandCircles
        xy = circle[0]
        ctx.moveTo(xy.x - glyphRadius, xy.y)
        ctx.lineTo(xy.x + glyphRadius, xy.y)
        ctx.moveTo(xy.x, xy.y + glyphRadius)
        ctx.lineTo(xy.x, xy.y - glyphRadius)
      ctx.stroke()

      ctx.restore()
