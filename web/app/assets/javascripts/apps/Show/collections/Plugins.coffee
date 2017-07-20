define [
  '../models/Plugin'
  'backbone'
], (Plugin, Backbone) ->
  class Plugins extends Backbone.Collection
    model: Plugin
    url: '/plugins'
    comparator: (p) -> p.attributes.name
