define [ 'jquery', '../models/observable' ], ($, observable) ->
  DEFAULT_OPTIONS = {
    handle_width: 11 #px
    border_width: 0 # px
  }

  class FocusView
    observable(this)

    constructor: (@div, @focus, options={}) ->
      @options = $.extend({}, DEFAULT_OPTIONS, options)
      this._add_html_elements()
      this._handle_dragging_middle()
      this._handle_dragging()
      this._handle_focus_changes()
      this._handle_window_resize()

    _handle_window_resize: () ->
      $(window).on('resize.focus-view', () => this.update())

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

        $('body').append('<div id="mousemove-handler"></div>')

        $(document).on 'mousemove.focus-view', (e) ->
          update_from_event(e)
          e.preventDefault()

        $(document).on 'mouseup.focus-view', (e) ->
          update_from_event(e)
          $('#mousemove-handler').remove()
          $(document).off('.focus-view')
          e.preventDefault()

    _handle_dragging: () ->
      $handle_left = $(@div).find('.handle.left')
      $handle_right = $(@div).find('.handle.right')

      $(@div).on 'mousedown', '.handle', (e) =>
        return if e.which != 1
        e.preventDefault()

        $elem = $(e.target)
        left_or_right = $elem.hasClass('left') && 'left' || 'right'
        xs_and_widths = this._get_xs_and_widths()
        positions = {
          left: xs_and_widths.middle.left
          right: xs_and_widths.middle.left + xs_and_widths.middle.width
        }
        start_x = e.pageX
        width = $(@div).width()

        update_from_event = (e) =>
          dx = e.pageX - start_x
          this_x = positions[left_or_right] + dx
          other_x = left_or_right == 'left' && positions.right || positions.left

          x1 = if other_x < this_x then other_x else this_x
          x2 = if other_x < this_x then this_x else other_x

          zoom = (x2 - x1) / width # difference
          pan = ((x1 / width - 0.5) + (x2 / width) - 0.5) / 2 # average

          this._notify('zoom-pan', { zoom: zoom, pan: pan })

        $('body').append('<div id="mousemove-handler"></div>')

        $(document).on 'mousemove.focus-view', (e) ->
          update_from_event(e)
          e.preventDefault()
        $(document).on 'mouseup.focus-view', (e) ->
          update_from_event(e)
          $('#mousemove-handler').remove()
          $(document).off('.focus-view')
          e.preventDefault()

    _add_html_elements: () ->
      $div = $(@div)
      $div.append('<div class="line"/><div class="handle left"/><div class="middle"/><div class="handle right"/>')
      height = $div.height()
      $div.find('.line').css({
        position: 'absolute'
        left: 0
        right: 0
      })
      $div.find('.handle').css({
        position: 'absolute'
        top: 0
        bottom: 0
        width: @options.handle_width
      })
      $div.find('.middle').css({
        position: 'absolute',
      })
      this._redraw()

    # Returns { left: { left: px, width: px }, middle: {}, right: {} }
    #
    # The position:absolute handles should be positioned according to these
    # numbers.
    #
    # There are a few guarantees:
    # * "left" and "right" width will == @options.handle_width
    # * "middle" width will be >= @options.handle_width
    # * "left" and "right" may overlap "middle", but they will always leave
    #   @options.handle_width pixels *not* overlapped.
    _get_xs_and_widths: () ->
      zoom = @focus.zoom
      pan = @focus.pan

      width = @div.clientWidth

      ret = {
        middle: {
          left: (0.5 + pan - zoom * 0.5) * width
          width: zoom * width
        }
      }

      if ret.middle.width < @options.handle_width
        handle_width_missing = @options.handle_width - ret.middle.width
        ret.middle.left -= handle_width_missing * 0.5
        ret.middle.width = @options.handle_width

      ret.left = {
        left: ret.middle.left - @options.handle_width * 0.5
        width: @options.handle_width
      }

      ret.right = {
        left: ret.middle.left + ret.middle.width - @options.handle_width * 0.5
        width: @options.handle_width
      }

      if ret.left.left < 0
        ret.left.left = 0

      if ret.right.left + ret.right.width > width
        ret.right.left = width - ret.right.width

      space_between = ret.right.left - (ret.left.left + ret.left.width)
      if space_between < @options.handle_width
        diff = @options.handle_width - space_between
        ret.left.left -= diff * 0.5
        ret.right.left += diff * 0.5

      ret.middle.left -= @options.border_width
      ret.middle.width += 2 * @options.border_width

      ret

    _redraw: () ->
      $div = $(@div)

      $handle1 = $div.find('.handle.left')
      $middle = $div.find('.middle')
      $handle2 = $div.find('.handle.right')

      positions = this._get_xs_and_widths()

      $handle1.css(positions.left)
      $middle.css(positions.middle)
      $handle2.css(positions.right)

    update: () ->
      this._redraw()
