define [
  'jquery'
  'apps/Tree/models/transaction_queue'
], ($, TransactionQueue) ->
  Deferred = $.Deferred

  describe 'models/transaction_queue', ->
    describe 'TransactionQueue', ->
      queue = undefined

      beforeEach ->
        queue = new TransactionQueue()

      afterEach ->
        queue = undefined

      it 'should run a Deferred-returning callback when empty', ->
        d = undefined
        run = false

        cb = ->
          d = Deferred()
          d.done(-> run = true)
          d

        queue.queue(cb)
        d.resolve()
        expect(run).toBe(true)

      it 'should run a second callback after the first', ->
        ds = []
        runs = 0

        cb = ->
          d = Deferred()
          d.done(-> runs += 1)
          ds.push(d)
          d

        spyOn(window, 'setTimeout')
        _.times(2, -> queue.queue(cb))
        expect(ds.length).toEqual(1)
        ds[0].resolve()
        expect(runs).toEqual(1)
        expect(window.setTimeout).toHaveBeenCalled()
        window.setTimeout.mostRecentCall.args[0].call()
        expect(ds.length).toEqual(2)
        ds[1].resolve()
        expect(runs).toEqual(2)
        expect(ds.length).toEqual(2)

      it 'should return a Deferred when queuing', ->
        op = Deferred()
        ret = queue.queue(-> op)
        expect(ret.state()).toBe('pending')
        op.resolve()
        expect(ret.state()).toBe('resolved')

      it 'should run a second callback after the queue has completed', ->
        d = undefined

        queue.queue(-> d = Deferred())
        d.resolve()

        run = false
        queue.queue(-> d = Deferred(); d.done(-> run = true))
        d.resolve()

        expect(run).toBe(true)

      it 'should throw an exception on failure', ->
        d = undefined
        queue.queue(-> d = Deferred())
        expect(-> d.reject()).toThrow("transactionFailed")

      it 'should halt operations after a failure', ->
        d = undefined

        queue.queue(-> d = Deferred())
        try
          d.reject()
        catch e
          # nothing

        called = false
        queue.queue(-> called = true; d = Deferred())
        expect(called).toBe(false)
