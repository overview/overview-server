define [ 'jquery', 'apps/Tree/models/TagLikeApi' ], ($, TagLikeApi) ->
  class MockTransactionQueue
    constructor: () ->
      @deferred = undefined
      @callbacks = []

    queue: (callback) ->
      @callbacks.push(callback)

    next: () ->
      @deferred = @callbacks.shift().apply()

  describe 'models/TagLikeApi', ->
    describe 'TagLikeApi', ->
      lastDeferred = undefined
      store =
        change: ->
      api = undefined
      transactionQueue = undefined

      beforeEach ->
        lastDeferred = undefined
        transactionQueue = new MockTransactionQueue()
        spyOn($, 'ajax').and.callFake(-> lastDeferred = $.Deferred())
        spyOn(store, 'change')
        api = new TagLikeApi(store, transactionQueue, '/root')

      describe 'create', ->
        tagLike = undefined
        beforeReceiveCalled = undefined

        beforeEach ->
          tagLike = { name: 'tag', color: '#abcdef' }
          beforeReceiveCalled = false
          api.create(tagLike, { beforeReceive: -> beforeReceiveCalled = true })

        describe 'before the transaction queue reaches it', ->
          it 'should not call $.ajax', ->
            expect($.ajax).not.toHaveBeenCalled()

          it 'should post something to the transaction queue', ->
            expect(transactionQueue.callbacks.length).toEqual(1)

        describe 'after sending to the server', ->
          lastAjaxArgs = undefined

          beforeEach ->
            transactionQueue.next()
            lastAjaxArgs = $.ajax.calls.mostRecent().args

          it 'should not call beforeReceive', ->
            expect(beforeReceiveCalled).toBe(false)

          it 'should have sent an AJAX request', ->
            expect($.ajax).toHaveBeenCalled()
            
          it 'should send POST', ->
            expect(lastAjaxArgs[0].type).toEqual('POST')

          it 'should send to the root url', ->
            expect(lastAjaxArgs[0].url).toEqual('/root')

          it 'should send the tagLike', ->
            expect(lastAjaxArgs[0].data).toEqual(tagLike)

          describe 'after receiving the server response', ->
            beforeEach ->
              lastDeferred.resolve({ id: 1, name: 'name', color: '#abcdef' })

            it 'should call beforeReceive', ->
              expect(beforeReceiveCalled).toBe(true)

            it 'should call store.change', ->
              expect(store.change).toHaveBeenCalledWith(tagLike, {
                id: 1
                name: 'name'
                color: '#abcdef'
              })

      describe 'update', ->
        tagLike = undefined

        beforeEach ->
          tagLike = { name: 'tag', color: '#abcdef' }
          api.update(tagLike)

        describe 'after sending to the server', ->
          lastAjaxArgs = undefined

          beforeEach ->
            tagLike.id = 2 # we set it later, as happens in The Real World
            transactionQueue.next()
            lastAjaxArgs = $.ajax.calls.mostRecent().args

          it 'should send PUT', ->
            expect(lastAjaxArgs[0].type).toEqual('PUT')

          it 'should send to the tag URL', ->
            expect(lastAjaxArgs[0].url).toEqual('/root/2')

          it 'should send the tagLike', ->
            expect(lastAjaxArgs[0].data).toEqual(tagLike)

      describe 'delete', ->
        tagLike = undefined

        beforeEach ->
          tagLike = { name: 'tag', color: '#abcdef' }
          api.destroy(tagLike)

        describe 'after sending to the server', ->
          lastAjaxArgs = undefined

          beforeEach ->
            tagLike.id = 2 # we set it later, as happens in The Real World
            transactionQueue.next()
            lastAjaxArgs = $.ajax.calls.mostRecent().args

          it 'should send DELETE', ->
            expect(lastAjaxArgs[0].type).toEqual('DELETE')

          it 'should send to the tag URL', ->
            expect(lastAjaxArgs[0].url).toEqual('/root/2')

          it 'should not send the tagLike', ->
            expect(lastAjaxArgs[0].data).toBeUndefined()
