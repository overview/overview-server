define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
  'bootstrap-popover'
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
      'click [data-toggle=popover]': '_onClickPopover'
      'click a[data-viz-id]': '_onClick'

    templates:
      viz: _.template('''
        <li class="viz <%- isSelected ? 'active' : '' %>">
          <a href="#" data-viz-id="<%- viz.id %>">
            <%- viz.title %>
            <span
              class="viz-info-icon icon-info-sign"
              data-toggle="popover"
              data-trigger="manual"
              data-placement="bottom"
              data-container="body"
              data-html="true"
              data-content="<%- templates.vizDetails({ t: t, viz: viz }) %>"
              ></span>
          </a>
        </li>
        ''')

      vizDetails: _.template('''
        <dl class="viz-details">
          <dt><%- t('viz.title.dt') %></dt>
          <dd><%- t('viz.title.dd', viz.title) %></dd>

          <dt><%- t('viz.createdAt.dt') %></dt>
          <dd><%- t('viz.createdAt.dd', viz.createdAt) %></dd>

          <% viz.creationData.forEach(function(d) { %>
            <dt><%- t('viz.' + d[0] + '.dt') %></dt>
            <dd><%- t('viz.' + d[0] + '.dd', d[1]) %></dd>
          <% }); %>
        </dl>
        ''')

      main: _.template('''
        <% vizs.forEach(function(viz) { %>
          <%= templates.viz({ t: t, templates: templates, isSelected: viz.id == selected.id, viz: viz.attributes }) %>
        <% }); %>
        ''')

    initialize: ->
      throw 'must set options.collection' if !@options.collection

      @render()

    render: ->
      html = @templates.main
        vizs: @collection
        selected: @options.selected || { id: 0 }
        templates: @templates
        t: t

      @$el.html(html)
      this

    _onClick: (e) ->
      e.preventDefault()
      vizId = e.currentTarget.getAttribute('data-viz-id')
      viz = @collection.get(vizId)
      @trigger('click', viz)

    _onClickPopover: (e) ->
      $el = $(e.currentTarget)
      @$('[data-toggle=popover]')
        .not($el)
        .popover('hide')
      $el.popover('toggle')
      e.stopPropagation()
