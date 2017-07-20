define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.ApiTokens.form')

  class FormView extends Backbone.View
    tagName: 'form'
    attributes:
      role: 'form'

    events:
      'submit': '_onSubmit'

    template: _.template('''
      <h3><%- t('heading') %></h3>
      <div class="form-group">
        <label for="api-token-description"><%- t('description.label') %></label>
        <input type="text" class="form-control" id="api-token-description" placeholder="<%- t('description.placeholder') %>">
      </div>
      <button type="submit" class="btn btn-default"><%- t('submit.label') %></label>
    ''')

    render: ->
      html = @template(t: t)
      @$el.html(html)

      @$input = @$('input')

      @

    _onSubmit: (e) ->
      e.preventDefault()

      description = @$input.val().trim()
      if description
        @collection.create(description: description)
        @el.reset()
