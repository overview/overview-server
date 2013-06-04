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
  #
  # Returned properties:
  #
  # * view: a Backbone.View
  (cache, state) ->
    proxy = new SearchResultStoreProxy(cache.search_result_store)
    collection = proxy.collection
    view = new InlineSearchResultListView({
      collection: proxy.collection
      searchResultIdToModel: (id) -> proxy.map(id)
      state: state
    })

    view.on 'search-result-clicked', (searchResult) ->
      searchResult = proxy.unmap(searchResult)
      if searchResult.id
        log('selected search result', "#{search_result_to_short_string(searchResult)}")
      else
        log('clicked unfinished search result', "#{search_result_to_short_string(searchResult)}")

    view.on 'create-submitted', (query) ->
      searchResult = { query: query }
      log('created search', "#{search_result_to_short_string(searchResult)}")
      searchResult = cache.search_result_store.add(searchResult)
      cache.search_result_api.create(searchResult)

    { view: view }
