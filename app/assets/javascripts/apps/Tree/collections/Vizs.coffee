define [
  'backbone'
  '../models/Viz'
], (backbone, Viz) ->
  class Vizs extends Backbone.Collection
    model: Viz
