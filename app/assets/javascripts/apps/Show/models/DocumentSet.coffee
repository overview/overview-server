define [
  'underscore'
  'backbone'
  '../collections/Tags'
  '../collections/Views'
], (_, Backbone, Tags, Views) ->
  # Represents a DocumentSet from the server.
  #
  # * Provides `id`, a Number
  # * Fetches `name`, `tags`, `views` and `nDocuments` constants. (Whoever
  #   constructs a DocumentSet should call `fetch()` on it, and wait for `sync`
  #   before using `tags`, `views`, `name` and `nDocuments`.
  # * Handles a `metadataFields` attribute, an Array of String field names. Set
  #   it with `setMetadataFields()` to send a PATCH request to the server.
  #
  # Initialize it like this:
  #
  # documentSet = new DocumentSet(id: 1234)
  # documentSet.fetch()
  # documentSet.once('sync', function() { doStuff(documentSet) })
  class DocumentSet extends Backbone.Model
    defaults:
      metadataFields: []

    url: -> "#{@_urlPrefix()}.json"

    _urlPrefix: -> "/documentsets/#{@id}"

    initialize: ->
      @tags = new Tags([], url: "#{@_urlPrefix()}/tags")
      @views = new Views([], url: "#{@_urlPrefix()}/views")
      @nDocuments = 0
      # Now the user should call fetch()
      
    parse: (data, options) ->
      @tags.reset(data.tags)
      @views.reset(data.views)
      @nDocuments = data.nDocuments
      @name = data.name

      metadataFields: @_parseMetadataFields(data.metadataSchema)

    # In: a JSON metadataSchema; out: an Array of String metadataFields.
    #
    # Eventually we'll want something more complex, if we support multiple data
    # types. Right now, everything's a String.
    _parseMetadataFields: (schemaJson) ->
      throw new Error("Wrong schema version, must be 1") if schemaJson.version != 1
      _.pluck(schemaJson.fields, 'name')

    # Sets metadataFields and persists the change to the server.
    #
    # Normally, this would just be
    # `.save({ metadataFields: [ 'foo' ] }, patch: true)`. But in this case,
    # `metadataFields` is a derived value: we need to send the server a
    # `metadataSchema` JSON.
    patchMetadataFields: (fields) ->
      return if _.isEqual(fields, @get('metadataFields'))

      schema =
        version: 1
        fields: fields.map((name) -> name: name, type: 'String')
      @set(metadataFields: fields)
      @sync('patch', @, attrs: { metadataSchema: schema })
