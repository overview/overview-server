import { isEqual } from 'underscore'
import Views from '../collections/Views'
import Backbone from 'backbone'
import Tags from '../collections/Tags'

# In: a JSON metadataSchema; out: an Array of String metadataFields.
#
# Eventually we'll want something more complex, if we support multiple data
# types. Right now, everything's a String.
parseMetadataFields = (schemaJson) =>
  throw new Error("Wrong schema version, must be 1") if schemaJson.version != 1
  schemaJson.fields.map((f) => f.name)

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
export default class DocumentSet extends Backbone.Model
  defaults:
    metadataFields: []
    metadataSchema: { version: 1, fields: [] }

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

    metadataSchema: data.metadataSchema
    metadataFields: parseMetadataFields(data.metadataSchema)

  # Sets metadataFields and persists the change to the server.
  #
  # Normally, this would just be
  # `.save({ metadataFields: [ 'foo' ] }, patch: true)`. But in this case,
  # `metadataFields` is a derived value: we need to send the server a
  # `metadataSchema` JSON.
  patchMetadataFields: (fields, options={}) ->
    return if isEqual(fields, @get('metadataFields'))

    schema =
      version: 1
      fields: fields.map((name) -> name: name, type: 'String')
    @set(metadataSchema: schema, metadataFields: fields)
    @sync('patch', @, Object.assign(attrs: { metadataSchema: schema }, options))

  # Sets metadataSchema and persists the change to the server.
  #
  # This recalculates `metadataFields` in the process.
  patchMetadataSchema: (schema, options={}) ->
    return if isEqual(schema, @get('metadataSchema'))

    fields = schema.fields.map((field) -> field.name)
    @set(metadataSchema: schema, metadataFields: fields)
    @sync('patch', @, Object.assign(attrs: { metadataSchema: schema }, options))
