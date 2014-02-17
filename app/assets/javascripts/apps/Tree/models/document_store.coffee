define [ 'underscore', './observable' ], (_, observable) ->
  # Canonical store of all loaded documents
  #
  # A document is a play JS object that looks like this:
  # { id: (int), description: (string), tagids: [(int), ...], nodeids: [(int), ...] }
  class DocumentStore
    observable(this)

    constructor: () ->
      @documents = {}
      @_counts = {}

    add: (document) ->
      id = "#{document.id}"
      if id of @_counts
        @_counts[id] += 1
        existing = @documents[id]
        if !_.isEqual(existing, document)
          existing[k] = v for k, v of document
      else
        @documents[id] = document
        @_counts[id] = 1
        this._notify('document-added', document)
      @documents[id]

    change: (document) ->
      this._notify('document-changed', document)

    remove: (document) ->
      id = "#{document.id}"

      @_counts[id]--
      if @_counts[id] == 0
        delete @_counts[id]
        delete @documents[id]
      this._notify('document-removed', document)

    add_doclist: (doclist, documents) ->
      this.add(documents[docid]) for docid in doclist.docids
      undefined

    remove_doclist: (doclist) ->
      this.remove(@documents[docid]) for docid in doclist.docids
      undefined

    rewrite_tag_id: (old_tagid, new_tagid) ->
      for __, document of @documents
        tagids = document.tagids
        index = tagids?.indexOf(old_tagid)
        if index? && index != -1
          tagids.splice(index, 1, new_tagid)
      undefined

    remove_tag_id: (tagid) ->
      for __, document of @documents
        tagids = document.tagids
        index = tagids?.indexOf(tagid)
        if index? && index != -1
          tagids.splice(index, 1)
          @change(document)
      undefined
