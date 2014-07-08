define [
  'backbone'
  '../models/Viz'
], (backbone, Viz) ->
  PollDelay = 1000 # ms

  class Vizs extends Backbone.Collection
    model: Viz

    comparator: (a, b) ->
      isJob = (x) -> x.attributes.type == 'job' && 1 || 0
      isError = (x) -> x.attributes.type == 'error' && 1 || 0

      isJob(a) - isJob(b) \
        || isError(a) - isError(b) \
        || a.attributes.createdAt - b.attributes.createdAt

    initialize: (models, options) ->
      throw 'Must pass options.url, a String like "/documentsets/3/searches"' if !options?.url
      @url = options.url

    # Poll every once in a while until all jobs are complete.
    #
    # Every SearchResult needs an ID for this method to work. If a SearchResult
    # is missing an ID, it will be replaced after a fetch.
    pollUntilStable: ->
      @_pollTimeout ||= window.setTimeout((=> @_doPoll()), PollDelay)

    _doPoll: ->
      if @findWhere(type: 'job')
        onComplete = =>
          @_pollTimeout = null
          @pollUntilStable()

        @fetch() # FIXME should have remove: false, but need stable IDs
          .then(onComplete, onComplete)
      else
        @_pollTimeout = null
