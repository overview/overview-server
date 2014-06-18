define [
  'apps/Tree/collections/Vizs'
], (Vizs) ->
  describe 'apps/Tree/collections/Vizs', ->
    it 'should contain Viz objects', ->
      expect(Vizs.prototype.model.name).to.eq('Viz')

    it 'should order by createdAt descending', ->
      vizs = new Vizs([
        { id: 1, type: 'viz', createdAt: '2014-06-04T12:30:01Z' }
        { id: 2, type: 'viz', createdAt: '2014-06-04T12:31:01Z' }
      ], url: 'url')
      expect(vizs.pluck('id')).to.deep.eq([ 2, 1 ])

    it 'should order jobs before errors before vizs', ->
      vizs = new Vizs([
        { id: 1, type: 'viz', createdAt: '2014-06-04T12:30:01Z' }
        { id: 2, type: 'error' }
        { id: 3, type: 'job' }
      ], url: 'url')
      expect(vizs.pluck('id')).to.deep.eq([ 3, 2, 1 ])

    describe 'when polling', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)
        @vizs = new Vizs([
          { id: 2, type: 'job', createdAt: '2014-06-04T14:30:01Z' }
          { id: 1, type: 'viz', createdAt: '2014-06-04T13:30:01Z' }
        ], url: '/path/to/vizs')
        @job = @vizs.get('job-2')
        @vizs.pollUntilStable()

        @respond = (code, data) =>
          reqs = @sandbox.server.requests
          req = reqs[reqs.length - 1]
          req.respond(code, { 'Content-Type': 'application/json' }, JSON.stringify(data))

      afterEach ->
        @sandbox.restore()

      it 'should not make a GET request right away', ->
        @sandbox.clock.tick(1)
        expect(@sandbox.server.requests.length).to.eq(0)

      it 'should send a GET request after some time', ->
        @sandbox.clock.tick(2000)
        expect(@sandbox.server.requests.length).to.eq(1)
        req = @sandbox.server.requests[0]
        expect(req.method).to.eq('GET')
        expect(req.url).to.eq('/path/to/vizs')

      it 'should not double up GET requests', ->
        @vizs.pollUntilStable()
        @sandbox.clock.tick(2000)
        expect(@sandbox.server.requests.length).to.eq(1)

      it 'should not queue a GET request when one is already in transit', ->
        @sandbox.clock.tick(2000)
        @vizs.pollUntilStable()
        @sandbox.clock.tick(2000)
        expect(@sandbox.server.requests.length).to.eq(1)

      it 'should retry when the server returns an error', ->
        @sandbox.clock.tick(2000)
        @sandbox.stub(console, 'log')
        @respond(500, '"error"')
        @sandbox.clock.tick(2000)
        expect(@sandbox.server.requests.length).to.eq(2)

      describe 'when the server responds with a progress update', ->
        beforeEach ->
          @sandbox.clock.tick(2000)
          @respond(200, [
            { id: 2, type: 'job', createdAt: '2014-06-04T14:30:01Z', foo: 'bar' }
            { id: 1, type: 'viz', createdAt: '2014-06-04T13:30:01Z' }
          ])
          @sandbox.clock.tick(1)

        it 'should update the model', -> expect(@job.get('foo')).to.eq('bar')
        it 'should not poll right away', -> expect(@sandbox.server.requests.length).to.eq(1)

        it 'should poll again after a while', ->
          @sandbox.clock.tick(2000)
          expect(@sandbox.server.requests.length).to.eq(2)

      describe 'when the server responds with everything completed', ->
        beforeEach ->
          @sandbox.clock.tick(2000)
          @respond(200, [
            { id: 2, type: 'viz', createdAt: '2014-06-04T14:30:01Z', foo: 'bar' }
            { id: 1, type: 'viz', createdAt: '2014-06-04T13:30:01Z' }
          ])
          @sandbox.clock.tick(1)

        it 'should replace the model', -> expect(@vizs.get('viz-2')).to.exist
        it 'should not poll again', ->
          @sandbox.clock.tick(2000)
          expect(@sandbox.server.requests.length).to.eq(1)
