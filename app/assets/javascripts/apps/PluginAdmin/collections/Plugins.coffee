define [
  '../models/Plugin'
  'backbone'
], (Plugin, Backbone) ->
  class Plugins extends Backbone.Collection
    model: Plugin
    comparator: (m) -> m.attributes.name
    url: '/plugins'
