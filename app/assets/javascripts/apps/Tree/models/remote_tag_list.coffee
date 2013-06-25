define [], ->
  class RemoteTagList
    constructor: (@cache) ->
      @tag_store = @cache.tag_store
      @document_store = @cache.document_store
      @tags = @tag_store.tags

    add_tag_to_selection: (tag, selection) ->
      documents = this._selection_to_documents(selection)
      return if !documents?

      this._maybe_add_tagid_to_document(tag.id, document) for document in documents

      selection_post_data = this._selection_to_post_data(selection)
      @cache.transaction_queue.queue =>
        deferred = @cache.server.post('tag_add', selection_post_data, { path_argument: tag.id })
          .done(this._after_tag_add_or_remove.bind(this, tag))
          .done(=> @cache.refresh_tagcounts(tag))
        deferred

    remove_tag_from_selection: (tag, selection) ->
      documents = this._selection_to_documents(selection)
      return if !documents?

      this._maybe_remove_tagid_from_document(tag.id, document) for document in documents

      selection_post_data = this._selection_to_post_data(selection)
      @cache.transaction_queue.queue =>
        deferred = @cache.server.post('tag_remove', selection_post_data, { path_argument: tag.id })
          .done(this._after_tag_add_or_remove.bind(this, tag))
          .done(=> @cache.refresh_tagcounts(tag))
        deferred

    _after_tag_add_or_remove: (tag, response) ->
      newSize = tag.size || 0
      if 'added' of response
        newSize += response.added
      if 'removed' of response
        newSize -= response.removed
      newTag = { size: newSize }
      @tag_store.change(tag, newTag)

    _selection_to_post_data: (selection) ->
      nodes: selection.nodes.join(',')
      documents: selection.documents.join(',')
      tags: selection.tags.join(',')
      searchResults: selection.searchResults.join(',')

    # Returns loaded docids from selection.
    #
    # If nothing is selected, returns undefined. Do not confuse this with
    # the empty-Array return value, which only means we don't have any loaded
    # docids that match the selection.
    _selection_to_documents: (selection) ->
      return undefined if selection.isEmpty()

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
