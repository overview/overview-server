Deferred = jQuery.Deferred

class DocumentList
  constructor: (@store, @selection, @resolver) ->
    @documents = []
    @deferreds = {}
    @n = undefined

  get_placeholder_documents: () ->
    ids_so_far = (document.id? && document.id || document for document in (@selection.documents || []))
    excludes_something = @selection.documents?.length

    for key in [ 'tags', 'nodes' ]
      store = @store[key]
      for object in @selection[key]
        object = store.get(object) if !object.id?

        return [] if !object?.doclist?

        doclist = object.doclist

        if !excludes_something
          ids_so_far = doclist.docids
          excludes_something = true
        else
          new_ids_so_far = []
          for docid in (doclist.docids || [])
            if ids_so_far.indexOf(docid) >= 0
              new_ids_so_far.push(docid)
          ids_so_far = new_ids_so_far

    (@store.documents.get(docid) for docid in ids_so_far)

  # Returns a Deferred which, when resolved, will be a slice of this.documents
  slice: (start=0, end=20) ->
    deferred_key = "#{start}..#{end}"

    return @deferreds[deferred_key] if @deferreds[deferred_key]?

    deferred = if end < @documents.length
      new Deferred().resolve(@documents.slice(start, end))
    else
      @resolver.get_selection_documents_slice(@selection, start, end)

    deferred.done (documents, n) =>
      for document, i in documents
        @documents[start+i] = document
      @n = n

    @deferreds[deferred_key] = deferred.pipe((documents, n) -> documents)


exports = require.make_export_object('models/document_list')
exports.DocumentList = DocumentList
