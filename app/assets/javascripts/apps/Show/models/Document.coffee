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
  #   document-renamed(document, newTitle)
  class Document extends Backbone.Model
    defaults:
      documentSetId: null
      title: ''
      description: ''
      snippet: ''
      pageNumber: null
      url: null
      metadata: null # We normally *don't* load metadata -- hence null

    url: -> "/documentsets/#{@get('documentSetId')}/documents/#{@id}"

    parse: (json) ->
      return null if !json? # a PATCH response should be empty
      tagIds = {}
      tagIds[tagId] = true for tagId in (json.tagids || [])

      id: json.id
      documentSetId: json.documentSetId
      title: json.title
      description: json.description
      pageNumber: json.page_number || null
      url: json.url || null
      snippet: json.snippet || null
      tagids: json.tagids || []
      nodeids: json.nodeids || []
      thumbnailUrl: json.thumbnailUrl || null

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
    tagLocal: (tag, options) ->
      return if @hasTag(tag)

      @tagCids[tag.cid] = true
      @trigger('document-tagged', @, tag, options)

    # Ensures this.hasTag(tag) == false.
    #
    # This triggers `document-untagged` after removing the tag. If the tag
    # wasn't there, this is a no-op.
    untagLocal: (tag, options) ->
      return if !@hasTag(tag)

      @tagCids[tag.cid] = false
      @trigger('document-untagged', @, tag, options)

    # Tags the document, locally and on the server.
    #
    # Triggers `document-tagged` if the tag is new.
    tag: (tag, options) ->
      return if @hasTag(tag)
      @tagLocal(tag, options)
      tag.addToDocumentsOnServer(documents: String(@id))

    # Untags the document, locally and on the server.
    #
    # Triggers `document-untagged` if the tag was there before.
    untag: (tag, options) ->
      return if !@hasTag(tag)
      @untagLocal(tag, options)
      tag.removeFromDocumentsOnServer(documents: String(@id))

    # Renames the document, locally and on the server.
    #
    # Triggers `document-renamed`, whether or not the name actually changed.
    rename: (newTitle, options) ->
      Backbone.ajax
        type: 'PATCH'
        url: _.result(@, 'url')
        contentType: 'application/json'
        data: JSON.stringify(title: newTitle)
        debugInfo: 'Document.rename'
        success: =>
          @set('title', newTitle)
          @trigger('document-renamed', @, newTitle, options)
