observable = require('models/observable').observable

DEFAULT_OPTIONS = {
  color: {
    background: '#ffffff',
    node: '#ccccdd',
    node_unloaded: '#ddddff',
    node_selected: '#bbbbbb',
    line: '#888888',
  },
  connector_line_width: 1, # px
  node_line_width: 1.5, # px
  node_line_width_selected: 3, # px
  node_line_width_unloaded: 1, # px
  leaf_width: 3, # relative units
  leaf_horizontal_padding: 1, # on each side
  node_height: 10,
  node_vertical_padding: 3,
  animation_speed: 0, # no animations
  mousewheel_zoom_factor: 1.2,
}

class DrawOperation
  constructor: (@canvas, @options) ->
    @ctx = @canvas.getContext('2d')
    $canvas = $(@canvas)

    @ctx.lineStyle = @options.color.line

    @width = Math.ceil($canvas.parent().width())
    @height = Math.ceil($canvas.parent().height())

    @canvas.width = @width
    @canvas.height = @height

  clear: () ->
    @ctx.fillStyle = @options.color.background
    @ctx.fillRect(0, 0, @width, @height)

  calculate_subpixels: (num_documents, depth, zoom_factor, pan_fraction) ->
    depth = 0.00001 if depth == 0

    # We render to pixels, but our calculations are done with integers in
    # subpixel space. Multiply by "spxx" and "spxy" to convert to pixel space.
    @spxx_per_document = @options.leaf_width + 2 * @options.leaf_horizontal_padding
    npx = @spxx_per_document * num_documents
    @spxx = @width / (npx * zoom_factor)
    @left_px = (0.5 + pan_fraction - zoom_factor * 0.5) * npx * @spxx

    @spxy_per_level = @options.node_height + 2 * @options.node_vertical_padding
    @spxy = @height / (depth * @spxy_per_level)

  _node_to_color: (node) ->
    # FIXME interpolate colors
    if node.selected
      @options.color.node_selected
    else if node.loaded
      @options.color.node
    else
      @options.color.node_unloaded

  _node_to_line_width: (node) ->
    # FIXME interpolate widths
    if node.selected
      @options.node_line_width_selected
    else if node.loaded
      @options.node_line_width
    else
      @options.node_line_width_unloaded

  _node_to_connector_line_width: (node) ->
    @options.connector_line_width * node.loaded_animation_fraction.current

  draw_node: (node, documents_before, level) ->
    ctx = @ctx

    left = @spxx_per_document * documents_before * @spxx - @left_px
    top = @spxy_per_level * level * @spxy

    width = node.num_documents.current * @options.leaf_width * @spxx
    padding_x = node.num_documents.current * @options.leaf_horizontal_padding * @spxx
    height = @options.node_height * @spxy
    padding_y = @options.node_vertical_padding * @spxy

    ctx.lineWidth = this._node_to_line_width(node)
    ctx.fillStyle = this._node_to_color(node)

    ctx.fillRect(left + padding_x, top + padding_y, width, height)
    ctx.strokeRect(left + padding_x, top + padding_y, width, height)

    middle_x = left + padding_x + width * 0.5

    documents_drawn_in_children = documents_before

    for child_node in node.children
      child_middle_x = this.draw_node(child_node, documents_drawn_in_children, level + 1)
      documents_drawn_in_children += child_node.num_documents.current

      ctx.lineWidth = this._node_to_connector_line_width(child_node)

      ctx.beginPath()
      ctx.moveTo(middle_x, top + padding_y + height)
      ctx.bezierCurveTo(
        middle_x, top + 2 * padding_y + height,
        child_middle_x, top + 2 * padding_y + height,
        child_middle_x, top + 3 * padding_y + height
      )
      ctx.stroke()

    middle_x

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
      nodeid = this._pixel_to_nodeid(x, y)
      this._notify('click', nodeid)

    this._handle_drag()
    this._handle_mousewheel()

  _handle_drag: () ->
    $(@canvas).on 'mousedown', (e) =>
      return if e.which != 1

      start_x = e.pageX
      zoom = @focus.zoom
      start_pan = @focus.pan
      width = $(@canvas).width()

      update_from_event = (e) =>
        dx = e.pageX - start_x
        d_pan = (dx / width) * zoom

        this._notify('zoom-pan', { zoom: zoom, pan: start_pan + d_pan })

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

  _pixel_to_nodeid: (x, y) ->
    return undefined if @tree.root is undefined

    $canvas = $(@canvas)

    zoom = @focus.zoom
    pan = @focus.pan

    canvas_fraction = x / $canvas.width()
    doc_fraction = 0.5 + pan - zoom * 0.5 + canvas_fraction * zoom

    node = @tree.root
    doc_index = Math.floor(doc_fraction * node.num_documents.current)
    levels_to_go = Math.floor(y / $canvas.height() * @tree.animated_height.current)

    docs_to_our_left = 0
    while levels_to_go > 0 && node.children.length > 0
      for child_node in node.children
        if child_node.num_documents.current + docs_to_our_left <= doc_index
          docs_to_our_left += child_node.num_documents.current
        else
          break

      levels_to_go -= 1
      node = child_node

    node?.id

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

  #_draw_unloaded_node: (nodeid, ctx, x, y, w, h) ->
  #  ctx.fillStyle = @options.color.node_unloaded

  #  ctx.beginPath()
  #  ctx.moveTo(x, y + h * 0.5)
  #  ctx.quadraticCurveTo(x, y, x + w * 0.5, y)
  #  ctx.quadraticCurveTo(x + w, y, x + w, y + h * 0.5)
  #  ctx.quadraticCurveTo(x + w, y + h, x + w * 0.5, y + h)
  #  ctx.quadraticCurveTo(x, y + h, x, y + h * 0.5)
  #  ctx.fill()
  #  ctx.stroke()

  _redraw: () ->
    op = new DrawOperation(@canvas, @options)
    op.clear()

    return if @tree.root is undefined

    op.calculate_subpixels(
      @tree.root.num_documents.current,
      @tree.animated_height.current,
      @focus.zoom,
      @focus.pan,
    )

    op.draw_node(@tree.root, 0, 0)

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
