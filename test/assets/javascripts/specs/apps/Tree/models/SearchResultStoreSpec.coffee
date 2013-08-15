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
        spyOn(window, 'setTimeout').andCallFake(-> lastTimeoutId += 1) # integer return value
        spyOn(window, 'clearTimeout')
        spyOn($, 'ajax').andCallFake(-> lastXhr = new $.Deferred()) # return Deferred
        store = new SearchResultStore('https://example.org')
        store.observe('id-changed', storeIdChangedSpy = jasmine.createSpy())
        store.observe('changed', storeChangedSpy = jasmine.createSpy())
        store.observe('added', storeAddedSpy = jasmine.createSpy())
        store.observe('removed', storeRemovedSpy = jasmine.createSpy())

      afterEach ->
        store?.destroy()

      describe 'beginning empty', ->
        it 'should schedule a poll on addAndPoll()', ->
          store.addAndPoll({ query: 'query' })
          expect(window.setTimeout).toHaveBeenCalled()

        describe 'when a poll is scheduled', ->
          originalSearchResult = undefined

          beforeEach ->
            originalSearchResult = _.clone(store.addAndPoll({ query: 'query' }))

          it 'should not schedule any other polls', ->
            store.addAndPoll({ query: 'query2' })
            expect(window.setTimeout.callCount).toEqual(1)

          it 'should cancel polling on destroy()', ->
            store.destroy()
            expect(window.clearTimeout).toHaveBeenCalledWith(lastTimeoutId)

          describe 'when a poll is sent', ->
            beforeEach ->
              window.setTimeout.mostRecentCall.args[0]()

            it 'should send an AJAX request to pollUrl', ->
              expect($.ajax).toHaveBeenCalledWith({ type: 'GET', url: 'https://example.org' })

            it 'should not schedule any other polls', ->
              store.addAndPoll({ query: 'query2' })
              expect(window.setTimeout.callCount).toEqual(1)

            describe 'when the return value does not fulfill the request', ->
              beforeEach ->
                lastXhr.resolve([{ id: 1, query: 'query', state: 'InProgress' }])

              it 'should update the model anyway', ->
                expect(storeIdChangedSpy).toHaveBeenCalledWith(originalSearchResult.id, { id: 1, query: 'query', state: 'InProgress', position: 0 })
                expect(storeChangedSpy).toHaveBeenCalledWith({ id: 1, query: 'query', state: 'InProgress', position: 0 })

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).toEqual(2)

            describe 'when the return value fulfills the request', ->
              beforeEach ->
                lastXhr.resolve([{ id: 1, query: 'query', state: 'Complete' }])

              it 'should not schedule another poll', ->
                expect(window.setTimeout.callCount).toEqual(1)

            describe 'on error', ->
              beforeEach ->
                lastXhr.reject({}, 'failed')

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).toEqual(2)

            describe 'when the first return value is missing the search key', ->
              beforeEach ->
                lastXhr.resolve([])

              it 'should not delete the model', ->
                expect(store.objects.length).toEqual(1)

              it 'should schedule another poll', ->
                expect(window.setTimeout.callCount).toEqual(2)

      describe 'beginning full', ->
        beforeEach ->
          store.add({ id: 1, query: 'query', status: 'Complete' })

        describe 'when a poll is scheduled', ->
          originalSearchResult = undefined

          beforeEach ->
            originalSearchResult = _.clone(store.addAndPoll({ query: 'query2' }))

          describe 'when the server responds without the existing object', ->
            beforeEach ->
              window.setTimeout.mostRecentCall.args[0]()
              lastXhr.resolve([{ id: 2, query: 'query2', status: 'Complete' }])

            it 'should remove the existing object', ->
              expect(store.objects.length).toEqual(1)
              expect(storeRemovedSpy).toHaveBeenCalledWith({ id: 1, query: 'query', status: 'Complete', position: 0 })
