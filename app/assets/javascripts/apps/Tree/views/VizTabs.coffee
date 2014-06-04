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
      'click .toggle-popover,.job,.error': '_onClickPopover'
      'click .viz[data-id]>a': '_onClick'
      'click a.new-viz': '_onClickNew'
      'click button.cancel': '_onClickCancel'

    templates:
      viz: _.template('''
        <li data-id="<%- viz.type %>-<%- viz.id %>" class="<%- viz.type %> <%- isSelected ? 'active' : '' %>">
          <a href="#">
            <span class="title"><%- viz.title %></span>
            <span class="toggle-popover viz-info-icon icon-info-sign"></span>
            <% if (viz.type == 'job' || viz.type == 'error') { %>
              <progress value="<%- viz.progress.fraction %>"></progress>
            <% } %>
          </a>

          <div class="popover bottom">
            <div class="arrow"></div>
            <div class="popover-content">
              <%= templates.vizDetails({ t: t, viz: viz }) %>
            </div>
          </div>
        </li>
        ''')

      vizDetails: _.template('''
        <dl class="viz-details">
          <dt><%- t('viz.title.dt') %></dt>
          <dd><%- t('viz.title.dd', viz.title) %></dd>

          <% if (viz.createdAt) { %>
            <dt><%- t('viz.createdAt.dt') %></dt>
            <dd><%- t('viz.createdAt.dd', viz.createdAt) %></dd>
          <% } %>

          <% viz.creationData.forEach(function(d) { %>
            <dt><%- t('viz.' + d[0] + '.dt') %></dt>
            <dd><%- t('viz.' + d[0] + '.dd', d[1]) %></dd>
          <% }); %>
        </dl>
        <% if (viz.type == 'job' || viz.type == 'error') { %>
          <button type="button" class="cancel btn btn-danger"><%- t('cancelJob') %></button>
        <% } %>
        ''')

      main: _.template('''
        <% vizs.forEach(function(viz) { %>
          <%= templates.viz({ t: t, templates: templates, isSelected: viz.id == selected.id, viz: viz.attributes }) %>
        <% }); %>
        <li class="new-viz">
          <a href="#" class="new-viz">
            <i class="icon-overview-plus"></i>
            <%- t('new_viz') %>
          </a>
        </li>
        ''')

    initialize: ->
      throw 'must set options.collection' if !@options.collection

      @listenTo(@collection, 'remove', @_onRemove)
      @listenTo(@collection, 'add', @_onAdd)
      @listenTo(@collection, 'change', @_onChange)

      @render()

    render: ->
      html = @templates.main
        vizs: @collection
        selected: @options.selected || { id: 0 }
        templates: @templates
        t: t

      @$el.html(html)
      this

    _onAdd: (model, __, options) ->
      html = @templates.viz
        t: t
        templates: @templates
        isSelected: false
        viz: model.attributes

      # While we _expect_ the change won't break ordering of the set, we aren't
      # entirely certain; don't use binary search.
      index = @collection.indexOf(model)
      
      @$("li:eq(#{index})").before(html)

    _onRemove: (model) ->
      @$("li[data-id=#{model.id}]").remove()

    _onChange: (model) ->
      # While we _expect_ the change won't break ordering of the set, we aren't
      # entirely certain; don't use binary search.
      index = @collection.indexOf(model)

      $li = @$("li:eq(#{index})")
      $li.find('progress').attr('value', model.attributes.progress?.fraction || '')

    _onClick: (e) ->
      e.preventDefault()
      vizId = e.currentTarget.parentNode.getAttribute('data-id')
      viz = @collection.get(vizId)
      @trigger('click', viz)

    _onClickPopover: (e) ->
      $el = $(e.currentTarget)
      $li = $el.closest('li')
      $popover = $li.children('.popover')
      $arrow = $popover.find('.arrow')
      $focus = $popover.siblings().eq(0).children().eq(1)

      @$('.popover')
        .not($popover)
        .removeClass('in')
        .hide()

      if $popover.hasClass('in')
        $popover.removeClass('in').hide()
      else
        $popover.show() # repaint, so we can calculate things

        offset =
          top: Math.floor($focus.position().top + $focus.height() + 1)
          left: Math.floor($popover.width() * -0.5 + $focus.position().left + $focus.width() * 0.5 - 1)
        arrowLeft = '50%'

        liLeft = $li.offset().left
        if liLeft + offset.left < 0
          delta = offset.left - liLeft
          offset.left = -liLeft
          arrowLeft = (50 * (1 + 2 * delta / $popover.width())) + '%'

        $popover
          .css
            top: "#{offset.top}px"
            left: "#{offset.left}px"
          .addClass('in')

        $arrow.css(left: arrowLeft)

      e.stopPropagation()

    _onClickNew: (e) ->
      e.preventDefault()
      @trigger('click-new')

    _onClickCancel: (e) ->
      e.preventDefault()
      dataId = $(e.currentTarget).closest('[data-id]').attr('data-id')
      job = @collection.get(dataId)
      @trigger('cancel', job)
