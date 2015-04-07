define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
  'bootstrap-dropdown'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagCountList')

  class TagCountListView extends Backbone.View
    events:
      'click a.remove': '_onClickRemove'

    templates:
      main: _.template("""
        <div class="dropdown">
          <button class="btn btn-default dropdown-toggle" data-toggle="dropdown">
            <%- t('prompt') %>
            <span class="caret"></span>
          </button>
          <ul class="dropdown-menu" role="menu">
          </ul>
        </div>
      """)

      loading: _.template("""
        <li role="presentation" class="loading"><%- t('loading') %></li>
      """)

      empty: _.template("""
        <li role="presentation" class="empty"><%- t('empty') %></li>
      """)

      error: _.template("""
        <li role="presentation" class="error"><%- t('error') %></li>
      """)

      tag: _.template("""
        <li role="presentation" data-cid="<%- tag.cid %>">
          <div class="<%- tag.getClass() %> style="<%- tag.getStyle() %>">
            <span class="name"><%- tag.get('name') %></span>
          </div>
          <span class="count"><%- t('nDocuments', nDocuments) %></span>
          <a href="#" class="remove"><%- t('remove') %></a>
        </li>
      """)

    initialize: (options) ->
      throw 'Must pass options.documentSetId, a String' if !options.documentSetId
      throw 'Must pass options.tags, a Backbone.Collection' if !options.tags
      throw 'Must pass options.state, a State' if !options.state

      @tags = options.tags
      @state = options.state
      @documentSetId = options.documentSetId

      @listenTo(@state, 'change:oneDocumentSelected', @_renderVisible)

      @render()

    render: ->
      html = @templates.main(t: t)
      @$el.html(html)
      @$el.on 'show.bs.dropdown', (e) =>
        html = @templates.loading(t: t)
        @$('ul').html(html)
        @_load()

      @_renderVisible()

    _renderVisible: ->
      @$el.toggleClass('hide', @state.get('oneDocumentSelected'))

    remove: ->
      @$el.off('show.bs.dropdown')
      super()

    _load: ->
      @params = @state.get('documentListParams')?.toQueryParams() || {} # used in click handler

      @xhr?.abort()
      @xhr = $.ajax
        url: "/documentsets/#{@documentSetId}/tags/count"
        data: @params
        success: (json, textStatus, xhr) =>
          return if @xhr != xhr
          @xhr = null
          @_onSuccess(json)
        error: (xhr, textStatus, error) =>
          return if @xhr != xhr
          @xhr = null
          @_onError(xhr, textStatus, error)

    _onSuccess: (counts) ->
      html = if Object.keys(counts).length == 0
        @templates.empty(t: t)
      else
        @tags
          .filter((tag) -> String(tag.id) of counts)
          .map((tag) => @templates.tag(tag: tag, nDocuments: counts[String(tag.id)], t: t))
          .join('')

      @$('ul').html(html)

    _onError: (xhr, textStatus, error) ->
      console.warn('Error in XHR request', xhr, textStatus, error)
      html = @templates.error(t: t)
      @$('ul').html(html)

    _onClickRemove: (e) ->
      e.preventDefault()
      tagCid = $(e.target).closest('[data-cid]').attr('data-cid')
      @trigger('remove-clicked', tagCid: tagCid, queryParams: @params)
