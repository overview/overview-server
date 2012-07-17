class PropertyInterpolator
  constructor: (@duration, @easing) ->

  update_property: (property, at_time=undefined) ->
    if property.v1? && property.v2? && property.start_ms?
      fraction = this.start_time_to_current_eased_fraction(property.start_ms, at_time)
      this.update_property_to_fraction(property, fraction)

  start_time_to_current_eased_fraction: (start_ms, time_ms=undefined) ->
    time_ms = Date.now() if !time_ms?
    tdiff = (time_ms - start_ms)
    if tdiff >= @duration
      1
    else if tdiff <= 0
      0
    else
      @easing(tdiff / @duration)

  update_property_to_fraction: (property, fraction) ->
    if fraction == 1
      property.current = property.v2
      delete property.v1
      delete property.v2
      delete property.start_ms
    else if fraction == 0
      property.current = property.v1
    else
      property.current = this._interpolate_value(property.v1, property.v2, fraction)

  _interpolate_value: (v1, v2, fraction) ->
    if _.isArray(v1)
      this._interpolate_array(v1, v2, fraction)
    else
      this._interpolate_number(v1, v2, fraction)

  _interpolate_array: (v1, v2, fraction) ->
    this._interpolate_number(pair[0], pair[1], fraction) for pair in _.zip(v1, v2)

  _interpolate_number: (v1, v2, fraction) ->
    v1 + (v2 - v1) * fraction

  set_property_target: (property, target, start_ms=undefined) ->
    if @duration == 0 || _.isEqual(property.current, target)
      property.current = target
    else
      property.start_ms = start_ms? && start_ms || Date.now()
      property.v1 = property.current
      property.v2 = target

exports = require.make_export_object('models/property_interpolator')
exports.PropertyInterpolator = PropertyInterpolator
