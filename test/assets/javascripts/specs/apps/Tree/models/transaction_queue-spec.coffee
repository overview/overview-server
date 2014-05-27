define [
  'jquery'
  'apps/Tree/models/transaction_queue'
], ($, TransactionQueue) ->
  Deferred = $.Deferred

  describe 'models/transaction_queue', ->
    describe 'TransactionQueue', ->
      queue = undefined

      beforeEach ->
        @sandbox = sinon.sandbox.create()
        queue = new TransactionQueue()

      afterEach ->
        @sandbox.restore()
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
        expect(run).to.be(true)

      it 'should run a second callback after the first', ->
        ds = []
        runs = 0

        cb = ->
          d = Deferred()
          d.done(-> runs += 1)
          ds.push(d)
          d

        @sandbox.stub(window, 'setTimeout')
        _.times(2, -> queue.queue(cb))
        expect(ds.length).to.eq(1)
        ds[0].resolve()
        expect(runs).to.eq(1)
        expect(window.setTimeout).to.have.been.called
        window.setTimeout.getCall(0).args[0].call()
        expect(ds.length).to.eq(2)
        ds[1].resolve()
        expect(runs).to.eq(2)
        expect(ds.length).to.eq(2)

      it 'should return a Deferred when queuing', ->
        op = Deferred()
        ret = queue.queue(-> op)
        expect(ret.state()).to.be('pending')
        op.resolve()
        expect(ret.state()).to.be('resolved')

      it 'should run a second callback after the queue has completed', ->
        d = undefined

        queue.queue(-> d = Deferred())
        d.resolve()

        run = false
        queue.queue(-> d = Deferred(); d.done(-> run = true))
        d.resolve()

        expect(run).to.be(true)

      it 'should throw an exception on failure', ->
        d = undefined
        queue.queue(-> d = Deferred())
        expect(-> d.reject()).to.throw("transactionFailed")

      it 'should halt operations after a failure', ->
        d = undefined

        queue.queue(-> d = Deferred())
        try
          d.reject()
        catch e
          # nothing

        called = false
        queue.queue(-> called = true; d = Deferred())
        expect(called).to.be(false)
