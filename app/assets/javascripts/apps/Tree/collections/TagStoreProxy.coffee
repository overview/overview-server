define [ './TagLikeStoreProxy' ], (TagLikeStoreProxy) ->
  # Proxies a TagStore to a Collection.
  #
  # This is a one-way proxy: TagStore values will propagate to the Tags, but
  # the reverse will not happen.
  #
  # Usage:
  #
  #   proxy = new TagStoreProxy(tagStore)
  #   collection = proxy.collection
  #   tagStore.add(...)
  #   tagStore.remove(...)
  #   tagStore.change(...)
  #   ...
  #   proxy.destroy() # stops listening
  class TagStoreProxy extends TagLikeStoreProxy
