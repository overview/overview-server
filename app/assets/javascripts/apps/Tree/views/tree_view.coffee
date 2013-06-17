define [
  'jquery'
  'underscore'
  '../models/observable'
  '../models/drawable_node'
  '../models/color_table'
  'jquery.mousewheel' # to catch the 'mousewheel' event properly
], ($, _, observable, DrawableNode, ColorTable) ->
  DEFAULT_OPTIONS = {
    color: {
      line: '#888888',
      line_selected: '#000000',
      line_default: '#333333',
      line_faded: '#999999',
    },
    connector_line_width: 2.5, # px
    node_corner_radius: 5, # px
    node_line_width: 2, # px
    node_expand_width: 1.5, # px
    node_expand_click_radius: 12 # px
    node_line_width_selected: 4, # px
    node_line_width_leaf: 1, # px
    start_fade_width: 10 #px begin fade to leaf color if node is narrower than this at current zoom
    mousewheel_zoom_factor: 1.2,
  }

  HOVER_NODE_TEMPLATE = _.template("""
    <div class="inner">(<%- node.doclist.n.toLocaleString() %>) <%- node.description %></div>
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
    constructor: (@canvas, @tree, @colorLogic, @focus_nodes, @focus, @options) ->
      $canvas = $(@canvas)
      @width = +Math.ceil($canvas.parent().width())
      @height = +Math.ceil($canvas.parent().height())

      @canvas.width = @width
      @canvas.height = @height

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
      @ctx.font = "12px Helvetica, Arial, sans-serif"
      @ctx.textBaseline = 'top'
      @ctx.shadowColor = 'white'

    clear: () ->
      @ctx.clearRect(0, 0, @width, @height)
      @root = undefined # will be overwritten if the tree isn't empty

    _auto_fit_pan: (drawable_node) ->
      if @focus_nodes?.length
        nodes = @focus_nodes

        # left_bound, right_bound: absolute X coordinates which must be in view
        left_bound = undefined
        right_bound = undefined
        @root.walk (dn) ->
          if nodes.indexOf(dn.animated_node.node.id) != -1
            # We want the outer bounds--that is, the bounds of the selected node
            # and its children.
            a = dn.absolute_position()
            width = dn.outer_width()
            node_left_bound = a.hmid - width * 0.5
            node_right_bound = a.hmid + width * 0.5

            left_bound = node_left_bound if !left_bound? || node_left_bound < left_bound
            right_bound = node_right_bound if !right_bound? || node_right_bound > right_bound

        if left_bound? && right_bound?
          # left_pan, right_pan: same, but as "focus" coordinates (from -0.5 to 0.5)
          tree_width = @root.outer_width()
          left_pan = left_bound / tree_width - 0.5
          right_pan = right_bound / tree_width - 0.5
          @focus.auto_fit_pan(left_pan, right_pan)

    draw: () ->
      this.clear()
      return if !@tree.root?

      @root = new DrawableNode(@tree.root)
      allNodes = @allDrawableNodes = []
      @root.walk((dn) -> allNodes.push(dn))
      @_auto_fit_pan()

      px_per_hunit = (@width - @options.node_line_width_selected) / @root.outer_width() / @focus.zoom
      px_per_vunit = (@height - 0.5 * @options.node_line_width_selected - 0.5 * @options.node_expand_click_radius) / @root.outer_height() # zoom doesn't affect Y axis
      pan_units = @root.outer_width() * (0.5 + @focus.pan - @focus.zoom * 0.5)

      # Set _px objects on all nodes
      @root.px(px_per_hunit, px_per_vunit, -0.5 * @options.node_line_width_selected / px_per_hunit + pan_units, 0.5 * @options.node_line_width_selected / px_per_vunit)

      for drawable_node in @allDrawableNodes
        this._draw_single_node(drawable_node)
      this._draw_nodes()
      this._draw_labels()
      this._draw_lines_from_parents_to_children()
      this._draw_expand_and_collapse_for_tree()

    pixel_to_drawable_node: (x, y) ->
      drawable_node = undefined
      @root?.walk (dn) ->
        return if drawable_node?
        px = dn._px
        if x >= px.left && x <= px.left + px.width && y >= px.top && y <= px.top + px.height
          drawable_node = dn
      drawable_node

    _pixel_to_expand_or_collapse_node: (x, y, list_of_circles) ->
      maxR2 = @options.node_expand_click_radius * @options.node_expand_click_radius
      for entry in (list_of_circles || []) # [ xy, drawable_node ] pairs
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
      event = (key, drawable_node) ->
        { event: key, id: drawable_node.animated_node.node.id }

      if drawable_node = @pixel_to_expand_node(x, y)
        event('expand', drawable_node)
      else if drawable_node = @pixel_to_collapse_node(x, y)
        event('collapse', drawable_node)
      else if drawable_node = @pixel_to_drawable_node(x, y)
        event('click', drawable_node)
        #if !drawable_node.animated_node.loaded
        #  event('expand', drawable_node)
        #else
        #  event('click', drawable_node)
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

      ctx.save()
      ctx.fillStyle = color
      ctx.beginPath()
      drawBottomRoundedRect(ctx, left, top + height * 0.75, tagwidth, height * 0.25, @options.node_corner_radius)
      ctx.fill()
      ctx.restore()

    _draw_single_node: (drawable_node) ->
      px = drawable_node._px
      animated_node = drawable_node.animated_node
      node = animated_node.node

      # Use the first tagid for which there's a count
      count = 0
      color = undefined
      if @colorLogic.searchResultIds?
        for id in @colorLogic.searchResultIds
          count = node.searchResultCounts?[id]
          if count
            color = @colorLogic.color(id)
            break
      if @colorLogic.tagIds?
        for id in @colorLogic.tagIds
          count = node.tagcounts?[id]
          if count
            color = @colorLogic.color(id)
            break

      if color
        this._draw_tagcount(px.left, px.top, px.width, px.height, color, count / node.doclist.n)

      undefined

    _draw_nodes: ->
      ctx = @ctx

      ctx.save()

      normalPxs = []
      selectedPxs = []
      leafPxs = []

      for dn in @allDrawableNodes
        animated_node = dn.animated_node
        px = dn._px
        if animated_node.selected
          selectedPxs.push(px)
        else if animated_node.children?.length is 0
          leafPxs.push(px)
        else
          normalPxs.push(px)

      minX = 0
      maxX = @width
      radius = @options.node_corner_radius
      drawPxs = (pxs) ->
        ctx.beginPath()
        for px in pxs
          continue if px.right < minX
          continue if px.left > maxX
          drawRoundedRect(ctx, px.left, px.top, px.width, px.height, radius)

      ctx.lineWidth = @options.node_line_width_selected
      ctx.strokeStyle = @options.color.line_selected
      ctx.beginPath()
      drawPxs(selectedPxs)
      ctx.stroke()

      ctx.lineWidth = @options.node_line_width_leaf
      ctx.strokeStyle = @options.color.line_faded
      ctx.beginPath()
      drawPxs(leafPxs)
      ctx.stroke()

      ctx.lineWidth = @options.node_line_width
      ctx.strokeStyle = @options.color.line_default
      ctx.beginPath()
      drawPxs(normalPxs)
      ctx.stroke()

      ctx.restore()

      undefined

    _draw_labels: ->
      ctx = @ctx

      ctx.save()

      ctx.fillStyle = '#333333'
      ctx.beginPath()

      maxX = @width

      for dn in @allDrawableNodes
        px = dn._px
        width = px.width - 12 # border + padding
        continue if width < 15

        continue if px.right < 0
        continue if px.left > maxX

        node = dn.animated_node.node
        description = node.description
        continue if !description

        left = px.left + 6
        right = left + width

        ctx.save()
        ctx.rect(left, px.top + 3, width, 15)
        ctx.clip()
        ctx.fillText(description, left, px.top + 3)
        ctx.restore()

      ctx.restore()

    _draw_lines_from_parents_to_children: ->
      ctx = @ctx

      ctx.save()

      lineWidth = ctx.lineWidth = @options.connector_line_width
      ctx.setLineDash?([ Math.ceil(@options.connector_line_width), Math.ceil(@options.connector_line_width) ])
      ctx.strokeStyle = @options.color.line_faded

      ctx.beginPath()

      minX = -@options.connector_line_width
      maxX = @width + @options.connector_line_width

      for dn in @allDrawableNodes when dn.parent?
        child_px = dn._px
        parent_px = dn.parent._px

        x1 = parent_px.hmid
        x2 = child_px.hmid

        continue if x1 < minX && x2 < minX
        continue if x1 > maxX && x2 > maxX

        y1 = parent_px.top + parent_px.height
        y2 = child_px.top
        mid_y = 0.5 * (y1 + y2)

        ctx.moveTo(x1, y1)
        ctx.bezierCurveTo(x1, mid_y + (0.1 * child_px.height), x2, mid_y - (0.1 * child_px.height), x2, y2)

      ctx.stroke()

      ctx.restore()

      undefined

    _draw_expand_and_collapse_for_tree: ->
      ctx = @ctx
      ctx.save()

      lineWidth = ctx.lineWidth = @options.node_expand_width
      ctx.strokeStyle = '#666666'
      ctx.fillStyle = '#ffffff'
      halfLineWidth = lineWidth * 0.5

      node_to_useful_xy = (drawable_node) ->
        px = drawable_node._px
        if px.width > 20
          x: px.hmid
          y: px.top + px.height - halfLineWidth
        else
          undefined

      expandCircles = @expand_circles = []
      collapseCircles = @collapse_circles = []

      for drawable_node in @allDrawableNodes
        xy = node_to_useful_xy(drawable_node)
        if xy?
          if drawable_node.children()?.length
            collapseCircles.push([ xy, drawable_node ])
          else if !drawable_node.animated_node.loaded
            expandCircles.push([ xy, drawable_node ])

      # Draw circles
      for circle in expandCircles.concat(collapseCircles)
        xy = circle[0]
        ctx.beginPath()
        ctx.arc(xy.x, xy.y, 6, 0, Math.PI * 2, true)
        ctx.fill()
        ctx.stroke()

      # Draw + and -'s
      ctx.beginPath()
      for circle in collapseCircles
        xy = circle[0]
        ctx.moveTo(xy.x - 4, xy.y)
        ctx.lineTo(xy.x + 4, xy.y)
      for circle in expandCircles
        xy = circle[0]
        ctx.moveTo(xy.x - 4, xy.y)
        ctx.lineTo(xy.x + 4, xy.y)
        ctx.moveTo(xy.x, xy.y + 4)
        ctx.lineTo(xy.x, xy.y - 4)
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

    # Returns the sibling (left or right) of the given node, or undefined if
    # there is no sibling.
    #
    # Parameters:
    # * nodeid: node ID to start at
    # * index_diff: +1 for node to the right; -1 for node to the left
    _nodeid_sibling: (nodeid, index_diff) ->
      parent_nodeid = @tree.id_tree.parent[nodeid]
      return undefined if !parent_nodeid?
      siblings = @tree.id_tree.children[parent_nodeid]
      node_index = siblings.indexOf(nodeid)
      sibling_index = node_index + index_diff
      if 0 <= sibling_index < siblings.length
        siblings[sibling_index]
      else
        undefined

    # Returns the node to the left or right of the given node.
    #
    # If there is no sibling, this method will traverse the tree to find a node
    # as nearby as possible and as close to the same level as possible.
    _nearby_nodeid_at_nearest_level: (nodeid, index_diff) ->
      # Make "nodeid" go up the tree until sibling_nodeid is found. Count the
      # levels we climb.
      levels_away = 0
      sibling_nodeid = undefined

      while true
        parent_nodeid = @tree.id_tree.parent[nodeid]
        return undefined if !parent_nodeid?
        sibling_nodeid = @_nodeid_sibling(nodeid, index_diff)
        if !sibling_nodeid?
          nodeid = parent_nodeid
          levels_away += 1
        else
          break

      # Descend the number of levels we've climbed. At the end, sibling_id will
      # be the result we want. parent_nodeid will be one above, in case we can't
      # descend all the way
      parent_nodeid = undefined # never return the parent
      while levels_away > 0 && sibling_nodeid?
        parent_nodeid = sibling_nodeid
        siblings = @tree.id_tree.children[parent_nodeid]
        # sibling_index: rightmost or leftmost index
        sibling_index = index_diff < 0 && (siblings.length - 1) || 0
        sibling_nodeid = siblings[sibling_index]
        # don't descend to nodes that can't be drawn
        sibling_nodeid = undefined if !@tree.id_tree.children[sibling_nodeid]
        levels_away -= 1

      sibling_nodeid || parent_nodeid

    nodeid_left: (nodeid) -> @_nearby_nodeid_at_nearest_level(nodeid, -1)

    nodeid_right: (nodeid) -> @_nearby_nodeid_at_nearest_level(nodeid, 1)

    _attach: () ->
      update = this._set_needs_update.bind(this)
      @tree.observe('needs-update', update)
      @focus.observe('needs-update', update)
      @focus.observe('zoom', update)
      @focus.observe('pan', update)
      @cache.tag_store.observe('changed', update)
      $(window).on('resize.tree-view', update)

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
        dn = @_event_to_drawable_node(e) # might be undefined
        @set_hover_node(dn)
        e.preventDefault()

      $(@canvas).on 'mouseleave', (e) =>
        @set_hover_node(undefined)
        e.preventDefault()

    _handle_drag: () ->
      $(@canvas).on 'mousedown', (e) =>
        return if e.which != 1
        e.preventDefault()

        @focus.block_auto_pan_zoom()

        start_x = e.pageX
        zoom = @focus.zoom
        start_pan = @focus.pan
        width = $(@canvas).width()

        update_from_event = (e) =>
          dx = e.pageX - start_x
          d_pan = (dx / width) * zoom

          this._notify('zoom-pan', { zoom: zoom, pan: start_pan - d_pan })

        $('body').append('<div id="mousemove-handler"></div>')
        $(document).on 'mousemove.tree-view', (e) ->
          update_from_event(e)
          e.stopImmediatePropagation() # prevent normal hover operation
          e.preventDefault()

        $(document).on 'mouseup.tree-view', (e) =>
          @focus.unblock_auto_pan_zoom()
          update_from_event(e)
          $('#mousemove-handler').remove()
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

        zoom1 = @focus.zoom
        zoom2 = zoom1 * Math.pow(@options.mousewheel_zoom_factor, -sign)
        pan1 = @focus.pan
        relative_cursor_fraction = ((x / width) - 0.5)

        pan2 = pan1 + relative_cursor_fraction * zoom1 - relative_cursor_fraction * zoom2

        this._notify('zoom-pan', { zoom: zoom2, pan: pan2 })

    _event_to_drawable_node: (e) ->
      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_drawable_node(x, y)

    _event_to_action: (e) ->
      return undefined if !@tree.root?

      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_action(x, y)

    _redraw: () ->
      # Add the focused tag to "focus tagids": stack of recently-viewed tags
      # (initialized to all tags)
      tagid = @tree.state.focused_tag?.id
      if tagid
        index = @focus_tagids.indexOf(tagid)
        if index == -1
          throw "Invalid tag"
        else if index != 0
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
      colorLogic = if selection.searchResults.length
        {
          searchResultIds: "#{id}" for id in selection.searchResults
          color: -> '#50ade5'
        }
      else if selection.tags.length
        {
          tagIds: "#{id}" for id in selection.tags
          color: (id) -> tag_id_to_color[id]
        }
      else
        {
          tagIds: "#{id}" for id in @focus_tagids
          color: (id) -> tag_id_to_color[id]
        }

      shown_tagids = @tree.state.selection.tags
      shown_tagids = @focus_tagids if !shown_tagids.length

      @last_draw = new DrawOperation(@canvas, @tree, colorLogic, @tree.state.selection.nodes, @focus, @options)
      @last_draw.draw()

    update: () ->
      @tree.update()
      @focus.update()
      this._redraw()
      @_needs_update = @tree.needs_update() || @focus.needs_update()

    needs_update: () ->
      @_needs_update

    _set_needs_update: () ->
      if !@_needs_update
        @_needs_update = true
        this._notify('needs-update')

    # Sets the node being hovered.
    #
    # We'll adjust @$hover_node_description to match.
    set_hover_node: (drawable_node) ->
      px = drawable_node?._px
      if !px?
        @$hover_node_description.removeAttr('data-node-id')
        @$hover_node_description.hide()
        return

      # If we're here, drawable_node is valid
      node = drawable_node.animated_node.node
      node_id_string = "#{node?.id}"

      return if @$hover_node_description.attr('data-node-id') == node_id_string

      # If we're here, we're hovering on a new node
      @$hover_node_description.hide()
      @$hover_node_description.empty()

      html = HOVER_NODE_TEMPLATE({ node: node })
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
