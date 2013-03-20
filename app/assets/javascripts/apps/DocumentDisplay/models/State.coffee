define [ 'backbone', './Preferences' ], (Backbone, Preferences) ->
  Backbone.Model.extend {
    defaults: {
      document: undefined # a Document, or undefined
      preferences: undefined # Preferences
    }

    initialize: ->
      @set('preferences', new Preferences())
  }
