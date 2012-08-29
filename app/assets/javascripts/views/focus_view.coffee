observable = require('models/observable').observable

DEFAULT_OPTIONS = {
  handle_color: '#cccccc',
  handle_border_color: '#000000',
  handle_border_radius: 1, #px
  handle_width: 11, #px
  handle_border_width: 1, #px
  line_color: '#aaaaaa',
  line_height: 1, #px
  middle_color: '#aaddaa',
  middle_height: 5, #px
}

class FocusView
  observable(this)

  constructor: (@div, @focus, options={}) ->
    @options = _.extend({}, DEFAULT_OPTIONS, options)
    this._add_html_elements()
    this._handle_dragging_middle()
    this._handle_dragging()
    this._handle_focus_changes()

  _handle_focus_changes: () ->
    @focus.observe('zoom', => this.update())
    @focus.observe('pan', => this.update())

  _handle_dragging_middle: () ->
    $(@div).on 'mousedown', '.middle', (e) =>
      return if e.which != 1
      e.preventDefault()

      $elem = $(e.target)
      start_x = e.pageX
      width = $(@div).width()
      start_pan = @focus.pan

      update_from_event = (e) =>
        dx = e.pageX - start_x
        d_pan = dx / width
        pan = start_pan + d_pan
        this._notify('zoom-pan', { zoom: @focus.zoom, pan: start_pan + d_pan })

      $('body').on 'mousemove.focus-view', (e) ->
        update_from_event(e)
        e.preventDefault()

      $('body').on 'mouseup.focus-view', (e) ->
        update_from_event(e)
        $('body').off('.focus-view')
        e.preventDefault()

  _handle_dragging: () ->
    $handle_left = $(@div).find('.handle.left')
    $handle_right = $(@div).find('.handle.right')

    $(@div).on 'mousedown', '.handle', (e) =>
      return if e.which != 1
      e.preventDefault()

      $elem = $(e.target)
      left_or_right = $elem.hasClass('left') && 'left' || 'right'
      positions = this._get_left_and_right_x_from_focus_and_width()
      start_x = e.pageX
      width = $(@div).width()

      update_from_event = (e) =>
        dx = e.pageX - start_x
        this_x = positions[left_or_right] + dx
        other_x = left_or_right == 'left' && positions['right'] || positions['left']

        x1 = if other_x < this_x then other_x else this_x
        x2 = if other_x < this_x then this_x else other_x

        zoom = (x2 - x1) / width # difference
        pan = ((x1 / width - 0.5) + (x2 / width) - 0.5) / 2 # average

        this._notify('zoom-pan', { zoom: zoom, pan: pan })

      $('body').on 'mousemove.focus-view', (e) ->
        update_from_event(e)
        e.preventDefault()
      $('body').on 'mouseup.focus-view', (e) ->
        update_from_event(e)
        $('body').off('.focus-view')
        e.preventDefault()

  _add_html_elements: () ->
    $div = $(@div)
    $div.append('<div class="line"/><div class="handle left"/><div class="middle"/><div class="handle right"/>')
    height = $div.height()
    $div.find('.line').css({
      position: 'absolute',
      top: (height - @options.line_height) * 0.5,
      height: @options.line_height,
      'background-color': @options.line_color,
      left: 0,
      right: 0,
    })
    $div.find('.handle').css({
      position: 'absolute',
      top: 0,
      bottom: 0,
      'background-color': @options.handle_color,
      'border-color': @options.handle_border_color,
      'border-radius': @options.handle_border_radius,
      'border-width': @options.handle_border_width,
      'border-style': 'solid',
      width: @options.handle_width,
    })
    $div.find('.middle').css({
      position: 'absolute',
      top: (height - @options.middle_height) * 0.5,
      height: @options.middle_height,
      'background-color': @options.middle_color,
    })
    this._redraw()

  _get_left_and_right_x_from_focus_and_width: () ->
    zoom = @focus.zoom
    pan = @focus.pan

    width = $(@div).width()

    {
      left: (0.5 + pan - zoom * 0.5) * width,
      right: (0.5 + pan + zoom * 0.5) * width,
    }

  _redraw: () ->
    $div = $(@div)

    $handle1 = $div.find('.handle.left')
    $middle = $div.find('.middle')
    $handle2 = $div.find('.handle.right')

    positions = this._get_left_and_right_x_from_focus_and_width()

    handle_width = $handle1.outerWidth()

    positions.left_minus_handle = positions.left - handle_width * 0.5
    positions.left_plus_handle = positions.left + handle_width * 0.5
    positions.right_minus_handle = positions.right - handle_width * 0.5
    positions.right_plus_handle = positions.right + handle_width * 0.5

    if positions.left_minus_handle < 0
      d = positions.left_minus_handle
      positions.left -= d
      positions.left_plus_handle -= d
      positions.left_minus_handle -= d

    width = $(@div).width()

    if positions.right_plus_handle > width
      d = positions.right_plus_handle - width
      positions.right_plus_handle -= d
      positions.right -= d
      positions.right_minus_handle -= d

    $handle1.css({ left: positions.left_minus_handle })
    $middle.css({ left: positions.left_plus_handle, width: positions.right_minus_handle - positions.left_plus_handle })
    $handle2.css({ left: positions.right_minus_handle })

  update: () ->
    this._redraw()

exports = require.make_export_object('views/focus_view')
exports.FocusView = FocusView
