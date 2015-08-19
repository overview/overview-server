define [
  'backbone'
  '../../Show/models/DocumentListParams'
], (Backbone, DocumentListParams) ->
  class DocumentListParamsModel extends Backbone.Model
    defaults:
      documentSet: undefined # A DocumentSet
      view: null             # A Backbone.Model, or null
      tagIds: []             # Array of Numbers
      tagOperation: 'any'    # any|all|none
      objectIds: []          # Array of Numbers
      nodeIds: []            # Array of Numbers. TODO make Tree a plugin, use objectIds
      title: ''              # String describing View+objectIds+nodeIds combination
      q: ''                  # String

    toDocumentListParams: ->
      ret = new DocumentListParams @get('documentSet'), @get('view'),
        nodes: @get('nodeIds')
        tags: @get('tagIds')
        objects: @get('objectIds')
        tagOperation: @get('tagOperation')
        q: @get('q')
        title: @get('title')
