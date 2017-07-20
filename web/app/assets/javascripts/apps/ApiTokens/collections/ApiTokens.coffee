define [
  'backbone'
  '../models/ApiToken'
], (Backbone, ApiToken) ->
  class ApiTokens extends Backbone.Collection
    model: ApiToken

    initialize: (items, options) ->
      @documentSetId = options?.documentSetId || null

    url: ->
      if @documentSetId?
        "/documentsets/#{@documentSetId}/api-tokens"
      else
        "/api-tokens"
