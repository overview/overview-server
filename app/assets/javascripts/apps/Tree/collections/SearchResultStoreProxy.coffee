define [ './TagLikeStoreProxy' ], (TagLikeStoreProxy) ->
  # Proxies a SearchResultStore to a Collection.
  #
  # This is a one-way proxy: SearchResultStore values will propagate to the
  # Backbone.Models, but the reverse will not happen.
  #
  # Usage:
  #
  #   proxy = new SearchResultStoreProxy(searchResultStore)
  #   collection = proxy.collection
  #   searchResultStore.add(...)
  #   searchResultStore.remove(...)
  #   searchResultStore.change(...)
  #   ...
  #   proxy.destroy() # stops listening
  class SearchResultStoreProxy extends TagLikeStoreProxy
