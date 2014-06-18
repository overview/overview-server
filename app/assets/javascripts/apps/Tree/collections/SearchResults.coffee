define [
  'backbone'
  '../models/SearchResult'
], (Backbone, SearchResult) ->
  PollDelay = 1000 # ms

  class SearchResults extends Backbone.Collection
    model: SearchResult
    comparator: (a, b) -> b.attributes.createdAt - a.attributes.createdAt

    initialize: (models, options) ->
      throw 'Must pass options.url, a String like "/documentsets/3/searches"' if !options?.url
      @url = options.url

    # Poll every once in a while until there are no InProgress SearchResults.
    #
    # Every SearchResult needs an ID for this method to work. If a SearchResult
    # is missing an ID, it will be replaced after a fetch.
    pollUntilStable: ->
      @_pollTimeout ||= window.setTimeout((=> @_doPoll()), PollDelay)

    _doPoll: ->
      if @findWhere(state: 'InProgress')
        @fetch(remove: false)
          .fail((xhr) -> console.log('Search refresh failed. Will retry soon.', xhr))
          .always =>
            @_pollTimeout = null
            @pollUntilStable()
      else
        @_pollTimeout = null
