define [
  'jquery'
  'backbone'
], ($, Backbone) ->
  class VizApp extends Backbone.View
    template: _.template("""
      <div class="viz-app">
        <iframe width="1" height="1" src="<%- url %>/show?<%- params %>"></iframe>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.viz, a Viz' if !options.viz
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId

      @viz = options.viz
      @documentSetId = options.documentSetId

      @render()

    render: ->
      loc = window.location
      params = $.param([
        { name: 'server', value: "#{loc.protocol}//#{loc.host}" }
        { name: 'documentSetId', value: @documentSetId }
        { name: 'vizId', value: @viz.get('id') }
        { name: 'apiToken', value: @viz.get('apiToken') }
      ])

      html = @template
        url: @viz.get('url')
        params: params
      @$el.html(html)
      @
