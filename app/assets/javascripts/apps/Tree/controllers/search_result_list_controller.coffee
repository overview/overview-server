define [
  '../models/DocumentListParams'
  '../collections/SearchResultStoreProxy'
  '../views/InlineSearchResultList'
  './logger'
  'i18n'
], (DocumentListParams, SearchResultStoreProxy, InlineSearchResultListView, Logger, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.SearchResultList')
  log = Logger.for_component('search_result_list')

  search_result_to_short_string = (search_result) ->
    "#{search_result.id} (#{search_result.query})"

  searchResultToTagName = (searchResultModel) ->
    t('tag_name', searchResultModel.get('query'))

  # SearchResult controller
  #
  # Arguments:
  #
  # * cache: a Cache (for creating search results, and for its search_result_store)
  # * state: a State (for reading and manipulating the doclist)
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
      searchResultIdToModel: (id) -> if proxy.canMap(id) then proxy.map(id) else undefined
      canCreateTagFromSearchResult: (searchResultModel) ->
        searchResultModel &&
          !cache.tag_store.find_by_name(searchResultToTagName(searchResultModel))?
      state: state
      el: el
    })

    collection.on 'change:state', (searchResult) =>
      if searchResult.get('state') == 'Complete'
        searchResult = proxy.unmap(searchResult)
        cache.refreshSearchResultCounts(searchResult)

    view.on 'search-result-clicked', (searchResult) ->
      searchResult = proxy.unmap(searchResult)
      log('clicked search result', "#{search_result_to_short_string(searchResult)}")
      state.setDocumentListParams(DocumentListParams.bySearchResultId(searchResult.id))

    view.on 'create-tag-clicked', (searchResultModel) ->
      tag = { name: searchResultToTagName(searchResultModel) }
      log('created tag', tag.name)
      tag = cache.add_tag(tag)
      cache.create_tag(tag)
      cache.addTagToSelection(tag, DocumentListParams.bySearchResultId(searchResultModel.id).toApiParams())
        .done ->
          cache.refresh_tagcounts(tag)
          # This shouldn't be done on "done": it should be done right away.
          # But that leads to a crash, as our Backbone proxies execute out
          # of order. Make TagStore and SearchResultStore plain
          # Backbone.Collections, then un-indent this.
          state.setDocumentListParams(DocumentListParams.byTagId(tag.id))

    view.on 'create-submitted', (query) ->
      searchResult = { query: query }
      log('created search', "#{search_result_to_short_string(searchResult)}")
      searchResult = cache.search_result_store.addAndPoll(searchResult)
      cache.search_result_api.create(searchResult)
      state.setDocumentListParams(DocumentListParams.bySearchResultId(searchResult.id))

    { view: view }
