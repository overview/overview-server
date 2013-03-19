define [ './observable' ], (observable) ->
  MIN_ZOOM = 0.00001 # above 0

  # A focus is a pair of values: pan and zoom
  #
  # Zoom ranges from 1 (everything) to 0.00001 (a tiny slice).
  #
  # Pan is the center. A pan of 0 means center the entire image; a pan of 0.5 is
  # the complete edge.
  #
  # A pan of 0.5 is not a sane value: it implies that we will see (zoom/2) to the
  # right of 0.5, which is not okay. Sane values are between -0.5 and 0.5, not
  # inclusive.
  #
  # Usage:
  #
  #   focus = new AnimatedFocus(animator)
  #   focus.observe('zoom', (zoom) -> console.log(zoom))
  #   focus.observe('pan', (pan) -> console.log(pan))
  #   focus.observe('needs-update', () -> console.log('Needs update'))
  #   focus.set_zoom(0.5) # zoom 2x, callback called once
  #   focus.set_pan(-0.25) # pan to the left, callback called once
  #   focus.set_zoom_and_pan(0.5, -0.25) # equivalent
  #   focus.animate_zoom(0.25) # zoom 4x, animatedly, callback called lots, needs-update called
  #   focus.animate_pan(-0.375) # pan to the left, animatedly, callback called lots, needs-update left alone (because it was already called)
  #   focus.animate_zoom_and_pan(0.25, -0.375) # equivalent
  #   focus.needs_update() # true
  #   focus.update()
  #
  # Pan and zoom are always notified separately. The intent is for these values
  # to be eventually polled, as in a requestAnimationFrame() callback. Callers
  # should ensure pan and zoom are consistent with respect to one another; if
  # they aren't, AnimatedFocus will sanify them.
  #
  # Finally, some callers may want to "autofocus" during animation itself.
  # This is a state, @auto_pan_zoom_enabled, we toggle with two methods:
  #
  #   focus.set_auto_pan_zoom(false) # stop automatically pan/zooming to selection
  #   focus.block_auto_pan_zoom() # stop autofocusing
  #   focus.set_auto_pan_zoom(true) # would auto pan/zoom, except for block
  #   focus.unblock_auto_pan_zoom() # back to normal
  class AnimatedFocus
    observable(this)

    constructor: (@animator) ->
      @zoom = 1
      @pan = 0
      @_auto_pan_zoom = true
      @_auto_pan_zoom_blocks = 0
      @auto_pan_zoom_enabled = true
      @_animated_zoom = { current: @zoom }
      @_animated_pan = { current: @pan }

    _maybe_notifying_needs_update: (callback) ->
      old_needs_update = this.needs_update()
      callback.apply(this)
      this._notify('needs-update') if !old_needs_update && this.needs_update()

    set_zoom: (zoom) ->
      @animator.set_object_properties(this, { _animated_zoom: zoom })
      @zoom = this._sanify_zoom(@_animated_zoom.current)
      this._notify('zoom', zoom)

    set_pan: (pan) ->
      @animator.set_object_properties(this, { _animated_pan: pan })
      @pan = this._sanify_pan_at_zoom(@_animated_pan.current, @zoom)
      this._notify('pan', pan)

    set_zoom_and_pan: (zoom, pan) ->
      @set_zoom(zoom)
      @set_pan(pan)

    block_auto_pan_zoom: () ->
      @_auto_pan_zoom_blocks += 1
      @auto_pan_zoom_enabled = false

    unblock_auto_pan_zoom: () ->
      @_auto_pan_zoom_blocks -= 1
      @auto_pan_zoom_enabled = !@_auto_pan_zoom_blocks && @_auto_pan_zoom

    set_auto_pan_zoom: (@_auto_pan_zoom) ->
      @auto_pan_zoom_enabled = !@_auto_pan_zoom_blocks && @_auto_pan_zoom

    # Pans (and, if necessary, zooms) to ensure the range from "left" to "right"
    # are focused.
    #
    # The movement will be as slight as possible: a pan only if necessary (and
    # as little shift as necessary), and a zoom only if still necessary.
    fit_pan: (left, right) ->
      current_left = @pan - @zoom * 0.5
      current_right = @pan + @zoom * 0.5
      return if left >= current_left && right <= current_right

      min_zoom = right - left

      new_zoom = @zoom
      new_pan = @pan

      if min_zoom > @zoom
        new_zoom = min_zoom
        new_pan = (left + right) * 0.5
      else
        if left < current_left
          new_pan = left + new_zoom * 0.5
        else if right > current_right
          new_pan = right - new_zoom * 0.5

      @set_zoom_and_pan(new_zoom, new_pan)

    # Calls fit_pan(), only if @auto_pan_zoom_enabled
    auto_fit_pan: (left, right) ->
      if @auto_pan_zoom_enabled
        @fit_pan(left, right)

    animate_zoom: (zoom, time=undefined) ->
      time = Date.now() if !time?
      this._maybe_notifying_needs_update ->
        @animator.animate_object_properties(this, { _animated_zoom: zoom }, undefined, time)
      this._notify('zoom', zoom)

    animate_pan: (pan, time=undefined) ->
      time = Date.now() if !time?
      this._maybe_notifying_needs_update ->
        @animator.animate_object_properties(this, { _animated_pan: pan }, undefined, time)
      this._notify('pan', pan)

    _sanify_zoom: (zoom) ->
      if zoom > 1
        1
      else if zoom < MIN_ZOOM
        MIN_ZOOM
      else
        zoom

    _sanify_pan_at_zoom: (pan, zoom) ->
      if 2 * pan < zoom - 1
        pan = (zoom - 1) * 0.5
      else if 2 * pan > 1 - zoom
        pan = (1 - zoom) * 0.5
      else
        pan

    sane_target_zoom: () ->
      this._sanify_zoom(this.target_zoom())

    sane_target_pan: () ->
      zoom = this.sane_target_zoom()
      this._sanify_pan_at_zoom(this.target_pan(), zoom)

    target_zoom: () ->
      if @_animated_zoom.v2?
        @_animated_zoom.v2
      else
        @_animated_zoom.current

    target_pan: () ->
      if @_animated_pan.v2?
        @_animated_pan.v2
      else
        @_animated_pan.current

    update: () ->
      @animator.update()
      @zoom = this._sanify_zoom(@_animated_zoom.current)
      @pan = this._sanify_pan_at_zoom(@_animated_pan.current, @zoom)

    needs_update: () ->
      @animator.needs_update()
