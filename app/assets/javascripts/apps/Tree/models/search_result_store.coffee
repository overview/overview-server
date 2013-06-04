define [ './TagLikeStore' ], (TagLikeStore) ->
  class SearchResultStore extends TagLikeStore
    constructor: () ->
      super('query')
      @search_results = @objects

    parse: (search_result) ->
      search_result

    find_by_query: (query) ->
      @find_by_key(query)
