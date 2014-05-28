define [ 'backbone' ], (Backbone) ->
  # A Viz is a visualization the server can serve up.
  #
  # The id is an ID on the server; everything else is for displaying
  # in the UI.
  class Viz extends Backbone.Model
    defaults:
      title: '' # What the user calls this Viz
      creationData: [] # Viz-dependent [key,value] strings

    constructor: (attrs) ->
      attrs ?= {}
      if 'createdAt' of attrs
        attrs = _.defaults({
          createdAt: new Date(attrs.createdAt)
        }, attrs)

      super(attrs)
