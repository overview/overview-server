observable = require('models/observable').observable

class RemoteTagList
  observable(this)

  constructor: (@cache, @transaction_queue, @server) ->
    @tag_store = @cache.tag_store
    @document_store = @cache.document_store
    @tags = @tag_store.tags

    @tag_store.observe('tag-added', (v) => this._notify('tag-added', v))
    @tag_store.observe('tag-removed', (v) => this._notify('tag-removed', v))
    @tag_store.observe('tag-changed', (v) => this._notify('tag-changed', v))

  create_tag: (name) ->
    @tag_store.create_tag(name)

  add_tag_to_selection: (tag, selection) ->
    documents = this._selection_to_documents(selection)
    return if !documents?

    this._maybe_add_tagid_to_document(tag.id, document) for document in documents

    @cache.on_demand_tree.id_tree.edit =>
      if tag.doclist?
        @document_store.remove_doclist(tag.doclist)
        @tag_store.change(tag, { doclist: undefined })

      if selection.tags.length == 0 && selection.documents.length == 0 && selection.nodes.length > 0
        @cache.on_demand_tree.add_tag_to_node(nodeid, tag) for nodeid in selection.nodes

    selection_post_data = this._selection_to_post_data(selection)
    @transaction_queue.queue =>
      deferred = @server.post('tag_add', selection_post_data, { path_argument: tag.name })
      deferred.done(this._after_tag_add_or_remove.bind(this, tag))

  remove_tag_from_selection: (tag, selection) ->
    documents = this._selection_to_documents(selection)
    return if !documents?

    this._maybe_remove_tagid_from_document(tag.id, document) for document in documents

    @cache.on_demand_tree.id_tree.edit =>
      if tag.doclist?
        @document_store.remove_doclist(tag.doclist)
        @tag_store.change(tag, { doclist: undefined })

      if selection.tags.length == 0 && selection.documents.length == 0 && selection.nodes.length > 0
        @cache.on_demand_tree.remove_tag_from_node(nodeid, tag) for nodeid in selection.nodes

    selection_post_data = this._selection_to_post_data(selection)
    @transaction_queue.queue =>
      deferred = @server.post('tag_remove', selection_post_data, { path_argument: tag.name })
      deferred.done(this._after_tag_add_or_remove.bind(this, tag))

  _after_tag_add_or_remove: (tag, obj) ->
    old_tagid = tag.id

    if obj.tag?.doclist? && obj.documents?
      documents = {}
      documents[doc.id] = doc for doc in obj.documents
      @document_store.add_doclist(obj.tag.doclist, documents)
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
  _selection_to_documents: (selection) ->
    if !selection.nodes.length && !selection.tags.length && !selection.documents.length
      return undefined

    selection.documents_from_cache(@cache)

  _maybe_add_tagid_to_document: (tagid, document) ->
    tagids = document.tagids
    if tagid not in tagids
      tagids.push(tagid)
      @document_store.change(document)

  _maybe_remove_tagid_from_document: (tagid, document) ->
    tagids = document.tagids
    index = tagids.indexOf(tagid)
    if index >= 0
      tagids.splice(index, 1)
      @document_store.change(document)

  find_tag_by_name: (name) ->
    @tag_store.find_tag_by_name(name)

exports = require.make_export_object('models/remote_tag_list')
exports.RemoteTagList = RemoteTagList
