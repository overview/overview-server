define [
  'apps/Show/collections/Views'
], (Views) ->
  describe 'apps/Show/collections/Views', ->
    it 'should contain View objects', ->
      expect(Views.prototype.model.name).to.eq('View')

    it 'should order by createdAt ascending', ->
      views = new Views([
        { id: 1, type: 'view', createdAt: '2014-06-04T12:31:01Z' }
        { id: 2, type: 'view', createdAt: '2014-06-04T12:30:01Z' }
      ], url: 'url')
      expect(views.pluck('id')).to.deep.eq([ 2, 1 ])

    it 'should order jobs after errors after views', ->
      views = new Views([
        { id: 1, type: 'job' }
        { id: 2, type: 'error' }
        { id: 3, type: 'view', createdAt: '2014-06-04T12:30:01Z' }
      ], url: 'url')
      expect(views.pluck('id')).to.deep.eq([ 3, 2, 1 ])

    describe 'when polling', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)
        @views = new Views([
          { id: 2, type: 'job', createdAt: '2014-06-04T14:30:01Z' }
          { id: 1, type: 'view', createdAt: '2014-06-04T13:30:01Z' }
        ], url: '/path/to/views')
        @job = @views.get('job-2')
        @views.pollUntilStable()

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
        expect(req.url).to.eq('/path/to/views')

      it 'should not double up GET requests', ->
        @views.pollUntilStable()
        @sandbox.clock.tick(2000)
        expect(@sandbox.server.requests.length).to.eq(1)

      it 'should not queue a GET request when one is already in transit', ->
        @sandbox.clock.tick(2000)
        @views.pollUntilStable()
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
            { id: 1, type: 'view', createdAt: '2014-06-04T13:30:01Z' }
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
            { id: 2, type: 'view', createdAt: '2014-06-04T14:30:01Z', foo: 'bar' }
            { id: 1, type: 'view', createdAt: '2014-06-04T13:30:01Z' }
          ])
          @sandbox.clock.tick(1)

        it 'should replace the model', -> expect(@views.get('view-2')).to.exist
        it 'should not poll again', ->
          @sandbox.clock.tick(2000)
          expect(@sandbox.server.requests.length).to.eq(1)
