import $ from 'jquery'
import Backbone from 'backbone'
import oboe from 'oboe'

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
# A note on ordering: callers can use the `success` option, which gets passed
# directly to jQuery.ajax(), rather than using `promise1.then()`.
# TransactionQueue starts pending requests before your `.then()` clauses
# execute. In contrast, it calls `success` before any `.then()`.
#
# TransactionQueue is a Backbone.Model to help report and tweak status:
#
#   tq.on('change:error', (t, error) => {
#     if (error !== null) {
#       const { xhr, textStatus, errorThrown } = t.get('error')
#       // There is no recovering from this error: no more requests will run
#       // until the next page load.
#       ...
#     }
#   })
#   tq.on('change:networkError', (t, error) => {
#     if (error !== null) {
#       if confirm('The network seems down. Retry?') {
#         // clear error (triggers change:networkError) and re-run last request
#         tq.retry()
#       } else {
#         alert('Okay, you said no, so requests to Overview are halted forever')
#       }
#     }
#   })
#
# You can circumvent TransactionQueue's error handling for a specific request
# by defining your own `error` option in `.ajax()`. This is appropriate if,
# say, you expect the server to return a `404` error for a missing resource.
# Write your error handler exactly as you would with $.ajax(), except you may
# use a fourth argument: TransactionQueue's default error handler (which you
# may call without arguments).
export default class TransactionQueue extends Backbone.Model
  defaults:
    nAjaxIncomplete: 0,
    error: null,
    networkError: null,

  initialize: ->
    @_last = Promise.resolve(null)

  # Queues a $.ajax() call.
  #
  # Options: a bunch of options:
  # * jQuery.ajax() options: see https://api.jquery.com/jquery.ajax/
  # * debugInfo: Anything you want; this will appear in the debugger if the
  #   callback fails.
  #
  # Returns: a Promise that will be resolved when the callback's Promise is.
  # It will never be rejected: it will simply stall forever instead.
  #
  # Attributes to monitor:
  #
  # * nAjaxIncomplete: increments when operation starts; decrements after
  #                    "success" callback and before Promise resolution.
  # * error: set (and never unset) on unhandled server error.
  # * networkError: set (and unset if you retry()) on incomplete request.
  ajax: (options) ->
    handleError = (xhr, textStatus, errorThrown) =>
      console.log('XMLHttpRequest error:', xhr.status, textStatus, errorThrown)
      if xhr.status <= 0
        @set(networkError: { xhr: xhr, textStatus: textStatus, errorThrown: errorThrown })
      else
        @set(error: { xhr: xhr, textStatus: textStatus, errorThrown: errorThrown })
        # Do not reject the Promise. Assume something is displaying our error.
        #reject(xhr, textStatus, errorThrown)
      undefined

    @set(nAjaxIncomplete: @get('nAjaxIncomplete') + 1)

    @_last = @_last.then =>
      options = if typeof options == 'function'
        # Allow caller to pass _function_ that resolves to options immediately
        # before request. This is useful when we want to queue two calls right
        # away but the second depends on the return value of the first.
        #
        # Needless to say, this feature was coded before Promises made it big.
        # Never use this. Use Promises instead.
        options()
      else
        Object.assign({}, options)

      originalErrorHandler = options.error
      options.error = if originalErrorHandler
        (xhr, textStatus, errorThrown) ->
          defaultHandler = () -> handleError(xhr, textStatus, errorThrown)
          originalErrorHandler(xhr, textStatus, errorThrown, defaultHandler)
      else
        handleError

      onSuccess = options.success || () ->

      new Promise (resolve, reject) =>
        @retry = doIt = =>
          options.success = (args...) =>
            @retry = () => null
            @set(nAjaxIncomplete: @get('nAjaxIncomplete') - 1)
            onSuccess(args...)
            resolve(args...)

          @set(networkError: null)
          $.ajax(options)
            .then(() ->, () ->)

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
  #     onStartError: console.warn,
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
  # Lots can go wrong. To handle the server sending a non-200-range status
  # code, pass an `onStartError` Function. It will be called with
  # `{ statusCode: XXX, jsonBody: '"response from server"' }`. (The server
  # must respond with JSON.) If you pass `onStartError`, the Promise will
  # be _resolved_ after the error occurs -- regardless of what the error
  # handler does. (TODO consider changing this.)
  #
  # Unlike .ajax(), this returned Promise will be rejected when the server
  # responds with non-200.
  #
  # The TransactionQueue may set 'error' if the initial server response
  # fails. It may set 'networkError' at any time before completion. If you
  # retry() after a 'networkError', onStart() will be called again, and
  # onItem() will be called for all items the server returns during the
  # retry -- including any items you have already processed.
  #
  # This will not modify 'nAjaxIncomplete'. Rationale: the UX you use to
  # stream will probably render progress in its own way.
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
    onStartError = options.onStartError || null

    @_last = @_last.then =>
      new Promise (resolve, reject) =>
        doResolve = () =>
          @retry = () => null
          resolve()

        streamServerResponse = (stream) ->
          # Call this when the server responds with 200. It'll call
          # onStart(), onItem() a few times and then onSuccess() (unless
          # there's a 'fail').
          onStart()

          stream.on 'node', '![*]', (item) ->
            onItem(item)
            oboe.drop

          stream.on 'done', ->
            onSuccess()
            doResolve()

        streamServerError = (stream, statusCode) =>
          # Call this when the server responds non-200. It'll call
          # onStartError() (unless the server response is invalid JSON, in
          # which case there will be an error).
          stream.on 'fail', (report) =>
            if onStartError
              onStartError(report)
              doResolve()
            else
              @set(error: { xhr: {}, textStatus: String(report.statusCode), errorThrown: report.thrown })

        @retry = doIt = () =>
          @set(networkError: null)
          hasStartError = false

          stream = oboe(url)

          stream.on 'start', (statusCode) ->
            if String(statusCode)[0] == '2'
              streamServerResponse(stream)
            else
              streamServerError(stream, statusCode)

          stream.on 'fail', (report) =>
            if !report.statusCode || String(report.statusCode)[0] == '2'
              # This should never happen -- it means the server made a mistake
              if report.statusCode
                @set(error: { xhr: {}, textStatus: String(report.statusCode), errorThrown: report.thrown })
              else
                @set(networkError: { xhr: {}, textStatus: '', errorThrown: report.thrown })
              undefined

        doIt()
