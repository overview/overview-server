define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  # A Document from the server.
  #
  # A Document has a title and description.
  #
  # It can also contain tags, which are stored in a `tagIds` and a `tagCids`
  # attributes. These are two layers of caches, required because Document
  # doesn't know about all Tags. `tagIds` stores what the server sent, as an
  # Object mapping tagId -> null. `tagCids` stores overrides, in an Object
  # mapping tagCid -> (true|false).
  #
  # Document is mostly a client-side cache of what we think the server is
  # storing. One big exception is tagging. When tagging, we must pre-empt the
  # server: the server won't tell us when a document changes.
  #
  # Events:
  #   document-tagged(document, tag)
  #   document-untagged(document, tag)
  class Document extends Backbone.Model
    defaults:
      title: ''
      description: ''
      pageNumber: null
      url: null

    parse: (json) ->
      tagIds = {}
      tagIds[tagId] = true for tagId in (json.tagids || [])

      id: json.id
      title: json.title
      description: json.description
      pageNumber: json.page_number || null
      url: json.url || null
      tagids: json.tagids || []
      nodeids: json.nodeids || []

    initialize: (attributes, options) ->
      super(attributes, options)

      @tagIds = {}
      @tagIds[tagId] = null for tagId in @get('tagids')
      @tagCids = {}

    hasTag: (tag) ->
      if tag.cid of @tagCids
        @tagCids[tag.cid]
      else if tag.id?
        tag.id of @tagIds
      else
        false

    # Ensures this.hasTag(tag) == true.
    #
    # This triggers `document-tagged` after adding the tag. If the tag was
    # already there, this is a no-op.
    tagLocal: (tag) ->
      return if @hasTag(tag)

      @tagCids[tag.cid] = true
      @trigger('document-tagged', @, tag)

    # Ensures this.hasTag(tag) == false.
    #
    # This triggers `document-untagged` after removing the tag. If the tag
    # wasn't there, this is a no-op.
    untagLocal: (tag) ->
      return if !@hasTag(tag)

      @tagCids[tag.cid] = false
      @trigger('document-untagged', @, tag)

    # Tags the document, locally and on the server.
    #
    # Triggers `document-tagged` if the tag is new.
    tag: (tag) ->
      return if @hasTag(tag)
      @tagLocal(tag)
      tag.addToDocumentsOnServer(documents: String(@id))

    # Untags the document, locally and on the server.
    #
    # Triggers `document-untagged` if the tag was there before.
    untag: (tag) ->
      return if !@hasTag(tag)
      @untagLocal(tag)
      tag.removeFromDocumentsOnServer(documents: String(@id))
