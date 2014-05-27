define [
  'jquery'
  'apps/Tree/models/search_result_store'
], ($, SearchResultStore) ->
  describe 'models/search_result_store', ->
    describe 'SearchResultStore', ->
      lastTimeoutId = undefined
      lastXhr = undefined
      store = undefined
      storeIdChangedSpy = undefined
      storeChangedSpy = undefined
      storeAddedSpy = undefined
      storeRemovedSpy = undefined

      beforeEach ->
        lastTimeoutId = 0
        lastXhr = undefined
        @sandbox = sinon.sandbox.create()
        @sandbox.stub(window, 'setTimeout', -> lastTimeoutId += 1) # integer return value
        @sandbox.stub(window, 'clearTimeout')
        @sandbox.stub($, 'ajax', -> lastXhr = new $.Deferred()) # return Deferred
        store = new SearchResultStore('https://example.org')
        store.observe('id-changed', storeIdChangedSpy = sinon.spy())
        store.observe('changed', storeChangedSpy = sinon.spy())
        store.observe('added', storeAddedSpy = sinon.spy())
        store.observe('removed', storeRemovedSpy = sinon.spy())

      afterEach ->
        @sandbox.restore()
        store?.destroy()

      describe 'beginning empty', ->
        it 'should schedule a poll on addAndPoll()', ->
          store.addAndPoll({ query: 'query' })
          expect(window.setTimeout).to.have.been.called

        describe 'when a poll is scheduled', ->
          originalSearchResult = undefined

          beforeEach ->
            originalSearchResult = _.clone(store.addAndPoll({ query: 'query' }))

          it 'should not schedule any other polls', ->
            store.addAndPoll({ query: 'query2' })
            expect(window.setTimeout.callCount).to.eq(1)

          it 'should cancel polling on destroy()', ->
            store.destroy()
            expect(window.clearTimeout).to.have.been.calledWith(lastTimeoutId)

          describe 'when a poll is sent', ->
            beforeEach ->
              window.setTimeout.lastCall.args[0]()
              undefined

            it 'should send an AJAX request to pollUrl', ->
              expect($.ajax).to.have.been.calledWith({ type: 'GET', url: 'https://example.org' })

            it 'should not schedule any other polls', ->
              store.addAndPoll({ query: 'query2' })
              expect(window.setTimeout.callCount).to.eq(1)

            describe 'when the return value does not fulfill the request', ->
              beforeEach ->
                lastXhr.resolve([{ id: 1, query: 'query', state: 'InProgress' }])

              it 'should update the model anyway', ->
                expect(storeIdChangedSpy).to.have.been.calledWith(originalSearchResult.id, { id: 1, query: 'query', state: 'InProgress', position: 0 })
                expect(storeChangedSpy).to.have.been.calledWith({ id: 1, query: 'query', state: 'InProgress', position: 0 })

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).to.eq(2)

            describe 'when the return value fulfills the request', ->
              beforeEach ->
                lastXhr.resolve([{ id: 1, query: 'query', state: 'Complete' }])

              it 'should not schedule another poll', ->
                expect(window.setTimeout.callCount).to.eq(1)

            describe 'on error', ->
              beforeEach ->
                lastXhr.reject({}, 'failed')
                undefined

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).to.eq(2)

            describe 'when the first return value is missing the search key', ->
              beforeEach ->
                lastXhr.resolve([])

              it 'should not delete the model', ->
                expect(store.objects.length).to.eq(1)

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).to.eq(2)

      describe 'beginning full', ->
        beforeEach ->
          store.add({ id: 1, query: 'query', status: 'Complete' })

        describe 'when a poll is scheduled', ->
          originalSearchResult = undefined

          beforeEach ->
            originalSearchResult = _.clone(store.addAndPoll({ query: 'query2' }))

          describe 'when the server responds without the existing object', ->
            beforeEach ->
              window.setTimeout.lastCall.args[0]()
              lastXhr.resolve([{ id: 2, query: 'query2', status: 'Complete' }])

            it 'should remove the existing object', ->
              expect(store.objects.length).to.eq(1)
              expect(storeRemovedSpy).to.have.been.calledWith({ id: 1, query: 'query', status: 'Complete', position: 0 })
