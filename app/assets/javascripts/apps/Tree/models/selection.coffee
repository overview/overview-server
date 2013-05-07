define [ 'underscore' ], (_) ->
  KEYS = [ 'nodes', 'tags', 'documents' ]

  # A Selection is an intersection of unions, describing Documents:
  #
  # * @nodes: A list of Node IDs
  # * @tags: A list of Tag IDs
  # * @documents: A list of Document IDs
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

    pick: (keys...) ->
      obj = {}
      (obj[k] = this[k]) for k in keys
      new Selection(obj)

    _node_ids_to_document_ids: (cache, node_ids) ->
      arrays = []
      for nodeid in node_ids
        array = []
        for id, document of cache.document_store.documents
          if document.nodeids.indexOf(nodeid) != -1
            array.push(document.id) # not plain id, which is a String
        arrays.push(array)
      _.uniq(_.union.apply({}, arrays))

    _tag_ids_to_document_ids: (cache, tag_ids) ->
      arrays = []
      for tagid in tag_ids
        array = []
        for id, document of cache.document_store.documents
          if document.tagids.indexOf(tagid) != -1
            array.push(document.id) # not plain id, which is a String
        arrays.push(array)
      _.uniq(_.union.apply({}, arrays))

    documents_from_cache: (cache) ->
      arrays = []

      if @nodes.length
        arrays.push(this._node_ids_to_document_ids(cache, @nodes))

      if @tags.length
        arrays.push(this._tag_ids_to_document_ids(cache, @tags))

      if @documents.length
        arrays.push(@documents)

      documents = if arrays.length >= 1
        docids = _.intersection.apply(null, arrays)
        cache.document_store.documents[docid] for docid in docids
      else
        _.values(cache.document_store.documents)

      _.sortBy(documents, (d) -> d.description)

    to_string: () ->
      "documents:#{@documents.length},nodes:#{@nodes.length},tags:#{@tags.length}"

    allows_correct_tagcount_adjustments: () ->
      !!(@nodes.length && !@tags.length && !@documents.length)
