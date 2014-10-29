define [
  'jquery'
  'backbone'
], ($, Backbone) ->
  class ViewApp extends Backbone.View
    template: _.template("""
      <div class="view-app">
        <iframe width="1" height="1" src="<%- url %>/show?<%- params %>"></iframe>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.view, a View' if !options.view
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId

      @view = options.view
      @documentSetId = options.documentSetId

      @render()

    render: ->
      loc = window.location
      params = $.param([
        { name: 'server', value: "#{loc.protocol}//#{loc.host}" }
        { name: 'documentSetId', value: @documentSetId }
        { name: 'apiToken', value: @view.get('apiToken') }
      ])

      html = @template
        url: @view.get('url')
        params: params
      @$el.html(html)
      @
