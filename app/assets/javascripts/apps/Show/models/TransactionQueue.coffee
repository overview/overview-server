define [
  'underscore'
  'jquery'
  'backbone'
  'oboe'
], (_, $, Backbone, oboe) ->
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
      @_last = Promise.resolve(null)

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
      @_last = @_last.then =>
        new Promise (resolve, reject) =>
          options = options?() || options

          doIt = ->
            jqxhr = $.ajax(options)
            jqxhr.done(resolve)

          handleError = (xhr, textStatus, errorThrown) =>
            console.log('XMLHttpRequest error:', textStatus, errorThrown)
            if xhr.status <= 0
              @trigger('network-error', xhr, doIt)
            else
              reject(xhr, textStatus, errorThrown)
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

    # Queues a streaming-read-JSON call.
    #
    # Usage:
    #
    #   tq = new TransactionQueue
    #   tq.streamJsonArray({
    #     url: '/foo',
    #     onStart: console.log,
    #     onItem: console.log,
    #     onSuccess: console.log,
    #   })
    #
    # The server is expected to stream a valid HTTP response with Content-Type
    # `application/json` (UTF-8 encoded), which is a JSON Array. onItem() will
    # be called with each element of the Array.
    #
    # For instance, if the server sends `[ 1, 2, 3, 4 ]`, then the
    # TransactionQueue will invoke:
    #
    #     onStart()   # the server responded with status 200-299
    #     onItem(1)
    #     onItem(2)
    #     onItem(3)
    #     onItem(4)
    #     onSuccess() # the request is complete
    #
    # Error handling:
    #
    # The TransactionQueue may emit an 'error' event when the initial server
    # response fails. It may emit a 'network-error' event at any time before
    # completion. If you retry after a 'network-error', onStart() will be
    # called again, and onItem() will be called for all items the server returns
    # during the retry -- including any items you have already processed.
    #
    # The browser cannot stream infinitely-large JSON, because XMLHttpRequest
    # won't let it. Limit the streamed response size to, say, 20MB.
    #
    # Returns: a Promise that will be resolved (or rejected) when the request
    # is complete.
    streamJsonArray: (options) ->
      throw new Error('Must pass options.url') if !options.url

      url = options.url
      onStart = options.onStart || () ->
      onItem = options.onItem || () ->
      onSuccess = options.onSuccess || () ->

      @_last = @_last.then =>
        new Promise (resolve, reject) =>
          doIt = =>
            oboe(url)
              .on 'start', ->
                onStart()
              .on 'node', '![*]', (item) ->
                onItem(item)
                oboe.drop
              .on 'done', ->
                onSuccess()
                resolve()
              .on 'fail', (report) =>
                console.log('streamJsonArray error:', report.statusCode, report.thrown)
                if report.statusCode
                  reject(report)
                  @trigger('error', {}, report.statusCode, report.thrown)
                else
                  @trigger('network-error', {}, doIt)
                undefined

          doIt()
