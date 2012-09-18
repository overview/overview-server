observable = require('models/observable').observable
ColorTable = require('views/color_table').ColorTable

DEFAULT_OPTIONS = {
  node_hunits: 1,
  node_vunits: 1,
  node_hpadding: 0.5,
  node_vpadding: 0.7,
  color: {
    background: '#ffffff',
    line: '#888888',
    line_selected: '#000000',
    line_loaded: '#333333',
    line_leaf: '#999999',
  },
  connector_line_width: 1, # px
  node_line_width: 2, # px
  node_line_width_selected: 4, # px
  node_line_width_leaf: 1, # px
  animation_speed: 0, # no animations
  mousewheel_zoom_factor: 1.2,
}

class DrawOperation
  constructor: (@canvas, @tree, @zoom, @pan, @options) ->
    if @tree.state.focused_tag?
      @tag = {
        id: @tree.state.focused_tag.id,
        color: new ColorTable().get(@tree.state.focused_tag.name)
      }

    $canvas = $(@canvas)
    @width = +Math.ceil($canvas.parent().width())
    @height = +Math.ceil($canvas.parent().height())

    @canvas.width = @width
    @canvas.height = @height

    @ctx = @canvas.getContext('2d')
    @ctx.lineStyle = @options.color.line
    @ctx.font = '10px Helvetica, Arial, sans-serif'
    @ctx.textBaseline = 'top'

  clear: () ->
    @ctx.fillStyle = @options.color.background
    @ctx.fillRect(0, 0, @width, @height)

  draw: () ->
    this.clear()
    return if !@tree.root?

    @drawable_node = this._node_to_drawable_node(@tree.root)
    @drawable_node.relative_x = 0
    depth = @drawable_node.height

    @px_per_hunit = @width / @drawable_node.width_with_padding / @zoom
    @px_per_vunit = (@height - @options.node_line_width_selected) / ((depth > 1 && ((depth - 1) * @options.node_vpadding) || 0) + depth * @options.node_vunits)
    @px_pan = @width * ((0.5 + @pan) / @zoom - 0.5)

    this._draw_drawable_node(@drawable_node, { middle: @drawable_node.width_with_padding * 0.5 * @px_per_hunit - @px_pan })

  _pixel_is_within_node: (x, y, drawable_node) ->
    px = drawable_node.px
    x >= px.left && x <= px.left + px.width && y >= px.top && y <= px.top + px.height

  _pixel_to_drawable_node_recursive: (x, y, drawable_node) ->
    return drawable_node if this._pixel_is_within_node(x, y, drawable_node)

    if drawable_node.children?
      for child in drawable_node.children
        drawable_child = this._pixel_to_drawable_node_recursive(x, y, child)
        return drawable_child if drawable_child

    return undefined

  pixel_to_action: (x, y) ->
    drawable_node = this._pixel_to_drawable_node_recursive(x, y, @drawable_node)
    return undefined if !drawable_node?.node?

    px = drawable_node.px

    event = if px.width > 20 && x > px.middle - 5 && x < px.middle + 5 && y > px.top + px.height - 12 && y < px.top + px.height - 2
      if drawable_node.children?.length
        'collapse'
      else if drawable_node.node.children?
        'expand'
      else
        'click'
    else
      'click'

    return { event: event, id: drawable_node.node.id }

  _node_is_complete: (node) ->
    !_(node.children).any((n) => !n.loaded && !n.loaded_animation_fraction.current)

  _node_to_drawable_node: (node) ->
    fraction = node.loaded_animation_fraction.current
    hpadding = @options.node_hpadding * fraction

    drawable_node = {
      node: node,
      fraction: fraction,
      width: node.num_documents.current * fraction,
    }

    width_of_this_node_with_padding = drawable_node.width + (2 * hpadding)

    if !node.children.length || !this._node_is_complete(node)
      drawable_node.width_with_padding = width_of_this_node_with_padding
      drawable_node.height = fraction
    else
      drawable_node.children = _(node.children).map(this._node_to_drawable_node.bind(this))

      width_of_children_with_padding = _(drawable_node.children).reduce(((s, n) -> s + n.width_with_padding), 0) + (drawable_node.children.length + 1) * hpadding
      drawable_node.width_with_padding = if width_of_this_node_with_padding > width_of_children_with_padding
        width_of_this_node_with_padding
      else
        width_of_children_with_padding

      drawable_node.height = _(n.height for n in drawable_node.children).max() + fraction

      x = -0.5 * drawable_node.width_with_padding + hpadding
      for child in drawable_node.children
        child.relative_x = x + child.width_with_padding * 0.5
        x += child.width_with_padding + hpadding

    drawable_node

  _node_to_line_width: (node) ->
    if node.selected
      @options.node_line_width_selected
    else if node.children?.length is 0 # leaf node
      @options.node_line_width_leaf
    else
      @options.node_line_width

  _node_to_line_color: (node) ->
    if node.selected
      @options.color.line_selected
    else if node.children?.length is 0 # leaf node
      @options.color.line_leaf
    else
      @options.color.line_loaded

  _draw_tagcount: (left, top, width, height, color, fraction) ->
    return if fraction == 0

    slant_offset = height / 2
    tagwidth = 1.0 * (width + slant_offset) * fraction

    ctx = @ctx

    ctx.save()

    ctx.beginPath()
    ctx.rect(left, top, width, height)
    ctx.clip()

    ctx.fillStyle = @tag.color

    ctx.beginPath()
    ctx.moveTo(left, top)
    ctx.lineTo(left + tagwidth + slant_offset, top)
    ctx.lineTo(left + tagwidth - slant_offset, top + height)
    ctx.lineTo(left, top + height)
    ctx.fill()

    ctx.restore()

  _maybe_draw_description: (drawable_node) ->
    px = drawable_node.px
    width = px.width - 6 # border+padding
    return if width < 15

    id = drawable_node.node.id
    real_node = @tree.on_demand_tree.nodes[id]
    return if !real_node?.description

    ctx = @ctx

    left = px.left + 3
    right = left + width

    gradient = ctx.createLinearGradient(left, 0, right, 0)
    gradient.addColorStop((width-10)/width, 'rgba(0, 0, 0, 0.7)')
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
    ctx.save()
    ctx.beginPath()
    ctx.rect(left, px.top, width, px.height)
    ctx.clip()
    ctx.fillStyle = gradient
    ctx.fillText(real_node.description, left, px.top + 3)
    ctx.restore()

  _maybe_draw_collapse: (drawable_node) ->
    if drawable_node.children?.length
      px = drawable_node.px
      if px.width > 20
        ctx = @ctx
        y = px.top + px.height - 8
        x = px.middle
        ctx.lineWidth = 1
        ctx.strokeStyle = '#aaaaaa'
        ctx.beginPath()
        ctx.arc(x, y, 5, 0, Math.PI*2, true)
        ctx.moveTo(x - 3, y)
        ctx.lineTo(x + 3, y)
        ctx.stroke()

  _maybe_draw_expand: (drawable_node) ->
    if !drawable_node.children?.length && drawable_node.node.children?.length
      px = drawable_node.px
      if px.width > 20
        ctx = @ctx
        y = px.top + px.height - 8
        x = px.middle
        ctx.lineWidth = 1
        ctx.strokeStyle = '#aaaaaa'
        ctx.beginPath()
        ctx.arc(x, y, 5, 0, Math.PI*2, true)
        ctx.moveTo(x - 3, y)
        ctx.lineTo(x + 3, y)
        ctx.moveTo(x, y + 3)
        ctx.lineTo(x, y - 3)
        ctx.stroke()

  _measure_drawable_node: (drawable_node, parent_px) ->
    vpadding = @options.node_vpadding
    fraction = drawable_node.fraction
    px_per_hunit = @px_per_hunit
    vpx_of_fraction = fraction * @px_per_vunit

    px = drawable_node.px = {
      middle: parent_px.middle + drawable_node.relative_x * px_per_hunit,
      width: drawable_node.width * px_per_hunit,
      width_with_padding: drawable_node.width_with_padding * px_per_hunit,
      top: (parent_px.top? && (parent_px.top + parent_px.height + vpadding * vpx_of_fraction) || @options.node_line_width_selected * 0.5),
      height: @options.node_vunits * vpx_of_fraction,
    }
    px.left = px.middle - px.width * 0.5
    px.left_with_padding = px.middle - px.width_with_padding * 0.5

  _draw_measured_drawable_node: (drawable_node) ->
    px = drawable_node.px
    node = drawable_node.node

    if @tag? && tagcount = node.tagcounts?[@tag.id]
      this._draw_tagcount(px.left, px.top, px.width, px.height, @tag.color, tagcount / node.num_documents.current)

    ctx = @ctx
    ctx.lineWidth = this._node_to_line_width(node)
    ctx.strokeStyle = this._node_to_line_color(node)

    ctx.strokeRect(px.left, px.top, px.width, px.height)

    this._maybe_draw_collapse(drawable_node)
    this._maybe_draw_expand(drawable_node)
    this._maybe_draw_description(drawable_node)

  _draw_line_from_parent_to_child: (parent_px, child_px) ->
    x1 = parent_px.middle
    y1 = parent_px.top + parent_px.height
    x2 = child_px.middle
    y2 = child_px.top
    mid_y = 0.5 * (y1 + y2)

    ctx = @ctx
    ctx.lineWidth = @options.connector_line_width
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.bezierCurveTo(x1, mid_y + (0.1 * child_px.height), x2, mid_y - (0.1 * child_px.height), x2, y2)
    ctx.stroke()

  _draw_drawable_node: (drawable_node, parent_px) ->
    this._measure_drawable_node(drawable_node, parent_px)
    this._draw_measured_drawable_node(drawable_node)

    if drawable_node.children?
      for child_drawable_node in drawable_node.children
        this._draw_drawable_node(child_drawable_node, drawable_node.px)
        this._draw_line_from_parent_to_child(drawable_node.px, child_drawable_node.px)

    undefined

