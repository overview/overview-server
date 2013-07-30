define [
  'jquery'
  'underscore'
  '../models/observable'
  '../models/color_table'
  'jquery.mousewheel' # to catch the 'mousewheel' event properly
], ($, _, observable, ColorTable) ->
  DEFAULT_OPTIONS = {
    color:
      line: '#888888'
      line_selected: '#000000'
      line_default: '#333333'
      line_faded: '#999999'
      highlight: '#6ab9e9'
    nodeStyles:
      normal:
        normal:
          strokeStyle: '#333333'
          lineWidth: 2
        highlighted:
          strokeStyle: '#6ab9e9'
          lineWidth: 2
      leaf:
        normal:
          strokeStyle: '#666666'
          lineWidth: 1
        highlighted:
          strokeStyle: '#6ab9e9'
          lineWidth: 1
      selected:
        normal:
          strokeStyle: '#000000'
          lineWidth: 4
        highlighted:
          strokeStyle: '#6ab9e9'
          lineWidth: 4
    maxNodeBorderWidth: 4 # px
    nodeCornerRadius: 5 # px

    connector_line_width: 2.5 # px
    node_expand_width: 1.5 # px
    node_expand_click_radius: 12 # px
    node_expand_radius: 6 # px
    mousewheel_zoom_factor: 1.2
  }

  HOVER_NODE_TEMPLATE = _.template("""
    <div class="inner">(<%- node.size.toLocaleString() %>) <%- node.description %></div>
  """)

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
    constructor: (@canvas, @tree, @colorLogic, @highlightedNodeIds, @focus, @options) ->
      @canvas.width = @width = Math.floor(parseFloat(@canvas.parentNode.clientWidth))
      @canvas.height = @height = Math.floor(parseFloat(@canvas.parentNode.clientHeight))

      @ctx = @canvas.getContext('2d')

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

        @canvas.width = old_width * ratio
        @canvas.height = old_height * ratio

        @canvas.style.width = "#{old_width}px"
        @canvas.style.height = "#{old_height}px"

        @ctx.scale(ratio, ratio)

      @ctx.lineStyle = @options.color.line
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

      this._draw_colors(drawableNodes)
      this._draw_nodes(drawableNodes)
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
        { event: key, id: node.json.id }

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
        if (tagwidth < 2) 
          tagwidth = 2 # always make the non-empty tag at least this wide on display, so visible

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

        if @colorLogic.searchResultIds?
          for id in @colorLogic.searchResultIds
            count = json.searchResultCounts?[id]
            if count
              color = @colorLogic.color(id)
              break
        else if @colorLogic.tagIds?
          for id in @colorLogic.tagIds
            count = json.tagCounts?[id]
            if count
              color = @colorLogic.color(id)
              break

        if color
          @_draw_tagcount(px.left, px.top, px.width, px.height, color, count / json.size)

      ctx.restore()
      undefined

    _draw_nodes: (drawableNodes) ->
      ctx = @ctx

      ctx.save()

      # We draw six categories, all nodes all at once: that's much faster
      # than styling every node individually.
      pxs =
        normal:
          normal: []
          highlighted: []
        selected:
          normal: []
          highlighted: []
        leaf:
          normal: []
          highlighted: []

      # Fill pxs
      (=>
        buffer = Math.max(@options.node_line_width, @options.node_line_width_selected, @options.node_line_width_leaf) * 0.5
        minX = 0
        maxX = @width

        for px in drawableNodes
          node = px.node

          continue if px.left + px.width + buffer < minX || px.left - buffer > maxX

          type1 = if (node.selected_fraction.v2? && node.selected_fraction.v2 == 1) || node.selected_fraction.current == 1
            'selected'
          else if px.isLeaf
            'leaf'
          else
            'normal'

          type2 = if node.json.id of @highlightedNodeIds
            'highlighted'
          else
            'normal'

          pxs[type1][type2].push(px)
      )()

      # Render pxs, each with the correct styles
      for type1, type2s of pxs
        for type2, nodes of type2s when nodes.length
          # Set styles
          for k, v of @options.nodeStyles[type1][type2]
            ctx[k] = v
          # Draw path
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

      ctx.strokeStyle = @options.color.line_faded
      ctx.beginPath()
      for px in drawableNodes when px.parent? && px.json.id not of @highlightedNodeIds
        drawLineToParent(px)
      ctx.stroke()

      ctx.strokeStyle = @options.color.highlight
      ctx.beginPath()
      for px in drawableNodes when px.parent? && px.json.id of @highlightedNodeIds
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
      glyphRadius = radius - 2
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

      # Draw circles
      for circle in expandCircles.concat(collapseCircles)
        xy = circle[0]
        ctx.beginPath()
        ctx.arc(xy.x, xy.y, radius, 0, Math.PI * 2, true)
        ctx.fill()
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

  class TreeView
    observable(this)

    constructor: (@div, @cache, @tree, @focus, options={}) ->
      options_color = _.extend({}, options.color, DEFAULT_OPTIONS.color)
      @options = _.extend({}, DEFAULT_OPTIONS, options, { color: options_color })
      @focus_tagids = (t.id for t in @cache.tag_store.tags)

      $div = $(@div)
      @canvas = $("<canvas width=\"#{$div.width()}\" height=\"#{$div.height()}\"></canvas>")[0]
      $div.append(@canvas)
      @$hover_node_description = $('<div class="hover-node-description" style="display:none;"></div>')
      $div.append(@$hover_node_description) # FIXME find a better place for this

      $zoom_buttons = $("""<div class="zoom-buttons">
          <button class="zoom-in">+</button>
          <button class="zoom-out">-</button>
        </div>""")
      $div.append($zoom_buttons)
      @zoomInButton = $zoom_buttons.find('.zoom-in')[0]
      @zoomOutButton = $zoom_buttons.find('.zoom-out')[0]

      this._attach()
      this.update()

    nodeid_above: (nodeid) ->
      @tree.id_tree.parent[nodeid]

    nodeid_below: (nodeid) ->
      try_nodeid = @tree.id_tree.children[nodeid]?[0]
      if @tree.on_demand_tree.nodes[try_nodeid]?
        try_nodeid
      else
        undefined

    # Returns the node to the left or right of the given node.
    #
    # If there is no sibling, this method will return undefined.
    _sibling: (nodeid, indexDiff) ->
      parentid = @tree.id_tree.parent[nodeid]
      if parentid
        siblings = @tree.id_tree.children[parentid]
        nodeIndex = siblings.indexOf(nodeid)
        siblingIndex = nodeIndex + indexDiff

        if 0 <= siblingIndex < siblings.length
          siblings[siblingIndex]
        else
          undefined
      else
        # root node has no siblings
        undefined

    nodeid_left: (nodeid) -> @_sibling(nodeid, -1)

    nodeid_right: (nodeid) -> @_sibling(nodeid, 1)

    _attach: () ->
      update = this._set_needs_update.bind(this)
      @tree.observe('needs-update', update)
      @focus.on('change', update)
      @cache.tag_store.observe('changed', update)
      $(window).on('resize.tree-view', update)

      @focus.on('change:zoom', this._refresh_zoom_button_status.bind(this))

      $(@zoomInButton).on 'click', (e) =>
        e.preventDefault()
        e.stopPropagation() # don't pan
        @_notify('zoom-pan', { zoom: @focus.get('zoom') * 0.5, pan: @focus.get('pan') }, { animate: true })
      $(@zoomOutButton).on 'click', (e) =>
        e.preventDefault()
        e.stopPropagation() # don't pan
        @_notify('zoom-pan', { zoom: @focus.get('zoom') * 2, pan: @focus.get('pan') }, { animate: true })

      @cache.tag_store.observe('added', this._on_tag_added.bind(this))
      @cache.tag_store.observe('removed', this._on_tag_removed.bind(this))
      @cache.tag_store.observe('id-changed', this._on_tagid_changed.bind(this))

      $(@canvas).on 'mousedown', (e) =>
        action = this._event_to_action(e)
        @set_hover_node(undefined) # on click, un-hover
        this._notify(action.event, action.id) if action

      this._handle_hover()
      this._handle_drag()
      this._handle_mousewheel()

    _on_tag_added: (tag) ->
      @focus_tagids.unshift(tag.id)
      # No need to redraw: that will happen elsewhere if necessary.

    _on_tag_removed: (tag) ->
      index = @focus_tagids.indexOf(tag.id)
      if index != -1
        @focus_tagids.splice(index, 1)
      # No need to redraw: that will happen elsewhere if necessary.

    _on_tagid_changed: (old_tagid, tag) ->
      index = @focus_tagids.indexOf(old_tagid)
      if index != -1
        @focus_tagids[index] = tag.id
      # No need to redraw: it would produce the same result.

    _handle_hover: () ->
      $(@canvas).on 'mousemove', (e) =>
        px = @_event_to_px(e) # might be undefined
        @set_hover_node(px)
        e.preventDefault()

      $(@canvas).on 'mouseleave', (e) =>
        @set_hover_node(undefined)
        e.preventDefault()

    _handle_drag: () ->
      $(@canvas).on 'mousedown', (e) =>
        return if e.which != 1
        e.preventDefault()

        start_x = e.pageX
        zoom = @focus.get('zoom')
        start_pan = @focus.get('pan')
        width = $(@canvas).width()
        dragging = false # if we only move one pixel, that doesn't count

        update_from_event = (e) =>
          dx = e.pageX - start_x

          dragging ||= true if Math.abs(dx) > 3

          return if !dragging

          d_pan = (dx / width) * zoom

          this._notify('zoom-pan', { zoom: zoom, pan: start_pan - d_pan })

        $('body').append('<div id="focus-view-mousemove-handler"></div>')
        $(document).on 'mousemove.tree-view', (e) ->
          update_from_event(e)
          e.stopImmediatePropagation() # prevent normal hover operation
          e.preventDefault()

        $(document).on 'mouseup.tree-view', (e) =>
          update_from_event(e)
          $('#focus-view-mousemove-handler').remove()
          $(document).off('.tree-view')
          e.preventDefault()

    _handle_mousewheel: () ->
      # When the user moves mouse wheel in, we divide zoom by a factor of
      # mousewheel_zoom_factor. We adjust pan to whatever will keep the mouse
      # cursor pointing to the same location, in absolute terms.
      #
      # Before zoom, absolute location is pan1 + (cursor_fraction - 0.5) * zoom1
      # After, it's pan2 + (cursor_fraction - 0.5) * zoom2
      #
      # So pan2 = pan1 + (cursor_fraction - 0.5) * zoom1 - (cursor_fraction - 0.5) * zoom2
      $(@canvas).on 'mousewheel', (e) =>
        e.preventDefault()
        offset = $(@canvas).offset()
        x = e.pageX - offset.left
        width = $(@canvas).width()

        sign = e.deltaY > 0 && 1 || -1

        zoom1 = @focus.get('zoom')
        zoom2 = zoom1 * Math.pow(@options.mousewheel_zoom_factor, -sign)
        pan1 = @focus.get('pan')
        relative_cursor_fraction = ((x / width) - 0.5)

        pan2 = pan1 + relative_cursor_fraction * zoom1 - relative_cursor_fraction * zoom2

        this._notify('zoom-pan', { zoom: zoom2, pan: pan2 })

    _event_to_px: (e) ->
      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_node(x, y)

    _event_to_action: (e) ->
      return undefined if !@tree.root?

      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_action(x, y)

    _getColorLogic: ->
      # Add the focused tag to "focus tagids": stack of recently-viewed tags
      # (initialized to all tags)
      if tagid = @tree.state.focused_tag?.id
        index = @focus_tagids.indexOf(tagid)
        # index may be:
        # * -1, if focused tag has been deleted
        # * 0, if it's at the top of the stack
        # * >0, if it's below the top item
        # If it's >0, move it to 0
        if index > 0
          @focus_tagids.splice(index, 1)
          @focus_tagids.unshift(tagid)

      # Cache colors, so each node shows most-recently-selected tag.
      color_table = new ColorTable()
      tag_id_to_color = {}
      for tag in @cache.tag_store.tags
        id = "#{tag.id}"
        color = tag.color || color_table.get(tag.name)
        tag_id_to_color[id] = color

      selection = @tree.state.selection
      if selection.searchResults.length
        {
          searchResultIds: selection.searchResults.slice(0)
          color: -> '#50ade5'
        }
      else if selection.tags.length
        {
          tagIds: selection.tags.slice(0)
          color: (id) -> tag_id_to_color[id]
        }
      else
        {
          tagIds: @focus_tagids.slice(0)
          color: (id) -> tag_id_to_color[id]
        }

    # Returns a set of { nodeid: null }.
    #
    # A node is highlighted if (one of the following):
    #
    # * The node or one of its children is selected
    # * A document in the node is selected
    _getHighlightedNodeIds: ->
      ret = {}

      # Selected nodes
      for nodeid in @tree.state.selection.nodes
        node = @tree.getAnimatedNode(nodeid)
        while node?
          ret[node.json.id] = null
          node = node.parent

      # Selected documents
      for docid in @tree.state.selection.documents
        document = @cache.document_store.documents[docid]
        if document?
          (ret[nodeid] = null) for nodeid in document.nodeids

      ret

    _redraw: () ->
      colorLogic = @_getColorLogic()
      highlightedNodeIds = @_getHighlightedNodeIds()

      shown_tagids = @tree.state.selection.tags
      shown_tagids = @focus_tagids if !shown_tagids.length

      @last_draw = new DrawOperation(@canvas, @tree, colorLogic, highlightedNodeIds, @focus, @options)
      @last_draw.draw()

    update: () ->
      @tree.update()
      @focus.update(@tree)
      this._redraw()
      @_needs_update = @tree.needsUpdate() || @focus.needsUpdate()

    needs_update: () ->
      @_needs_update

    _set_needs_update: () ->
      if !@_needs_update
        @_needs_update = true
        this._notify('needs-update')

    _refresh_zoom_button_status: ->
      @zoomInButton.className = @focus.isZoomedInFully() && 'disabled' || ''
      @zoomOutButton.className = @focus.isZoomedOutFully() && 'disabled' || ''

    # Sets the node being hovered.
    #
    # We'll adjust @$hover_node_description to match.
    set_hover_node: (px) ->
      if !px?
        @$hover_node_description.removeAttr('data-node-id')
        @$hover_node_description.hide()
        return

      # If we're here, drawable_node is valid
      json = px.json
      node_id_string = "#{json?.id}"

      return if @$hover_node_description.attr('data-node-id') == node_id_string

      # If we're here, we're hovering on a new node
      @$hover_node_description.hide()
      @$hover_node_description.empty()

      html = HOVER_NODE_TEMPLATE({ node: json })
      @$hover_node_description.append(html)
      @$hover_node_description.attr('data-node-id', node_id_string)
      @$hover_node_description.css({ opacity: 0.001 })
      @$hover_node_description.show() # Show it, so we can calculate dims

      h = @$hover_node_description.outerHeight(true)
      w = @$hover_node_description.outerWidth(true)

      $canvas = $(@canvas)
      offset = $canvas.offset()
      document_width = $(document).width()

      top = px.top - h
      left = px.hmid - w * 0.5

      if left + offset.left < 0
        left = 0
      if left + offset.left + w > document_width
        left = document_width - w - offset.left

      @$hover_node_description.css({
        left: left
        top: top
      })
      @$hover_node_description.animate({ opacity: 0.9 }, 'fast')
