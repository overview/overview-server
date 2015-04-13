define [
  'backbone'
  '../models/Tag'
], (Backbone, Tag) ->
  class Tags extends Backbone.Collection
    model: Tag
    comparator: 'name'

    initialize: (models, options={}) ->
      throw 'Must pass options.url, a String like "/documentsets/3/tags"' if !options.url
      @url = options.url
