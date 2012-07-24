observable = require('models/observable').observable

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
class AnimatedFocus
  observable(this)

  constructor: (@animator) ->
    @zoom = 1
    @pan = 0
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

exports = require.make_export_object('models/animated_focus')
exports.AnimatedFocus = AnimatedFocus
