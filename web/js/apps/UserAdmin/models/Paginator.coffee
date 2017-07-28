define [ 'backbone' ], (Backbone) ->
  class Paginator extends Backbone.Model
    defaults:
      page: 1
