define [
  'backbone'
], (Backbone) ->
  class Plugin extends Backbone.Model
    defaults:
      name: ''
      description: ''
      url: ''
      serverUrlFromPlugin: ''
      autocreate: false
      autocreateOrder: 0
