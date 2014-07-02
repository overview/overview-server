define [
  'backbone'
  '../models/Document'
], (Backbone, Document) ->
  # A sorted list of Document objects.
  #
  # Instances are created and managed by a DocumentList. The DocumentList alone
  # knows the total length of the list from the server's point of view. This
  # Backbone Collection merely holds the Document objects (and their
  # placeholders) that have been fetched.
  #
  # A complete list will look like this:
  #
  #   * { type: document, id: 1 }
  #   * { type: document, id: 2 }
  #   * { type: document, id: 3 }
  #
  # A list can also have a "loading" placeholder at the end:
  #
  #   * { type: document, id: 1 }
  #   * { type: document, id: 2 }
  #   * { type: loading }
  #
  # The entire collection can be tagged, client-side only. Do this instead of
  # tagging all members, because in this case you also want as-yet-unloaded
  # Document objects to have the tags, client-side, even if the server doesn't
  # know about every tag yet.
  class Documents extends Backbone.Collection
    model: Document

    parse: (jsonList) ->
      parseJsonTagIds = (tagids) ->
        ret = {}
        (ret[k] = null) for k in tagids
        ret

      for json in jsonList
        id: json.id
        type: json.type || 'document'
        title: json.title
        description: json.description
        pageNumber: json.page_number || null
        tagIds: parseJsonTagIds(json.tagids || [])
        nodeids: json.nodeids || []

    hasTag: (tag) -> @tagCids?[tag.cid]

    # Declare whether tag is tagged or not.
    setTagged: (tag, isTagged) ->
      model.unsetTag(tag) for model in @models

      @tagCids ||= {}
      @tagCids[tag.cid] = isTagged

    # Declare all documents have this tag
    tag: (tag) ->
      @setTagged(tag, true)
      @trigger('tag', tag)

    # Declare no documents have this tag
    untag: (tag) ->
      @setTagged(tag, false)
      @trigger('untag', tag)
