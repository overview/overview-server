define [
  'underscore'
  'jquery'
  'backbone'
  'rsvp'
], (_, $, Backbone, RSVP) ->
  # A set of AJAX operations that execute one at a time.
  #
  # Usage:
  #
  #   tq = new TransactionQueue
  #
  #   # You can queue AJAX requests based on the state right now
  #   promise1 = tq.ajax(url: 'foo', debugInfo: 'blah', success: ...)))
  #
  #   # You can queue AJAX requests based on a later state
  #   #
  #   # The callback will be invoked to find options immediately before the
  #   # request is sent.
  #   optionsCallback = -> url: 'bar', success: ...
  #   promise2 = tq.ajax(optionsCallback)
  #
  # A note on ordering: callers should probably use the `success` option, which
  # gets passed directly to jQuery.ajax(), rather than using `promise1.then()`.
  # Thats because TransactionQueue will start the next request before your code
  # executes its `.then()` clause. In contrast, your `success` is guaranteed to
  # happen before any `.then()`.
  #
  # TransactionQueue also implements a Backbone.Event interface for error
  # handling:
  #
  #   tq.on('error', ((xhr, textStatus, errorThrown) -> alert("Server returned 400-ish or 500-ish error")))
  #   tq.on 'network-error', ((xhr, retry) ->
  #     if confirm("The network seems down. Retry?")
  #       retry()
  #
  # There is no recovery from an `error`; you may `retry()` a `network-error`
  # until it succeeds or errors. In general, the user's best recourse is to
  # refresh the page.
  #
  # You can circumvent TransactionQueue's error handling for a specific request
  # by defining your own `error` option in `.ajax()`. This is appropriate if,
  # say, you expect the server to return a `404` error for a missing resource.
  # The fourth argument to the `error` callback is TransactionQueue's default
  # error handler; you may call it with no arguments in your custom error
  # handler.
  class TransactionQueue
    _.extend(@::, Backbone.Events)

    constructor: ->
      @_requests = [] # Array AJAX options
      @_last = RSVP.resolve(null)

    # Queues a $.ajax() call.
    #
    # Options: a bunch of options:
    # * jQuery.ajax() options: see https://api.jquery.com/jquery.ajax/
    # * debugInfo: Anything you want; this will appear in the debugger if the
    #   callback fails.
    #
    # Returns: a Promise that will be resolved (or rejected) when the
    # callback's Promise is. (It may be resolved by the time it's returned.)
    ajax: (options) ->
      @_last.then(=> @_poll())
      @_last = new RSVP.Promise (resolve, reject) =>
        @_requests.push(options: options, resolve: resolve, reject: reject)

    _poll: ->
      next = @_requests.shift()

      options = next.options
      options = options?() || options

      doIt = ->
        jqxhr = $.ajax(options)
        jqxhr.done(next.resolve)

      handleError = (xhr, textStatus, errorThrown) =>
        if xhr.status <= 0
          @trigger('network-error', xhr, doIt)
        else
          next.reject(xhr, textStatus, errorThrown)
          @trigger('error', xhr, textStatus, errorThrown)
        undefined

      originalErrorHandler = options.error
      if originalErrorHandler
        options.error = (xhr, textStatus, errorThrown) ->
          defaultHandler = -> handleError(xhr, textStatus, errorThrown)
          originalErrorHandler(xhr, textStatus, errorThrown, defaultHandler)
      else
        options.error = handleError

      doIt()

      undefined
