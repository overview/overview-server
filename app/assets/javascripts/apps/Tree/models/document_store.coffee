define [ 'underscore', './observable' ], (_, observable) ->
  # Canonical store of all loaded documents
  #
  # A document is a plain JS Object that looks like this:
  # { id: (int), description: (string), tagids: [(int), ...], nodeids: [(int), ...] }
  class DocumentStore
    observable(this)

    constructor: -> @documents = {}

    reset: (documents) ->
      @documents = {}
      (@documents[d.id] = d) for d in documents
      undefined

    change: (document) ->
      this._notify('document-changed', document)

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
