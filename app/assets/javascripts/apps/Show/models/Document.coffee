define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  # A Document from the server.
  #
  # A Document has a title and description. It can also contain tags, which are
  # stored in a 'tagIds' attribute, an Object mapping tag ID to `null`. These
  # attributes come from the server.
  #
  # Document is mostly a client-side cache of what we think the server is
  # storing. One big exception is tagging. When tagging, we must pre-empt the
  # server: for one thing, that's more usable; and for another, the server
  # won't tell us when a document changes.
  #
  # To avoid races, we track the changes the user has made between when the
  # Document was loaded and now. This amounts to two sets of operations:
  # "tag/untag a Document" and "tag/untag a _list_ of Documents". We store
  # the former in `document.attributes.tagCids` and the latter in
  # `document.collection.tagCids`. (We use CIDs, not IDs, because new tags
  # don't have IDs right away.)
  class Document extends Backbone.Model
    defaults:
      type: 'document'
      title: ''
      description: ''
      pageNumber: null
      url: null

    hasTag: (tag) ->
      inDocument = @attributes.tagCids?[tag.cid]
      inCollection = @collection?.hasTag(tag)

      if inDocument?
        inDocument
      else if inCollection?
        inCollection
      else
        tag.id? && tag.id of @attributes.tagIds

    # Override our cached value: declare whether tag is tagged or not.
    setTagged: (tag, isTagged) ->
      cids = _.extend({}, @get('tagCids'))
      cids[tag.cid] = isTagged
      @set(tagCids: cids)

    # Override our cached value: declare that this document has tag
    tag: (tag) -> @setTagged(tag, true)

    # Override our cached value: declare that this document does not have tag
    untag: (tag) -> @setTagged(tag, false)

    # Undo our cached value override: declare that tagIds is all we need
    unsetTag: (tag) ->
      return if 'tagCids' not of @attributes || tag.cid not of @attributes.tagCids # optimize the common case
      @setTagged(tag, null)
