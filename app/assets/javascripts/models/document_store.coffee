class DocumentStore
  constructor: () ->
    @documents = {}
    @_counts = {}

  add: (document) ->
    docid = document.id
    if @_counts[docid]?
      @_counts[docid]++
    else
      @documents[docid] = document
      @_counts[docid] = 1

  remove: (document) ->
    docid = document.id

    @_counts[docid]--
    if @_counts[docid] == 0
      delete @_counts[docid]
      delete @documents[docid]

  get: (docid) ->
    @documents[docid]

exports = require.make_export_object('models/document_store')
exports.DocumentStore = DocumentStore
