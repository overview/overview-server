define [
  'apps/Tree/models/TransactionQueue'
], (TransactionQueue) ->
  describe 'models/TransactionQueue', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)
      @tq = new TransactionQueue()
      @tq.on('all', @allSpy = sinon.spy())

    afterEach ->
      @sandbox.restore()
      @tq?.off()

    describe 'when queuing a first request', ->
      beforeEach ->
        @success1 = sinon.spy()
        @promise1 = @tq.ajax(url: '/foo', success: @success1)
        @success2 = sinon.spy()
        @options2 = sinon.stub().returns(url: '/bar', success: @success2)
        @promise2 = @tq.ajax(@options2)
        @sandbox.clock.tick(1)

      it 'should return a Promise', -> expect(@promise1.then).to.be.an.instanceOf(Function)
      it 'should be unresolved', ->
        @promise1.then(then1 = sinon.spy())
        @sandbox.clock.tick(1)
        expect(then1).not.to.have.been.called

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
          @sandbox.clock.tick(1)

        it 'should call success', ->
          expect(@success1).to.have.been.calledWith(foo: 'bar')

        it 'should call then()', ->
          @promise1.then(then1 = sinon.spy())
          @sandbox.clock.tick(1)
          expect(then1).to.have.been.calledWith(foo: 'bar')

        it 'should not trigger any events', ->
          expect(@allSpy).not.to.have.been.called

        it 'should send the next request, which uses a callback to get options', ->
          req = @sandbox.server.requests[1]
          expect(req).not.to.be.undefined
          expect(req.url).to.eq('/bar')
          expect(req.method).to.eq('GET')

        describe 'after queue has been emptied', ->
          beforeEach ->
            @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, '""')
            @sandbox.clock.tick(1)

          it 'should handle the next ajax call', ->
            @tq.ajax(url: '/baz')
            @sandbox.clock.tick(1)
            req = @sandbox.server.requests[2]
            expect(req).not.to.be.undefined
            expect(req.url).to.eq('/baz')

      describe 'on server error', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(500, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
          @sandbox.clock.tick(1)

        it 'should not call success', -> expect(@success1).not.to.have.been.called
        it 'should not queue the next request', -> expect(@sandbox.server.requests.length).to.eq(1)

        it 'should call the error event', ->
          expect(@allSpy).to.have.been.calledWith('error')
          req = @sandbox.server.requests[0]
          xhr = @allSpy.args[0][1]
          expect(xhr.status).to.eq(500)

        it 'should reject the promise', ->
          @promise1.then(null, fail1 = sinon.spy())
          @sandbox.clock.tick(1)
          expect(fail1).to.have.been.called

      describe 'on network error', ->
        beforeEach ->
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
          @sandbox.clock.tick(1)

        it 'should not call success', -> expect(@success1).not.to.have.been.called
        it 'should not queue the next request', -> expect(@sandbox.server.requests.length).to.eq(1)

        it 'should neither resolve nor reject the promise', ->
          @promise1.then(then1 = sinon.spy(), fail1 = sinon.spy())
          @sandbox.clock.tick(1)
          expect(then1).not.to.have.been.called
          expect(fail1).not.to.have.been.called

        it 'should throw a network-error event', ->
          expect(@allSpy).to.have.been.calledWith('network-error')
          req = @sandbox.server.requests[0]
          xhr = @allSpy.args[0][1]
          expect(xhr.status).to.eq(0) # jQuery coerces -1 to 0

        describe 'on retry', ->
          beforeEach ->
            retry = @allSpy.args[0][2]
            retry()
            @sandbox.clock.tick(1)

          it 'should neither resolve nor reject the promise', ->
            @promise1.then(then1 = sinon.spy(), fail1 = sinon.spy())
            @sandbox.clock.tick(1)
            expect(then1).not.to.have.been.called
            expect(fail1).not.to.have.been.called

          it 'should re-send the request', ->
            req = @sandbox.server.requests[1]
            expect(req).not.to.be.undefined
            expect(req.url).to.eq('/foo')
            expect(req.method).to.eq('GET')

          it 'should work on success', ->
            @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
            @sandbox.clock.tick(1)
            expect(@success1).to.have.been.calledWith(foo: 'bar')

    describe 'with a custom error handler', ->
      it 'should invoke a custom error handler', ->
        error = sinon.spy()
        @tq.ajax(url: '/foo', error: error)
        @sandbox.clock.tick(1)
        @sandbox.server.requests[0].respond(500, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
        expect(error).to.have.been.called

      it 'should let the custom handler invoke the original handler', ->
        originalError = sinon.spy()
        error = (xhr, textStatus, errorThrown, defaultHandler) -> defaultHandler()
        @tq.ajax(url: '/foo', error: error)
        @tq.on('error', originalError)
        @sandbox.clock.tick(1)
        @sandbox.server.requests[0].respond(500, { 'Content-Type': 'application/json' }, '{ "foo": "bar" }')
        expect(originalError).to.have.been.called
