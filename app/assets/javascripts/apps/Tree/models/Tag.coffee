define [
  'underscore'
  'backbone'
  './color_table'
], (_, Backbone, ColorTable) ->
  class Tag extends Backbone.Model
    defaults:
      name: ''
      color: null

    constructor: (attributes, options) ->
      attributes = _.extend({}, attributes)
      if !attributes.color
        attributes.color = new ColorTable().get(attributes.name)

      super(attributes, options)
