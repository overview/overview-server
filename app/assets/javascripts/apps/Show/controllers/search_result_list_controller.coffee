define [
  '../models/DocumentListParams'
  '../views/SearchView'
  'i18n'
], (DocumentListParams, SearchView, i18n) ->
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

    view = new SearchView
      state: state
      el: el

    view.on 'search', (q) ->
      if q.length > 0
        state.resetDocumentListParams().byQ(q)
      else
        state.resetDocumentListParams().all()

    { view: view }
