define ->
  class Callback
    constructor: (@callback, @needed_properties) ->

    mark_property_completed: (property) ->
      index = @needed_properties.indexOf(property)
      @needed_properties.splice(index, 1) if index != -1

    call_and_forget_if_ready: () ->
      if @needed_properties.length == 0
        @callback.call({})
        delete @callback

  # Animates properties 
  class Animator
    constructor: (@interpolator) ->
      @_tracked_properties = {} # hash of ms -> [ property, ... ]

    animate_object_properties: (object, new_values, callback=undefined, start_ms=undefined) ->
      tracked_callback = callback? && new Callback(callback, []) || undefined

      for k, v of new_values
        start_ms = start_ms? && start_ms || Date.now()
        property = object[k]
        @interpolator.set_property_target(property, v, start_ms)

        if property.start_ms?
          (@_tracked_properties[start_ms] ||= []).push(property)

          if tracked_callback?
            (property.tracked_callbacks ||= []).push(tracked_callback)
            tracked_callback.needed_properties.push(property)

      tracked_callback?.call_and_forget_if_ready()
      undefined

    set_object_properties: (object, new_values) ->
      for k, v of new_values
        property = object[k]
        start_ms = property.start_ms
        @interpolator.set_property_target(property, v)
        @interpolator.update_property_to_fraction(property, 1.0)

        if property.tracked_callbacks?
          for tracked_callback in property.tracked_callbacks
            tracked_callback.mark_property_completed(property)
            tracked_callback.call_and_forget_if_ready()
          delete property.tracked_callbacks

        if start_ms?
          properties_at_ms = @_tracked_properties[start_ms]
          idx = properties_at_ms.indexOf(property)
          if idx != -1
            if properties_at_ms.length == 1
              delete @_tracked_properties[start_ms]
            else
              properties_at_ms.splice(idx, 1)

      undefined

    update: (ms=undefined) ->
      ms = ms? && ms || Date.now()
      delete_ms = []

      for start_ms, properties of @_tracked_properties
        fraction = @interpolator.start_time_to_current_eased_fraction(start_ms, ms)
        for property in properties
          if +property.start_ms == +start_ms # we haven't updated it since
            @interpolator.update_property_to_fraction(property, fraction)
            if fraction >= 1 && property.tracked_callbacks?
              for tracked_callback in property.tracked_callbacks
                tracked_callback.mark_property_completed(property)
                tracked_callback.call_and_forget_if_ready()
              delete property.tracked_callbacks
        delete_ms.push(start_ms) if fraction >= 1

      delete @_tracked_properties[ms] for ms in delete_ms

      undefined

    needs_update: () ->
      for k, v of @_tracked_properties
        return true
      false
