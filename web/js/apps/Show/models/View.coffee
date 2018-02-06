import Backbone from 'backbone'

DefaultAttrs = {
  type: 'view' # 'tree' or 'view'
  title: '' # What the user calls this View
  creationData: [] # View-dependent [key,value] strings
}

# A View is an iframe served by a plugin.
#
# The id is an ID on the server; everything else is for displaying
# in the UI.
export default class View extends Backbone.Model
  constructor: (attrs, options) ->
    attrs = Object.assign({}, DefaultAttrs, attrs ? {})

    if 'createdAt' of attrs
      attrs.createdAt = new Date(attrs.createdAt)

    attrs.clientId = "#{attrs.type}-#{attrs.id}"

    super(attrs, options)

  idAttribute: 'clientId'

  url: ->
    "#{@collection.url.replace(/views$/, @attributes.type + 's')}/#{@attributes.id}"
