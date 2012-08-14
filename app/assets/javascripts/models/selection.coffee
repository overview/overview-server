observable = require('models/observable').observable

class Selection
  observable(this)

  constructor: () ->
    @nodes = []
    @tags = []
    @documents = []

  includes: (key, id) ->
    key += 's' if key[key.length - 1] != 's'
    this[key].indexOf(id) != -1

  update: (options) ->
    changed1 = if options.nodes?
      this._update_one('nodes', options.nodes)
    else if options.node?
      this._update_one('nodes', [options.node])

    changed2 = if options.tags?
      this._update_one('tags', options.tags)
    else if options.tag?
      this._update_one('tags', [options.tag])

    changed3 = if options.documents?
      this._update_one('documents', options.documents)
    else if options.document?
      this._update_one('documents', [options.document])

    this._notify() if changed1 || changed2 || changed3

  _update_one: (key, new_value) ->
    old_value = this[key]
    new_value ||= []

    return if _.isEqual(old_value, new_value)

    this[key] = new_value

  documents_from_caches: (document_store, on_demand_tree) ->
    node_document_ids = []
    if @nodes.length
      c = on_demand_tree.id_tree.children
      n = on_demand_tree.nodes

      # get flat list of all node IDs, with descendents
      all_node_ids = []
      next_node_ids = @nodes.slice(0)
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
      if all_node_ids.length
        arrays = (n[nodeid].doclist.docids for nodeid in all_node_ids)
        node_document_ids = _.uniq(_.union.apply({}, arrays))

    tag_document_ids = []
    if @tags.length
      arrays = []
      for tagid in @tags
        array = []
        for id, document of document_store.documents
          if document.tagids.indexOf(tagid) != -1
            array.push(document.id) # not plain id, which is a String
        arrays.push(array)
      tag_document_ids = _.uniq(_.union.apply({}, arrays))

    document_ids = if node_document_ids.length && tag_document_ids.length
      node_document_ids.sort()
      tag_document_ids.sort()
      _.intersection(node_document_ids, tag_document_ids)
    else if node_document_ids.length
      node_document_ids
    else
      tag_document_ids

    documents = (document_store.documents[id] for id in document_ids)

    _.sortBy(documents, (d) -> d.title)

exports = require.make_export_object('models/selection')
exports.Selection = Selection
