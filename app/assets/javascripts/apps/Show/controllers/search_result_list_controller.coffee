define [
  '../models/DocumentListParams'
  '../views/InlineSearchResultList'
  'i18n'
], (DocumentListParams, InlineSearchResultListView, i18n) ->
  t = i18n.namespaced('views.Tree.show.SearchResultList')

  # SearchResult controller
  #
  # Arguments:
  #
  # * documentSet: a DocumentSet (for tagging, SearchResults, Tags)
  # * state: a State (for reading and manipulating the doclist)
  # * el: an HTMLElement (optional)
  #
  # Returned properties:
  #
  # * view: a Backbone.View
  (options) ->
    documentSet = options.documentSet
    searchResults = documentSet.searchResults
    tags = documentSet.tags
    state = options.state
    el = options.el

    view = new InlineSearchResultListView
      collection: searchResults
      state: state
      el: el

    view.on 'search-result-clicked', (searchResult) ->
      state.resetDocumentListParams().bySearchResult(searchResult)

    view.on 'create-submitted', (query) ->
      searchResult = searchResults.create(query: query)
      searchResults.pollUntilStable()
      state.set(oneDocumentSelected: false) # https://www.pivotaltracker.com/story/show/65130854
      state.resetDocumentListParams().bySearchResult(searchResult)

    { view: view }
