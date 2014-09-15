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
      type: 'viz' # 'tree', 'viz', 'job' or 'error'
      title: '' # What the user calls this Viz
      creationData: [] # Viz-dependent [key,value] strings

    constructor: (attrs) ->
      attrs = _.extend({}, @defaults, attrs ? {})

      if 'createdAt' of attrs
        attrs.createdAt = new Date(attrs.createdAt)

      attrs.clientId = if attrs.jobId > 0
        "job-#{attrs.jobId}"
      else
        "#{attrs.type}-#{attrs.id}"

      for [ k, v ] in (attrs.creationData || [])
        if k == 'rootNodeId'
          attrs.rootNodeId = +v

      super(attrs)

    idAttribute: 'clientId'

    scopeApiParams: (params) ->
      if (rootNodeId = @get('rootNodeId'))?
        _.extend({ nodes: String(rootNodeId) }, params)
      else
        params
