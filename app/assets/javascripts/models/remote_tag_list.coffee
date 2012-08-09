observable = require('models/observable').observable

class RemoteTagList
  observable(this)

  constructor: (@tag_store, @on_demand_tree, @document_store, @transaction_queue, @server) ->
    @tags = @tag_store.tags

    @tag_store.observe('tag-added', (v) => this._notify('tag-added', v))
    @tag_store.observe('tag-removed', (v) => this._notify('tag-removed', v))
    @tag_store.observe('tag-changed', (v) => this._notify('tag-changed', v))

  create_tag: (name) ->
    tag = { id: undefined, name: name, count: 0 }
    @tag_store.add(tag)

  add_tag_to_selection: (tag, selection) ->
    docids = this._selection_to_docids(selection)
    return if !docids?

    documents = (@document_store.documents[docid] for docid in docids)
    this._maybe_add_tagid_to_document(tag.id, document) for document in documents

    if tag.doclist?
      @document_store.remove_doclist(tag.doclist)
      @tag_store.change(tag, { doclist: undefined })

    selection_post_data = this._selection_to_post_data(selection)
    @transaction_queue.queue =>
      deferred = @server.post('tag_add', selection_post_data, { path_argument: tag.name })
      deferred.done(this._after_tag_add_or_remove.bind(this, tag))

  remove_tag_from_selection: (tag, selection) ->
    docids = this._selection_to_docids(selection)
    return if !docids?

    documents = (@document_store.documents[docid] for docid in docids)
    this._maybe_remove_tagid_from_document(tag.id, document) for document in documents

    if tag.doclist?
      @document_store.remove_doclist(tag.doclist)
      @tag_store.change(tag, { doclist: undefined })

    selection_post_data = this._selection_to_post_data(selection)
    @transaction_queue.queue =>
      deferred = @server.post('tag_remove', selection_post_data, { path_argument: tag.name })
      deferred.done(this._after_tag_add_or_remove.bind(this, tag))

  _after_tag_add_or_remove: (tag, obj) ->
    if obj.tag?.doclist? && obj.documents?
      @document_store.add_doclist(obj.tag.doclist, obj.documents)
    @tag_store.change(tag, obj.tag)

  _selection_to_post_data: (selection) ->
    {
      nodes: selection.nodes.join(','),
      documents: selection.documents.join(','),
      tags: selection.tags.join(','),
    }

  # Returns loaded docids from selection.
  #
  # If nothing is selected, returns undefined. Do not confuse this with
  # the empty-Array return value, which only means we don't have any loaded
  # docids that match the selection.
  _selection_to_docids: (selection) ->
    arrays = []
    if selection.nodes.length
      arrays.push(this._nodeids_to_docids(selection.nodes))
    if selection.documents.length
      arrays.push(this._docids_to_docids(selection.documents))
    if selection.tags.length
      arrays.push(this._tagids_to_docids(selection.tags))

    if arrays.length
      _.intersection.apply({}, arrays)
    else
      undefined

  _nodeids_to_docids: (nodeids) ->
    c = @on_demand_tree.id_tree.children
    n = @on_demand_tree.nodes

    nodeids = (nodeid for nodeid in nodeids when n[nodeid]?)
    loop
      next_potential_nodeids = _.union.apply({}, c[nodeid] for nodeid in nodeids)
      next_nodeids = (nodeid for nodeid in next_potential_nodeids when n[nodeid]? && nodeid not in nodeids)

      if next_nodeids.length
        nodeids.push(nodeid) for nodeid in next_nodeids
      else
        break

    _.union.apply({}, n[nodeid].doclist.docids for nodeid in nodeids)

  _docids_to_docids: (docids) ->
    undefined

  _tagids_to_docids: (tagids) ->
    undefined

  _maybe_add_tagid_to_document: (tagid, document) ->
    tagids = document.tagids
    tagids.push(tagid) if tagid not in tagids

  _maybe_remove_tagid_from_document: (tagid, document) ->
    tagids = document.tagids
    index = tagids.indexOf(tagid)
    tagids.splice(index, 1) if index >= 0

exports = require.make_export_object('models/remote_tag_list')
exports.RemoteTagList = RemoteTagList
