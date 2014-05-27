define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.VizTabs')

  # Enumerates a list of visualizations.
  #
  # Usage:
  #   vizs = new Vizs(...)
  #   view = new VizTabs(vizs)
  #   $('body').append(view.el)
  #   view.on('click', (viz) -> ...)
  #
  # The view will emit the following events:
  #
  # * click: user clicked a viz, with the intent of displaying it.
  class VizTabs extends Backbone.View
    tagName: 'ul'
    className: 'viz-tabs nav nav-tabs'

    events:
      'click a[data-viz-id]': '_onClick'

    templates:
      viz: _.template('''
        <li class="viz <%- isSelected ? 'active' : '' %>">
          <a href="#" data-viz-id="<%- viz.id %>">
            <%- viz.title %>
          </a>
        </li>
        ''')

      main: _.template('''
        <% vizs.forEach(function(viz) { %>
          <%= vizTemplate({ isSelected: viz.id == selected.id, viz: viz.attributes }) %>
        <% }); %>
        ''')

    initialize: ->
      throw 'must set options.collection' if !@options.collection

      @render()

    render: ->
      html = @templates.main
        vizs: @collection
        selected: @options.selected || { id: 0 }
        vizTemplate: @templates.viz
        t: t

      @$el.html(html)
      this

    _onClick: (e) ->
      e.preventDefault()
      vizId = e.currentTarget.getAttribute('data-viz-id')
      viz = @collection.get(vizId)
      @trigger('click', viz)
