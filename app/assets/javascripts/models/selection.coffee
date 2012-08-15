KEYS = [ 'nodes', 'tags', 'documents' ]

# A Selection is an intersection of unions, describing Documents:
#
# * A list of Node IDs
# * A list of Tag IDs
# * A list of Document IDs
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
    c = cache.on_demand_tree.id_tree.children
    n = cache.on_demand_tree.nodes

    # get flat list of all node IDs, with descendents
    all_node_ids = []
    next_node_ids = node_ids.slice(0)
    while next_node_ids.length
      last_node_ids = next_node_ids
      next_node_ids = []
      for nextid in last_node_ids
        continue if !n[nextid]?
        continue if all_node_ids.indexOf(nextid) >= 0
        all_node_ids.push(nextid)
        if c[nextid]?
          for childid in c[nextid]
            next_node_ids.push(childid)

    # turn that into documents
    arrays = (n[nodeid].doclist.docids for nodeid in all_node_ids)
    _.uniq(_.union.apply(null, arrays))

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

    _.sortBy(documents, (d) -> d.title)

exports = require.make_export_object('models/selection')
exports.Selection = Selection
