define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.ViewTabs')

  # Enumerates a list of visualizations.
  #
  # Usage:
  #   views = new Views(...)
  #   view = new ViewTabs(views)
  #   $('body').append(view.el)
  #   view.on('click', (view) -> ...)
  #
  # The view will emit the following events:
  #
  # * click: user clicked a view, with the intent of displaying it.
  class ViewTabs extends Backbone.View
    tagName: 'ul'
    className: 'view-tabs nav nav-tabs'

    events:
      'click .toggle-popover': '_onClickPopover'
      'click li[data-id]>a': '_onClick'
      'click a[data-plugin-url]': '_onClickNewView'
      'click button.cancel': '_onClickCancel'
      'click button.delete': '_onClickDelete'
      'click a.rename': '_onClickRename'
      'click a.close': '_onClickClose'
      'submit form.rename': '_onSubmitRename'
      'reset form.rename': '_onResetRename'

    templates:
      view: _.template('''
        <li data-id="<%- id %>" class="<%- view.type %> <%- isSelected ? 'active' : '' %>">
          <a href="#">
            <span class="title"><%- view.title %></span>
            <% if (view.nDocuments) { %>
              <span class="count"><%- t('nDocuments', view.nDocuments) %></span>
            <% } %>
            <span class="toggle-popover view-info-icon icon icon-info-sign"></span>
            <% if (view.type == 'job' || view.type == 'error') { %>
              <progress value="<%- view.progress.fraction %>"></progress>
            <% } %>
          </a>

          <div class="popover bottom">
            <div class="arrow"></div>
            <div class="popover-content">
              <%= templates.viewDetails({ t: t, view: view, documentSet: documentSet }) %>
            </div>
          </div>
        </li>
        ''')

      viewDetails: _.template('''
        <a class="close close-top" href="#"><%- t('view.close.top') %></a>
        <dl class="view-details">
          <dt class="title"><%- t('view.title.dt') %></dt>
          <dd class="title">
            <div class="not-editing">
              <span class="title"><%- t('view.title.dd', view.title) %></span>
              <% if (view.type == 'tree' || view.type == 'view') { %>
                <a class="rename" href="#"><%- t('view.title.rename') %></a>
              <% } %>
            </div>
            <% if (view.type == 'tree' || view.type == 'view') { %>
              <div class="editing" style="display:none;">
                <form method="post" action="#" class="rename">
                  <div class="form-group">
                    <label class="sr-only" for="<%- view.id %>-title"><%- t('view.title.label') %></label>
                    <div class="input-group input-group-sm">
                      <input class="form-control" id="<%- view.id %>-title" name="title" value="<%- view.title %>" placeholder="<%- t('view.title.placeholder') %>" required>
                      <span class="input-group-btn">
                        <button class="btn btn-primary" type="submit"><%- t('view.title.save') %></label>
                        <button class="btn btn-default" type="reset"><%- t('view.title.reset') %></label>
                      </span>
                    </div>
                  </div>
                </form>
              </div>
            <% } %>
          </dd>

          <% if (view.nDocuments) { %>
            <dt class="n-documents"><%- t('view.nDocuments.dt') %></dt>
            <dd class="n-documents"><%- t('view.nDocuments.dd', view.nDocuments, documentSet.nDocuments) %></dd>
          <% } %>

          <% if (view.createdAt) { %>
            <dt class="created-at"><%- t('view.createdAt.dt') %></dt>
            <dd class="created-at"><%- t('view.createdAt.dd', view.createdAt) %></dd>
          <% } %>

          <% if (Object.keys(view.creationData).length) { %>
            <% view.creationData.forEach(function(d) { %>
              <% if (d[0] != 'rootNodeId' && d[0] != 'jobId' && d[0] != 'nDocuments') { %>
                <dt><%- t('view.' + d[0] + '.dt') %></dt>
                <dd><%- t('view.' + d[0] + '.dd', d[1]) %></dd>
              <% } %>
            <% }); %>
          <% } %>
        </dl>
        <% if (view.type == 'job' || view.type == 'error') { %>
          <button type="button" class="cancel btn btn-danger"><%- t('cancelJob') %></button>
        <% } else if (view.type == 'view' || view.type == 'tree') { %>
          <button type="button" class="delete btn btn-danger"><%- t('view.delete') %></button>
        <% } %>
        <a class="close close-bottom" href="#"><%- t('view.close.bottom') %></a>
        ''')

      main: _.template('''
        <% views.forEach(function(view) { %>
          <%= templates.view({
            t: t,
            templates: templates,
            isSelected: view == selectedView,
            id: view.id,
            view: view.attributes,
            documentSet: documentSet
          }) %>
        <% }); %>
        <li class="dropdown">
          <a href="#" data-toggle="dropdown">
            <%- t('newView') %>
            <span class="caret"></span>
          </a>
          <ul class="dropdown-menu" role="menu">
            <% plugins.forEach(function(plugin) { %>
              <li role="presentation">
                <a role="menuitem" href="#" data-plugin-url="<%- plugin.get('url') %>">
                  <span class="name"><%- plugin.get('name') %></span>
                  <span class="description"><%- plugin.get('description') %></span>
                </a>
              </li>
            <% }) %>
            <% if (plugins.length > 0) { %>
              <li role="presentation" class="divider"></li>
            <% } %>
            <li role="presentation">
              <a href="#" data-plugin-url="about:custom"><%- t('newView.custom') %></a>
            </li>
          </ul>
        </li>
        ''')

    initialize: ->
      throw 'must set options.documentSet' if !@options.documentSet
      throw 'must set options.plugins' if !@options.plugins
      throw 'must set options.collection' if !@options.collection
      throw 'must set options.state, a State' if !@options.state

      @state = @options.state
      @documentSet = @options.documentSet
      @plugins = @options.plugins

      @listenTo(@collection, 'remove', @_onRemove)
      @listenTo(@collection, 'add', @_onAdd)
      @listenTo(@collection, 'change', @_onChange)
      @listenTo(@state, 'change:view', @_onSelectedChanged)

      @render()
      @listenTo(@plugins, 'reset', @render)

    render: ->
      html = @templates.main
        views: @collection
        plugins: @plugins
        documentSet: @documentSet
        selectedView: @state.get('view')
        templates: @templates
        t: t

      @$el.html(html)
      this

    _onSelectedChanged: ->
      @$('.active').removeClass('active')
      view = @state.get('view')
      id = view?.id
      if id
        @$("li[data-id=#{id}]").addClass('active')

    _modelToHtml: (model) ->
      @templates.view
        t: t
        id: model.id
        templates: @templates
        isSelected: model == @state.get('view')
        view: model.attributes
        documentSet: @documentSet

    _onAdd: (model) ->
      # While we _expect_ the change won't break ordering of the set, we aren't
      # entirely certain; don't use binary search.
      index = @collection.indexOf(model)

      html = @_modelToHtml(model)
      
      @$("li:eq(#{index})").before(html)

    _onRemove: (model) ->
      @$("li[data-id=#{model.id}]").remove()

    _onChange: (model) ->
      # While we _expect_ the change won't break ordering of the set, we aren't
      # entirely certain; don't use binary search.
      index = @collection.indexOf(model)
      $li = @$("li:eq(#{index})")

      if model.hasChanged('progress')
        $li.find('progress').attr('value', model.attributes.progress?.fraction || '')

      if model.hasChanged('type')
        html = @_modelToHtml(model)
        $li.replaceWith(html)

      if model.hasChanged('title')
        $li.find('span.title').text(model.get('title'))

    _onClick: (e) ->
      e.preventDefault()
      viewId = e.currentTarget.parentNode.getAttribute('data-id')
      view = @collection.get(viewId)
      @trigger('click', view)

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

    _onClickNewView: (e) ->
      e.preventDefault()
      url = e.currentTarget.getAttribute('data-plugin-url')
      switch url
        when 'about:tree' then @trigger('click-new-tree')
        when 'about:custom' then @trigger('click-new-view')
        else @trigger('click-new-view', url: url, title: $('span.name', e.currentTarget).text())

    _onClickCancel: (e) ->
      e.preventDefault()
      dataId = $(e.currentTarget).closest('[data-id]').attr('data-id')
      job = @collection.get(dataId)
      @trigger('cancel', job)

    _onClickDelete: (e) ->
      e.preventDefault()
      dataId = $(e.currentTarget).closest('[data-id]').attr('data-id')
      view = @collection.get(dataId)
      if window.confirm(t('view.delete.confirm'))
        @trigger('delete-view', view)

    _onClickRename: (e) ->
      e.preventDefault()
      $dd = $(e.currentTarget).closest('dd')
      $dd.find('.editing').show()
      $dd.find('.not-editing').hide()
      $dd.find('input[name=title]').focus().select()

    _stopRenaming: (e) ->
      $dd = $(e.currentTarget).closest('dd')
      $dd.find('.editing').hide()
      $dd.find('.not-editing').show()

    _onSubmitRename: (e) ->
      e.preventDefault()

      dataId = $(e.currentTarget).closest('[data-id]').attr('data-id')
      view = @collection.get(dataId)
      $input = $(e.currentTarget).closest('form').find('input[name=title]')
      title = $input.val().trim()
      if title
        @trigger('update-view', view, title: title)

      @_stopRenaming(e)

    _onResetRename: (e) ->
      # Don't preventDefault(): we want the reset to happen
      @_stopRenaming(e)

    _onClickClose: (e) ->
      e.preventDefault()
      @$('.popover').removeClass('in').hide()
