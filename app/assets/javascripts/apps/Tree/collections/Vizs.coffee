define [
  'backbone'
  '../models/Viz'
], (backbone, Viz) ->
  class Vizs extends Backbone.Collection
    model: Viz

    comparator: (a, b) ->
      isJob = (x) -> x.attributes.type == 'job' && 1 || 0
      isError = (x) -> x.attributes.type == 'error' && 1 || 0

      isJob(b) - isJob(a) \
        || isError(b) - isError(a) \
        || b.attributes.createdAt - a.attributes.createdAt
