define [ 'backbone' ], (Backbone) ->
  class SearchResult extends Backbone.Model
    defaults:
      query: ''
      state: 'InProgress'

    idAttribute: 'query'

    isNew: -> !@get('id')?
