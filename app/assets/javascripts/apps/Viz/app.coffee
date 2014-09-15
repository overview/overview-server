define [
  'backbone'
], (Backbone) ->
  class VizApp extends Backbone.View
    template: _.template("""
      <div class="viz-app">
        <iframe width="1" height="1" src="<%- viz.url %>/show?documentSetId=<%- documentSetId %>&amp;vizId=<%- viz.id %>&amp;apiToken=<%- viz.apiToken %>"></iframe>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.viz, a Viz' if !options.viz
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId

      @viz = options.viz
      @documentSetId = options.documentSetId

      @render()

    render: ->
      html = @template
        documentSetId: @documentSetId
        viz: @viz.attributes
      @$el.html(html)
      @
