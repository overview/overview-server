define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  # A Viz is a visualization the server can serve up.
  #
  # The id is an ID on the server; everything else is for displaying
  # in the UI.
  class Viz extends Backbone.Model
    defaults:
      type: 'viz' # 'viz', 'job' or 'error'
      title: '' # What the user calls this Viz
      creationData: [] # Viz-dependent [key,value] strings

    constructor: (attrs) ->
      attrs ?= {}

      if 'createdAt' of attrs
        attrs = _.defaults({
          createdAt: new Date(attrs.createdAt)
        }, attrs)
      else
        attrs = _.extend({}, attrs)

      attrs.typeAndId = "#{attrs.type}-#{attrs.id}"

      for [ k, v ] in (attrs.creationData || [])
        if k == 'rootNodeId'
          attrs.rootNodeId = +v

      super(attrs)

    idAttribute: 'typeAndId'

    scopeApiParams: (params) ->
      _.extend({ nodes: String(@get('rootNodeId')) }, params)
