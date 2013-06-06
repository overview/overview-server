define [ 'jquery', 'underscore', './TagLikeStore' ], ($, _, TagLikeStore) ->
  # A SearchResultStore is mostly a TagLikeStore, for SearchResults.
  #
  # Unlike a TagLikeStore, the SearchResultStore is self-refreshing. Callers
  # may use addAndPoll() instead of plain add(); the store will poll the server
  # (at the pollUrl defined in the constructor) until the searchResult exists
  # on the server and its state is 'Complete' or 'Error'
  #
  # This is subject to race conditions:
  #
  # * If the server creates and destroys the searchResult in between two polls,
  #   the SearchResultStore will continue polling indefinitely.
  # * If the user refreshes the page before the searchResult appears on the
  #   server, the SearchResultStore on the refreshed page will not know it
  #   needs to poll.
  #
  # The SearchResultStore polls for all SearchResults at once.
  class SearchResultStore extends TagLikeStore
    constructor: (@pollUrl) ->
      super('query')
      @search_results = @objects
      @_pollForKeys = {}

    parse: (search_result) ->
      search_result

    find_by_query: (query) ->
      @find_by_key(query)

    addAndPoll: (attributes) ->
      searchResult = @add(attributes)
      @_pollForKey(searchResult.query)
      searchResult

    _pollForKey: (key) ->
      @_pollForKeys[key] = true
      @_maybeSchedulePoll()

    _maybeSchedulePoll: ->
      if !_.isEmpty(@_pollForKeys)
        @_pollTimeout ||= window.setTimeout((=> @_poll()), 500)

    _poll: ->
      $.ajax({ type: 'GET', url: @pollUrl })
        .done((data) => @_onPollDone(data))
        .fail((xhr, textStatus) => @_onPollError(xhr, textStatus))
        .always(=> @_onPollComplete())

    _onPollDone: (data) ->
      keyToObject = {}
      (keyToObject[object.query] = object) for object in @objects

      for serverSearchResult in data
        key = serverSearchResult.query
        object = keyToObject[key]
        if object
          # after iterating, keyToObject will be empty iff the server has
          # provided all keys that exist in the store
          delete keyToObject[key]
          if !_.isEqual(serverSearchResult, object)
            @change(object, serverSearchResult)
        else
          @add(serverSearchResult)

        if object.state == 'Complete'
          delete @_pollForKeys[key]

      # Delete from our store anything that's no longer on the server
      for key, oldObject of keyToObject
        if key not of @_pollForKeys
          @remove(oldObject)

    _onPollError: (xhr, textStatus) ->
      #console.log('SearchResultStore poll error', arguments)

    _onPollComplete: ->
      @_pollTimeout = undefined
      @_maybeSchedulePoll()

    # Destroy the store, stopping any polling that's under way.
    destroy: ->
      window.clearTimeout(@_pollTimeout)
