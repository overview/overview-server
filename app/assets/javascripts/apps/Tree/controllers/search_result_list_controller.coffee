define [
  '../models/selection'
  '../collections/SearchResultStoreProxy'
  '../views/InlineSearchResultList'
  './logger'
], (Selection, SearchResultStoreProxy, InlineSearchResultListView, Logger) ->
  log = Logger.for_component('search_result_list')

  search_result_to_short_string = (search_result) ->
    "#{search_result.id} (#{search_result.query})"

  # SearchResult controller
  #
  # Arguments:
  #
  # * cache: a Cache (for creating search results, and for its search_result_store)
  # * state: a State (for reading and manipulating the selection)
  # * el: an HTMLElement (optional)
  #
  # Returned properties:
  #
  # * view: a Backbone.View
  (options) ->
    cache = options.cache
    state = options.state
    el = options.el

    proxy = new SearchResultStoreProxy(cache.search_result_store)
    collection = proxy.collection
    view = new InlineSearchResultListView({
      collection: proxy.collection
      searchResultIdToModel: (id) -> proxy.map(id)
      state: state
      el: el
    })

    collection.on 'change:state', (searchResult) =>
      if searchResult.get('state') == 'Complete'
        searchResult = proxy.unmap(searchResult)
        cache.refreshSearchResultCounts(searchResult)

    view.on 'search-result-clicked', (searchResult) ->
      searchResult = proxy.unmap(searchResult)
      if searchResult.id
        state.set('selection', new Selection({ searchResults: [ searchResult.id ] }))
      else
        log('clicked unfinished search result', "#{search_result_to_short_string(searchResult)}")

    view.on 'create-submitted', (query) ->
      searchResult = { query: query }
      log('created search', "#{search_result_to_short_string(searchResult)}")
      searchResult = cache.search_result_store.addAndPoll(searchResult)
      cache.search_result_api.create(searchResult)

    { view: view }
