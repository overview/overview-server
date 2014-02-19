define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
  'bootstrap-dropdown'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.index.ImportOptions')

  class TagIdInput extends Backbone.View
    template: _.template("""
      <div class="dropdown">
        <a class="dropdown-toggle" id="tag-id-select" data-toggle="dropdown" href="#"><%- t('tag.allDocuments') %></a>
        <ul class="dropdown-menu" role="menu" aria-labelledby="tag-id-select">
          <li class="loading" role="presentation"><%- t('tag.loading') %></li>
        </ul>
      </div>
      """)

    tagTemplate: _.template("""
      <li role="presentation"><a data-tag-id="<%- id %>" href="#"><%- t('tag.name', name, size) %></a></li>
      """)

    events:
      'click a[data-tag-id]': '_onClick'

    initialize: (options) ->
      @tagListUrl = options.tagListUrl

      throw 'Must pass model, a Backbone.Model' if !@model
      throw 'Must pass tagListUrl, a URL that gives a JSON { tags: [ ... ] } response' if !@tagListUrl
      @model.set(tag_id: '')

    render: ->
      html = @template(t: t, tag: @tag, allTags: @allTags)
      @$el.html(html)
      @$('.dropdown-toggle')
        .dropdown()
        .one 'click', (=> @_loadTags())

    _loadTags: ->
      $.get(@tagListUrl)
        .done((json) => @_renderTags(json.tags))
        .fail(=> @_renderTagLoadFailure())

    _renderTagLoadFailure: ->
      @$('ul').html("""<li class="error" role="presentation">#{t('tag.error')}</li>""")

    _renderTags: (@tags) ->
      $ul = @$('ul')
      html = """<li role="presentation"><a data-tag-id="" href="#">#{t('tag.allDocuments')}</a></li>"""
      for tag in tags
        html += @tagTemplate(t: t, id: tag.id, name: tag.name, size: tag.size)
      $ul.html(html)

    _onClick: (e) ->
      e.preventDefault()
      id = e.currentTarget.getAttribute('data-tag-id')
      @model.set(tag_id: id)
      tag = _.findWhere(@tags, id: parseInt(id, 10))
      @$('.dropdown-toggle').text(t('tag.name', tag?.name || '', tag?.size || -1))
