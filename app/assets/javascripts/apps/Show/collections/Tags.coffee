define [
  'backbone'
  '../models/Tag'
], (Backbone, Tag) ->
  class Tags extends Backbone.Collection
    model: Tag
    comparator: (t1, t2) -> t1.get('name').localeCompare(t2.get('name'), undefined, sensitivity: 'base')

    initialize: (models, options={}) ->
      throw 'Must pass options.url, a String like "/documentsets/3/tags"' if !options.url
      throw 'URLs will all break unless you call Tags with no models' if models.length
      @url = options.url
