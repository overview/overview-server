define [
  'apps/Show/collections/SearchResults'
], (SearchResults) ->
  describe 'apps/Show/collections/SearchResults', ->
    beforeEach -> @subject = new SearchResults([], url: '/documentsets/3/searches')

    it 'should order search results by descending createdAt', ->
      @subject.add(query: 'early', createdAt: new Date(5000))
      @subject.add(query: 'late', createdAt: new Date(10000))
      expect(@subject.pluck('query')).to.deep.eq([ 'late', 'early' ])

    describe 'when polling for a specific SearchResult', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)
        @subject.add(query: 'foo', createdAt: new Date(), state: 'InProgress')
        @subject.add(id: 2, query: 'bar', createdAt: new Date(), state: 'Complete')
        @search = @subject.at(0)
        @subject.pollUntilStable()

        @respond = (code, data) =>
          reqs = @sandbox.server.requests
          req = reqs[reqs.length - 1]
          req.respond(code, { 'Content-Type': 'application/json' }, JSON.stringify(data))

      afterEach -> @sandbox.restore()

      it 'should not make a GET request right away', ->
        @sandbox.clock.tick(1)
        expect(@sandbox.server.requests.length).to.eq(0)

      it 'should make a GET request after some time', ->
        @sandbox.clock.tick(2000)
        req = @sandbox.server.requests[0]
        expect(req.method).to.eq('GET')
        expect(req.url).to.eq('/documentsets/3/searches')

      describe 'when the server still does not know about the search', ->
        beforeEach ->
          @sandbox.clock.tick(2000)
          @respond(200, [
            @subject.at(1).toJSON()
          ])
          @sandbox.clock.tick(1)

        it 'should not remove the new search', ->
          expect(@subject.length).to.eq(2)
          expect(@subject.at(0)).to.eq(@search)

      describe 'when the server says the search is in progress', ->
        beforeEach ->
          newAttrs = @search.toJSON()
          newAttrs.id = 50
          @sandbox.clock.tick(2000)
          @respond(200, [
            newAttrs
            @subject.at(1).toJSON()
          ])

        it 'should not make a GET request right away', ->
          @sandbox.clock.tick(1)
          expect(@sandbox.server.requests.length).to.eq(1)

        it 'should give the model an ID', ->
          @sandbox.clock.tick(1)
          expect(@search.get('id')).to.eq(50)

        it 'should make a GET request after some time', ->
          @sandbox.clock.tick(2000)
          req = @sandbox.server.requests[1]
          expect(req.method).to.eq('GET')
          expect(req.url).to.eq('/documentsets/3/searches')

        it 'should not add another GET request when in a polling timeout', ->
          @sandbox.clock.tick(1)
          @subject.pollUntilStable()
          @sandbox.clock.tick(1)
          expect(@sandbox.server.requests.length).to.eq(1)

        it 'should not add another GET request when in an AJAX request', ->
          @sandbox.clock.tick(2000)
          @subject.pollUntilStable()
          @sandbox.clock.tick(1)
          expect(@sandbox.server.requests.length).to.eq(2)

      describe 'when the server says the search is done', ->
        beforeEach ->
          @sandbox.clock.tick(2000)
          newAttrs = @search.toJSON()
          newAttrs.id = 50
          newAttrs.state = 'Complete'
          @respond(200, [
            newAttrs
            @subject.at(1).toJSON()
          ])
          @sandbox.clock.tick(1)

        it 'should modify the model', ->
          expect(@search.get('state')).to.eq('Complete')

        it 'should not make another GET request', ->
          @sandbox.clock.tick(2000)
          expect(@sandbox.server.requests.length).to.eq(1)
