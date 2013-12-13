define [
  'backbone'
], (Backbone) ->
  class Document extends Backbone.Model
    defaults:
      id: 0 # ID from Overview
      title: ''
      description: ''
      text: '' # Text, if it's not a DocumentCloud document
      url: '' # The server gives this URL, but we should not display it. Use
              # urlProperties.url instead (it may be https rather than http)
      urlProperties: undefined # Object; must be passed

    initialize: ->
      throw 'Must set urlProperties' if !@get('urlProperties')

      if !@get('heading')
        @set('heading', @get('title') || @get('description'))
