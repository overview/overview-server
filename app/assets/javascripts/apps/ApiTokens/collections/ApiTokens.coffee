define [
  'backbone'
  '../models/ApiToken'
], (Backbone, ApiToken) ->
  class ApiTokens extends Backbone.Collection
    model: ApiToken

    initialize: (items, options) ->
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId
      @documentSetId = options.documentSetId

    url: -> "/documentsets/#{@documentSetId}/api-tokens"
