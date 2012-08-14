Deferred = jQuery.Deferred

observable = require('models/observable').observable

# Stores a possibly-incomplete list of selected documents
#
# When you create a DocumentList (from a Store, a Selection and a
# NeedsResolver), its @documents property is empty and @n is undefined.
# Call get_placeholder_documents() to get some documents we know to exist that
# match the selection; call .slice() to get a Deferred that will (or has been)
# resolved to the Documents. When documents have been found, @documents will
# be populated with this (possibly-incomplete) list, and @n will be the total
# number of documents.
#
# DocumentList is almost immutable. In weird circumstances its @n may change,
# and its @documents will grow until it reaches @n elements and every element
# is defined. But that's it.
class DocumentList
  observable(this)

  constructor: (@selection, @resolver) ->
    @documents = []
    @deferreds = {}
    @n = undefined

  get_placeholder_documents: (document_store, on_demand_tree) ->
    @selection.documents_from_caches(document_store, on_demand_tree)

  # Returns a Deferred which, when resolved, will be a slice of this.documents
  slice: (start, end) ->
    deferred_key = "#{start}..#{end}"

    return @deferreds[deferred_key] if @deferreds[deferred_key]?

    deferred = if end < @documents.length
      new Deferred().resolve(@documents.slice(start, end))
    else
      @resolver.get_deferred('selection_documents_slice', { selection: @selection, start: start, end: end }).done((ret) =>
        for document, i in ret.documents
          @documents[start+i] = document
        @n = ret.total_items
        this._notify()
      ).pipe((ret) -> ret.documents)

    @deferreds[deferred_key] = deferred


exports = require.make_export_object('models/document_list')
exports.DocumentList = DocumentList
