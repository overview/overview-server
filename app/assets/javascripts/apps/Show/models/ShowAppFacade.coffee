define [
], () ->
  class ShowAppFacade
    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.tags, a Tags' if !options.tags
      throw 'Must pass options.searchResults, a SearchResults' if !options.searchResults

      @state = options.state
      @tags = options.tags
      @searchResults = options.searchResults

    resetDocumentListParams: -> @state.resetDocumentListParams()
    getTag: (cid) -> @tags.get(cid)
    getSearchResult: (cid) -> @searchResults.get(cid)
