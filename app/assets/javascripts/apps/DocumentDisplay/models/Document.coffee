define [
  'backbone'
  'apps/DocumentDisplay/models/UrlPropertiesExtractor'
], (Backbone, UrlPropertiesExtractor) ->
  urlToProperties = UrlPropertiesExtractor.urlToProperties

  Backbone.Model.extend {
    defaults: {
      id: 0 # ID from Overview
      title: ''
      description: ''
      text: '' # Text, if it's not a DocumentCloud document
      url: '' # The server gives this URL, but we should not display it. Use
              # urlProperties.url instead (it may be https rather than http)
    }

    initialize: ->
      url = @get('url') || ''
      @set('urlProperties', UrlPropertiesExtractor.urlToProperties(url))
  }
