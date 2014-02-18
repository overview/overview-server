define [ 'backbone' ], (Backbone) ->
  class User extends Backbone.Model
    idAttribute: 'email'

    getDate: (key) ->
      value = @get(key)
      value? && new Date(Date.parse(value)) || null

    isNew: -> !@has('id')
    urlRoot: -> '/admin/users'
