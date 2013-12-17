define [ 'underscore' ], (_) ->
  KEYS = [ 'nodes', 'tags', 'documents', 'searchResults' ]

  # A Selection is an intersection of unions, describing Documents:
  #
  # * @nodes: A list of Node IDs
  # * @tags: A list of Tag IDs
  # * @documents: A list of Document IDs
  # * @searchResults: a list of SearchResult IDs
  #
  # A Selection is immutable.
  #
  # The documents_from_cache() method returns the documents in the passed Cache
  # which can be proven to be included in the Selection. This is a subset of the
  # documents in the Cache which are included in the Selection (the client can't
  # currently determine that).
  class Selection
    constructor: (obj = undefined) ->
      (this[k] = obj?[k]?.slice(0) || []) for k in KEYS
      undefined

    _copy_with_algorithm: (rhs, functor) ->
      obj = {}
      (obj[k] = functor(this[k], rhs[k])) for k in KEYS
      new Selection(obj)

    plus: (rhs) ->
      this._copy_with_algorithm(rhs, (lv, rv) -> _.union(lv, rv || []))

    minus: (rhs) ->
      this._copy_with_algorithm(rhs, (lv, rv) -> _.difference(lv, rv || []))

    replace: (rhs) ->
      this._copy_with_algorithm(rhs, (lv, rv) -> rv? && rv || lv)

    copy: (rhs) ->
      this._copy_with_algorithm({}, _.identity)

    equals: (rhs) ->
      _.isEqual(this, rhs)

    isEmpty: ->
      !@nonEmpty()

    nonEmpty: ->
      return true for k in KEYS when this[k].length
      false

    pick: (keys...) ->
      obj = {}
      (obj[k] = this[k]) for k in keys
      new Selection(obj)

    to_string: () ->
      "documents:#{@documents.length},nodes:#{@nodes.length},tags:#{@tags.length}"

    allows_correct_tagcount_adjustments: () ->
      !!(@nodes.length && !@tags.length && !@documents.length && !@searchResults.length)

    deprecated_documents_from_cache: (cache) ->
      nodeids = {}
      nodeids["#{nodeid}"] = null for nodeid in @nodes
      tagids = {}
      tagids["#{tagid}"] = null for tagid in @tags
      searchids = {}
      searchids["#{searchid}"] = null for searchid in @searchResults
      docids = {}
      docids["#{docid}"] = null for docid in @documents || []

      checkNodeIds = !_.isEmpty(nodeids)
      checkTagIds = !_.isEmpty(tagids)
      checkSearchResultIds = !_.isEmpty(searchids)
      checkDocumentId = !_.isEmpty(docids)

      ret = []
      for docid, document of cache.document_store.documents
        if checkDocumentId
          continue if docid not of docids

        if checkNodeIds
          found = false
          for nodeid in document.nodeids
            if "#{nodeid}" of nodeids
              found = true
              break
          continue if not found

        if checkTagIds
          found = false
          for tagid in document.tagids
            if "#{tagid}" of tagids
              found = true
              break
          continue if not found

        if checkSearchResultIds
          found = false
          for searchid in document.searchResultIds || []
            if "#{searchid}" of searchids
              found = true
              break
          continue if not found

        ret.push(document)

      ret.sort (a, b) ->
        (a.title || '').toLowerCase().localeCompare((b.title || '').toLowerCase()) ||
          (a.description || '').toLowerCase().localeCompare((b.description || '').toLowerCase()) ||
          a.id - b.id
