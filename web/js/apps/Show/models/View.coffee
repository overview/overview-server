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
      type: 'view' # 'tree' or 'view'
      title: '' # What the user calls this View
      creationData: [] # View-dependent [key,value] strings

    constructor: (attrs, options) ->
      attrs = _.extend({}, @defaults, attrs ? {})

      if 'createdAt' of attrs
        attrs.createdAt = new Date(attrs.createdAt)

      attrs.clientId = "#{attrs.type}-#{attrs.id}"

      super(attrs, options)

    idAttribute: 'clientId'

    url: ->
      "#{@collection.url.replace(/views$/, @attributes.type + 's')}/#{@attributes.id}"
