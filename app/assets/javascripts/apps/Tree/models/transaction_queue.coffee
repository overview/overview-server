define [ 'jquery' ], ($) ->
  class TransactionQueue
    constructor: () ->
      @_callbacks = [] # Array of { callback: ..., deferred: ... }
      @_running = false

    # Queues a callback.
    #
    # Params:
    # * callback: Code that returns a jQuery Promise. This code may be
    #   executed immediately (if the queue is empty) or later (if it isn't).
    # * debugInfo: Anything you want; this will appear in the debugger if the
    #   callback fails.
    #
    # Returns: a Promise that will be resolved (or rejected) when the
    # callback's Promise is. (It may be resolved by the time it's returned.)
    queue: (callback, debugInfo) ->
      deferred = $.Deferred()
      @_callbacks.push({ callback: callback, deferred: deferred, debugInfo: debugInfo })
      this._next()
      $.when(deferred)

    _next: () ->
      if !@_running && @_callbacks.length
        @_running = true

        obj = @_callbacks.shift()
        callback = obj.callback
        publicDeferred = obj.deferred

        callback.apply({})
          .done (args...) ->
            publicDeferred.resolve(args...)
          .fail (args...) ->
            console.log('Transaction failed', obj)
            alert('An unexpected error happened. Please refresh the page.')
            throw "transactionFailed" # The queue will be stalled forever!
            publicDeferred.reject(args...)
          .always =>
            @_running = false
            window.setTimeout((=> @_next()), 0)

      undefined
