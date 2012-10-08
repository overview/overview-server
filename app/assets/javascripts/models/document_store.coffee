observable = require('models/observable').observable

# Canonical store of all loaded documents
#
# A document is a play JS object that looks like this:
# { id: (int), title: (string), tagids: [(int), ...], nodeids: [(int), ...] }
class DocumentStore
  observable(this)

  constructor: () ->
    @documents = {}
    @_counts = {}

  add: (document) ->
    documentid = document.id
    if @_counts[documentid]?
      @_counts[documentid] += 1
    else
      @documents[document.id] = document
      @_counts[documentid] = 1
    this._notify('document-added', document)

  change: (document) ->
    this._notify('document-changed', document)

  remove: (document) ->
    documentid = document.id

    @_counts[documentid]--
    if @_counts[documentid] == 0
      delete @_counts[documentid]
      delete @documents[documentid]
    this._notify('document-removed', document)

  add_doclist: (doclist, documents) ->
    this.add(documents[docid]) for docid in doclist.docids
    undefined

  remove_doclist: (doclist) ->
    this.remove(@documents[docid]) for docid in doclist.docids
    undefined

  rewrite_tag_id: (old_tagid, new_tagid) ->
    for _, document of @documents
      tagids = document.tagids
      index = tagids?.indexOf(old_tagid)
      if index? && index != -1
        tagids.splice(index, 1, new_tagid)
    undefined

  remove_tag_id: (tagid) ->
    for _, document of @documents
      tagids = document.tagids
      index = tagids?.indexOf(tagid)
      if index? && index != -1
        tagids.splice(index, 1)
    undefined

exports = require.make_export_object('models/document_store')
exports.DocumentStore = DocumentStore
