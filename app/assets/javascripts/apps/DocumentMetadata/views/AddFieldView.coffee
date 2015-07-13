define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.AddFieldView')

  class AddFieldView extends Backbone.View
    tagName: 'form'
    className: 'add-metadata-field'

    template: _.template('''
      <a href="#" class="expand"><%- t('expand') %></a>
      <div class="details">
        <label>
          <span><%- t('label') %></span>
          <input name="name" required="required" type="text" placeholder="<%- t('placeholder') %>">
        </label>
        <button type="submit"><%- t('submit') %></button>
        <button type="reset"><%- t('reset') %></button>
      </div>
    ''')

    events:
      'click a.expand': '_onClickExpand'
      'submit': '_onSubmit'
      'reset': '_onReset'

    initialize: (options) ->
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet

      @documentSet = options.documentSet

      @render()

    render: ->
      @$el.html(@template(t: t))

    _collapse: -> @$el.removeClass('expanded')

    _onClickExpand: (e) ->
      e.preventDefault()
      @$el.toggleClass('expanded')

    _onReset: (e) ->
      # Don't preventDefault()
      @_collapse()

    _onSubmit: (e) ->
      e.preventDefault()

      fields = @documentSet.get('metadataFields').slice(0)

      name = @$('input').val().trim()
      if name && name not in fields
        fields.push(name)
        @documentSet.save({ metadataFields: fields }, patch: true)

      @_collapse()
