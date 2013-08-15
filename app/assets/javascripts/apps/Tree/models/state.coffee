define [ 'backbone', './selection' ], (Backbone, Selection) ->
  Backbone.Model.extend
    defaults:
      # Current selection: node, node+doc, tag, or tag+doc
      selection: new Selection()

      # Currently-displayed searchResult or tag
      taglike: null
