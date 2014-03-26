PATH = 'apps/DocumentCloudImportForm/models/DocumentCloudQueryFetcher'

define [ 'jquery', 'backbone', PATH ], ($, Backbone, Fetcher) ->
  withFakeCorsSupport = (cors, callback) ->
    realCors = $.support.cors
    $.support.cors = cors
    callback()
    $.support.cors = realCors

  dummyCredentials = (options) -> $.extend({
    toAuthHeaders: -> { 'Authorization': 'Basic aAbBcC==' }
    toPostData: -> { email: 'user@example.org', password: 'password' }
    isComplete: -> true
  }, options || {})

  describe PATH, ->
    query = undefined
    model = undefined
    ajaxDeferred = undefined

    beforeEach ->
      query = new Backbone.Model({ id: 'projectid:1' })
      query.url = -> 'https://www.documentcloud.org/api/search?q=projectid:1'
      model = new Fetcher({ query: query, credentials: dummyCredentials() })
      ajaxDeferred = new $.Deferred()

      spyOn($, 'ajax').and.returnValue(ajaxDeferred)

    describe 'fetchQuery', ->
      # We make all browsers test CORS and non-CORS, because our workflow
      # doesn't include testing across all browsers during development.
      it 'should send a CORS AJAX request', ->
        withFakeCorsSupport(true, -> model.fetchQuery())
        expect($.ajax).toHaveBeenCalled()
        options = $.ajax.calls.mostRecent().args[0]
        expect(options.type).toEqual('GET')
        expect(options.timeout).toBeDefined()
        expect(options.headers?.Authorization).toBeDefined()

      it 'should send a non-CORS AJAX request, via proxy', ->
        withFakeCorsSupport(false, -> model.fetchQuery())
        expect($.ajax).toHaveBeenCalled()
        options = $.ajax.calls.mostRecent().args[0]
        expect(options.type).toEqual('POST')
        expect(options.timeout).toBeDefined()
        expect(options.headers?.Authorization).toBeUndefined()
        expect(options.data).toEqual(model.get('credentials').toPostData())
        expect(options.url).not.toEqual(query.url())

      it 'should send an AJAX request when credentials changes', ->
        model.set({ credentials: dummyCredentials({ dummyVariableToMakeEqualsReturnFalse: true }) })
        expect($.ajax).toHaveBeenCalled()

      it 'should abort an AJAX request when a second one comes in', ->
        model.set({ credentials: dummyCredentials({ dummyVariableToMakeEqualsReturnFalse: 1 }) })
        firstDeferred = ajaxDeferred
        firstDeferred.abort = ->
        spyOn(firstDeferred, 'abort')
        model.set({ credentials: dummyCredentials({ dummyVariableToMakeEqualsReturnFalse: 2 }) })
        expect(firstDeferred.abort).toHaveBeenCalled()

      it 'should not abort an AJAX request after it is complete', ->
        model.set({ credentials: dummyCredentials({ dummyVariableToMakeEqualsReturnFalse: 1 }) })
        $.ajax.calls.mostRecent().args[0].complete()
        model.set({ credentials: dummyCredentials({ dummyVariableToMakeEqualsReturnFalse: 2 }) })
        expect($.ajax.calls.count()).toEqual(2)
        # If we're here and didn't crash, we know ajaxDeferred.abort() wasn't called.

      it 'should set status=error', ->
        query.trigger('error')
        expect(model.get('status')).toEqual('error')

      it 'should set status=fetched', ->
        query.trigger('sync')
        expect(model.get('status')).toEqual('fetched')

      it 'should set status=fetching', ->
        query.trigger('request')
        expect(model.get('status')).toEqual('fetching')
