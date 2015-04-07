define [
  'underscore'
  'backbone'
  './color_table'
  'tinycolor'
], (_, Backbone, ColorTable, tinycolor) ->
  class Tag extends Backbone.Model
    defaults:
      name: ''
      color: null

    constructor: (attributes, options) ->
      attributes = _.extend({}, attributes)
      if !attributes.color
        attributes.color = new ColorTable().get(attributes.name)

      super(attributes, options)

    # Returns "tag tag-light" or "tag tag-dark". The "tag-light" means the
    # text on the tag should be black; "tag-dark" means the text should be
    # white.
    getClass: ->
      c = tinycolor.mostReadable(@get('color'), ['white', 'black']).toName()
      switch c
        when 'white' then 'tag tag-dark'
        else 'tag tag-light'

    # Returns "background-color: [xxx]". When creating an HTML element, use
    # this in conjunction with getClass().
    getStyle: -> "background-color: #{@get('color')}"
