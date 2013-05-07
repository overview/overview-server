define ->
  # Call this in your class, even CoffeeScript-style classes:
  #
  #   class MyClass
  #     observable(this)
  #     ...
  #
  # It will add observe(event, callback) and unobserve(event, callback) methods.
  # Then there's the _notify(event) method, which should be called from within
  # other methods.
  observable = (klass) ->
    klass.prototype.observe = (event, callback=undefined) ->
      if !callback?
        callback = event
        event = '_'
      all_observers = (@_observers ||= {})
      observers_for_event = (all_observers[event] ||= [])
      observers_for_event.push(callback)
      undefined

    klass.prototype.unobserve = (event, callback=undefined) ->
      if !callback?
        callback = event
        event = '_'
      observers_for_event = @_observers[event]
      index = observers_for_event.indexOf(callback)
      observers_for_event.splice(index, 1)
      undefined

    klass.prototype._notify = (event='_') ->
      all_observers = (@_observers ||= {})
      observers_for_event = (all_observers[event] ||= [])
      args = Array.prototype.slice.call(arguments, 1)
      for observer in observers_for_event
        observer.apply(this, args)
      undefined