$ = jQuery
_ = window._

class TreeView
  observable(this)

  constructor: (@div, @tree, @focus, options={}) ->
    options_color = _.extend({}, options.color, DEFAULT_OPTIONS.color)
    @options = _.extend({}, DEFAULT_OPTIONS, options, { color: options_color })

    $div = $(@div)
    @canvas = $("<canvas width=\"#{$div.width()}\" height=\"#{$div.height()}\"></canvas>")[0]

    @_nodes = {}
    @_zoom_document = { current: -1 }
    @_zoom_factor = { current: 1 }

    $div.append(@canvas)

    this._attach()
    this.update()

  _attach: () ->
    @tree.id_tree.observe 'edit', =>
      if @_zoom_document.current == -1
        root_id = @tree.id_tree.root
        if root_id?
          @_zoom_document.current = @tree.root.num_documents.current / 2

    update = this._set_needs_update.bind(this)
    @tree.observe('needs-update', update)
    @focus.observe('needs-update', update)
    @focus.observe('zoom', update)
    @focus.observe('pan', update)
    $(window).on('resize.tree-view', update)

    $(@canvas).on 'click', (e) =>
      offset = $(@canvas).offset()
      $canvas = $(@canvas)
      x = e.pageX - offset.left
      y = e.pageY - offset.top
      action = this._pixel_to_action(x, y)
      this._notify(action.event, action.id) if action

    this._handle_drag()
    this._handle_mousewheel()

  _handle_drag: () ->
    $(@canvas).on 'mousedown', (e) =>
      return if e.which != 1
      e.preventDefault()

      start_x = e.pageX
      zoom = @focus.zoom
      start_pan = @focus.pan
      width = $(@canvas).width()

      update_from_event = (e) =>
        dx = e.pageX - start_x
        d_pan = (dx / width) * zoom

        this._notify('zoom-pan', { zoom: zoom, pan: start_pan - d_pan })

      $('body').on 'mousemove.tree-view', (e) ->
        update_from_event(e)
        e.preventDefault()

      $('body').on 'mouseup.tree-view', (e) ->
        update_from_event(e)
        $('body').off('.tree-view')
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

  _pixel_to_action: (x, y) ->
    return undefined if !@tree.root?

    @last_draw.pixel_to_action(x, y)

  _nodeid_to_n_documents: (nodeid) ->
    exact = @tree.nodes[nodeid]?.doclist?.n
    return exact if exact?

    # Divide the number of documents that must be in unresolved siblings by
    # the number of unresolved siblings.
    parent_nodeid = @tree.id_tree.parent[nodeid]
    parent_node = @tree.nodes[parent_nodeid]

    sibling_nodeids = @tree.id_tree.children[parent_nodeid]
    n_unknown_documents = parent_node.doclist.n
    n_unloaded_siblings = 0
    for sibling_nodeid in sibling_nodeids
      sibling = @tree.nodes[sibling_nodeid]
      if sibling?
        n_unknown_documents -= sibling.doclist.n
      else
        n_unloaded_siblings += 1

    n_unknown_documents / n_unloaded_siblings # we know n_unloaded_siblings > 1 because we're here

  _redraw: () ->
    @last_draw = new DrawOperation(@canvas, @tree, @focus.zoom, @focus.pan, @options)
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

exports = require.make_export_object('views/tree_view')
exports.TreeView = TreeView
