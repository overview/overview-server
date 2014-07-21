define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class ApiToken extends Backbone.Model
    idAttribute: 'token'

    defaults:
      token: null
      createdAt: new Date()
      description: ""

    parse: (json) ->
      _.extend({}, json, {
        createdAt: new Date(json.createdAt)
      })
