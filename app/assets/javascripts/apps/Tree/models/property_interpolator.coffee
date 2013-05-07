define ->
  # Interpolates values.
  #
  # This class relies on a core object type, termed "Property", which callers
  # should treat like this:
  #
  #     property = { current: [value] }
  #
  # Internally, this class will add three properties: `v1`, `v2` and `start_ms`.
  #
  # A whole sequence looks like this:
  #
  #     t1 = 0 # milliseconds since epoch
  #     property = { current: 1 } # a property
  #     interpolator = new PropertyInterpolator(1000, (x) -> x) # 1000ms animation, linear easing
  #     interpolator.set_property_target(property, 2, t1)
  #     # property is now { current: 1, v1: 1, v2: 2, start_ms: 100 }
  #     # Now we can update...
  #     t2 = 200 # ms
  #     interpolator.update_property(property, t2) # property.current is now 1.2
  #     # Also, we can break apart the update into steps
  #     t3 = 400 # ms
  #     fraction = interpolator.start_time_to_current_eased_fraction(property.start_ms, t3) # 0.4
  #     interpolator.update_property_to_fraction(property, t3) # property.current is now 1.4
  #     # We can cancel the animation and set a new one
  #     t4 = 500
  #     interpolator.set_property_target(property, 3, t4) # property.current is still 0.4--it was not updated
  #     # Finally, when we pass start_ms+duration, v1, v2 and start_ms are deleted
  #     interpolator.update_property_to_fraction(property, 1) # property is now { current: 3 }
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
      if @duration == 0
        property.current = target
      else
        property.start_ms = start_ms? && start_ms || Date.now()
        property.v1 = property.current
        property.v2 = target
