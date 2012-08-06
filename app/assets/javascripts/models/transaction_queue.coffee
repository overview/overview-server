class TransactionQueue
  constructor: () ->
    @_callbacks = []
    @_deferred = undefined

  queue: (callback) ->
    @_callbacks.push(callback)
    this._next()

  _next: () ->
    if !@_deferred? && @_callbacks.length > 0
      callback = @_callbacks.shift()

      @_deferred = callback.apply({})
      @_deferred.done =>
        @_deferred = undefined
        this._next()
      @_deferred.fail =>
        throw "transactionFailed"
        # since @_deferred is still set, the queue will be stalled forever

    undefined

exports = require.make_export_object('models/transaction_queue')
exports.TransactionQueue = TransactionQueue
