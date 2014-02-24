define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.index.ImportOptions')

  MinDocumentSetSize = 3

  class TagIdInput extends Backbone.View
    template: _.template("""
      <select id="import-options-tag-id" name="tag_id">
        <option selected="selected" value=""><%- t('tag.allDocuments') %></option>
        <option disabled="disabled"><%- t('tag.loading') %></option>
      </select>
      """)

    tagTemplate: _.template("""
      <option value="<%- id %>"<%= (size < #{MinDocumentSetSize}) ? ' disabled="disabled"' : '' %>><%- t('tag.name', name, size) %></option>
      """)

    events:
      'change select': '_onChange'

    initialize: (options) ->
      @tagListUrl = options.tagListUrl

      throw 'Must pass model, a Backbone.Model' if !@model
      throw 'Must pass tagListUrl, a URL that gives a JSON { tags: [ ... ] } response' if !@tagListUrl
      @model.set(tag_id: '')

    render: ->
      html = @template(t: t)
      @$el.html(html)
      @$('select').one 'focus', (=> @_loadTags())

    _loadTags: ->
      @$el.attr('class', 'loading')
      $.get(@tagListUrl)
        .done((json) => @_renderTags(json.tags))
        .fail(=> @_renderTagLoadFailure())

    _renderTagLoadFailure: ->
      @$el.attr('class', 'error')
      @$('option:disabled').text(t('tag.error'))

    _renderTags: (@tags) ->
      @$el.attr('class', '')
      $select = @$('select')
      $select.find('option:disabled').remove()
      @tags.sort((a, b) -> a.name.localeCompare(b.name))
      for tag in @tags
        # Append elements, don't do $select.html(). We don't want to deselect
        # the original element.
        $select.append($(@tagTemplate(t: t, id: tag.id, name: tag.name, size: tag.size)))
      undefined

    _onChange: (e) ->
      e.preventDefault()
      id = @$('select').val()
      @model.set(tag_id: id)
      tag = _.findWhere(@tags, id: parseInt(id, 10))
      @$('.dropdown-toggle').text(t('tag.name', tag?.name || '', tag?.size || -1))
