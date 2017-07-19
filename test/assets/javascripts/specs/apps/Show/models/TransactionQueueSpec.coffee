define [
  'apps/Show/models/TransactionQueue'
], (TransactionQueue) ->
  describe 'models/TransactionQueue', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @sandbox.server.respondImmediately = false
      @sandbox.server.autoRespond = false
      # when sinon XHR gets a request, it'll install fake setTimeout, even
      # though we really don't want it. That means we can't use setTimeout()
      # throughout this file.
      @realSetTimeout = window.setTimeout
      @nextTick = (fn) =>
        @realSetTimeout.call(window, fn, 0)

      @sandbox.stub(console, 'log')
      @tq = new TransactionQueue()
      @tq.on('all', @allSpy = sinon.spy())

    afterEach ->
      @sandbox.restore()
      @tq?.off()

    describe 'when queuing a first request', ->
      beforeEach (done) ->
        @success1 = sinon.spy()
        @promise1 = @tq.ajax(url: '/foo', success: @success1)
        @success2 = sinon.spy()
        @options2 = sinon.stub().returns(url: '/bar', success: @success2)
        @promise2 = @tq.ajax(@options2)
        @nextTick(done) # wait for promises to catch up

      it 'should be unresolved', ->
        expect(@promise1).not.to.be.fulfilled
        undefined

      it 'should send a request', ->
        req = @sandbox.server.requests[0]
        expect(req).not.to.be.undefined
        expect(req.url).to.eq('/foo')
        expect(req.method).to.eq('GET')

      it 'should not call an options-generating callback', ->
        expect(@options2).not.to.have.been.called

      describe 'on success', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
          @promise1

        it 'should call success', -> expect(@success1).to.have.been.calledWith(foo: 'bar')
        it 'should fulfill the promise', -> expect(@promise1).to.eventually.deep.eq(foo: 'bar')
        it 'should not trigger any events', -> expect(@allSpy).not.to.have.been.called

        it 'should send the next request, which uses a callback to get options', ->
          req = @sandbox.server.requests[1]
          expect(req).not.to.be.undefined
          expect(req.url).to.eq('/bar')
          expect(req.method).to.eq('GET')

        describe 'after queue has been emptied', ->
          beforeEach ->
            @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, '""')
            @promise2

          it 'should handle the next ajax call', (done) ->
            @tq.ajax(url: '/baz')
            @nextTick =>
              req = @sandbox.server.requests[2]
              expect(req).to.exist
              expect(req.url).to.eq('/baz')
              done()

      describe 'on server error', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(500, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
          @promise1.catch(-> null)

        it 'should not call success', -> expect(@success1).not.to.have.been.called
        it 'should not queue the next request', -> expect(@sandbox.server.requests.length).to.eq(1)
        it 'should reject the promise', -> expect(@promise1).to.have.been.rejectedWith(status: 500)

        it 'should call the error event', ->
          expect(@allSpy).to.have.been.calledWith('error')
          req = @sandbox.server.requests[0]
          xhr = @allSpy.args[0][1]
          expect(xhr.status).to.eq(500)

      describe 'on network error', ->
        beforeEach (done) ->
          # Try out $.get('https://localhost:1') and you'll find that among
          # the weird error-y stuff, there's a "status: 0".
          #
          # That makes sense, because the server will never send back that
          # status so we know it's an error.
          #
          # In our test, we have to pretend the server gave it back.
          #
          # We can't use status 0 for the _test_ because that's a success for
          # file: protocol requests. So our code needs to handle status <= 0.
          @sandbox.server.requests[0].respond(-1, { 'Content-Type': 'application/json' }, '""')
          @nextTick(done)

        it 'should not call success', -> expect(@success1).not.to.have.been.called
        it 'should not queue the next request', -> expect(@sandbox.server.requests.length).to.eq(1)

        it 'should neither resolve nor reject the promise', ->
          expect(@promise1).not.to.be.fulfilled
          undefined

        it 'should throw a network-error event', ->
          expect(@allSpy).to.have.been.calledWith('network-error')
          req = @sandbox.server.requests[0]
          xhr = @allSpy.args[0][1]
          expect(xhr.status).to.eq(0) # jQuery coerces -1 to 0

        describe 'on retry', ->
          beforeEach (done) ->
            retry = @allSpy.args[0][2]
            retry()
            @nextTick(done)

          it 'should neither resolve nor reject the promise', ->
            expect(@promise1).not.to.be.fulfilled
            undefined

          it 'should re-send the request', ->
            req = @sandbox.server.requests[1]
            expect(req).not.to.be.undefined
            expect(req.url).to.eq('/foo')
            expect(req.method).to.eq('GET')

          it 'should work on success', ->
            @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
            expect(@promise1).to.eventually.deep.eq(foo: 'bar')

    describe 'when queueing an Oboe request', ->
      beforeEach (done) ->
        @onStart1 = sinon.spy()
        @onItem1 = sinon.spy()
        @onSuccess1 = sinon.spy()
        @promise1 = @tq.streamJsonArray(url: '/foo', onStart: @onStart1, onItem: @onItem1, onSuccess: @onSuccess1)

        @onStart2 = sinon.spy()
        @onItem2 = sinon.spy()
        @onSuccess2 = sinon.spy()
        @promise2 = @tq.streamJsonArray(url: '/bar', onStart: @onStart2, onItem: @onItem2, onSuccess: @onSuccess2)

        @nextTick(done) # wait for promises to catch up

      it 'should be unresolved', ->
        expect(@promise1).not.to.be.fulfilled
        undefined

      it 'should send a request', ->
        req = @sandbox.server.requests[0]
        expect(req).not.to.be.undefined
        expect(req.url).to.eq('/foo')
        expect(req.method).to.eq('GET')

      it 'should not send subsequent requests', ->
        expect(@sandbox.server.requests.length).to.eq(1)

      describe 'on success', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '[ [ 1, 2 ], [ 3, 4 ] ]')
          @promise1

        it 'should call onStart()', ->
          expect(@onStart1).to.have.been.called

        it 'should call onItem() with each item', ->
          expect(@onItem1).to.have.been.calledWith([ 1, 2 ])
          expect(@onItem1).to.have.been.calledWith([ 3, 4 ])

        it 'should call onSuccess()', ->
          expect(@onSuccess1).to.have.been.called

        it 'should resolve the promise', ->
          expect(@promise1).to.eventually.be.undefined

        it 'should send the next request', ->
          req = @sandbox.server.requests[1]
          expect(req).not.to.be.undefined
          expect(req.url).to.eq('/bar')
          expect(req.method).to.eq('GET')

      describe 'on failure (status code)', ->
        beforeEach (done) ->
          @sandbox.server.requests[0].respond(404, { 'Content-Type': 'application/json' }, '{ "key": "not-found" }')
          @nextTick(done)

        it 'should reject the promise', ->
          expect(@promise1).to.eventually.be.rejected

        it 'should trigger an error event', ->
          expect(@allSpy).to.have.been.calledWith('error')
          expect(@allSpy.args[0][1]).to.deep.eq({})
          expect(@allSpy.args[0][2]).to.eq(404)
          expect(@promise2).not.to.be.fulfilled
          undefined

      describe 'on incomplete result (network error)', ->
        beforeEach (done) ->
          @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, '[ [ 1, 2 ], [ 3, 4')
          @nextTick(done)

        it 'should call onStart() and onItem() for the parts that succeeded', ->
          expect(@onStart1).to.have.been.called
          expect(@onItem1).to.have.been.calledWith([ 1, 2 ])
          expect(@onItem1).not.to.have.been.calledWith([ 3, 4 ])
          expect(@onSuccess1).not.to.have.been.called

        it 'should trigger a network-error event', ->
          expect(@allSpy).to.have.been.calledWith('network-error')
          expect(@allSpy.args[0][1]).to.deep.eq({})
          expect(@promise2).not.to.be.fulfilled
          undefined

        describe 'on retry', ->
          beforeEach (done) ->
            retry = @allSpy.args[0][2]
            retry()
            @nextTick(done)

          it 'should neither resolve nor reject the promise', ->
            expect(@promise1).not.to.be.fulfilled
            undefined

          it 'should re-send the request', ->
            req = @sandbox.server.requests[1]
            expect(req).not.to.be.undefined
            expect(req.url).to.eq('/foo')
            expect(req.method).to.eq('GET')

          it 'should work on success', ->
            @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, '[ "foo", "bar" ]')
            expect(@promise1).to.eventually.be.fulfilled
              .then =>
                expect(@onItem1).to.have.been.calledWith("foo")
                expect(@onItem1).to.have.been.calledWith("bar")
                expect(@onSuccess1).to.have.been.called

    describe 'with a custom error handler', ->
      beforeEach ->
        @sandbox.server.respondImmediately = true
        @sandbox.server.respondWith([ 500, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }' ])

      it 'should let the custom handler invoke the original handler', ->
        @tq.on('error', originalError = sinon.spy())
        error = (xhr, textStatus, errorThrown, defaultHandler) -> defaultHandler()
        @tq.ajax(url: '/foo', error: error)
          .catch(-> null)
          .then(-> expect(originalError).to.have.been.called)
