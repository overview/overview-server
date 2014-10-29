define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  # A View is an iframe served by a plugin.
  #
  # The id is an ID on the server; everything else is for displaying
  # in the UI.
  class View extends Backbone.Model
    defaults:
      type: 'view' # 'tree', 'view', 'job' or 'error'
      title: '' # What the user calls this View
      creationData: [] # View-dependent [key,value] strings

    constructor: (attrs, options) ->
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

      super(attrs, options)

    idAttribute: 'clientId'

    url: -> "#{@collection.url}/#{@attributes.id}"

    scopeApiParams: (params) ->
      if (rootNodeId = @get('rootNodeId'))?
        _.extend({ nodes: String(rootNodeId) }, params)
      else
        params
