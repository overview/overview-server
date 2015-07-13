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
      <div class="details form-inline">
        <label for="document-metadata-add-field"><%- t('label') %></label>
        <input id="document-metadata-add-field" class="form-control" name="name" required="required" type="text" placeholder="<%- t('placeholder') %>">
        <button type="submit" class="btn btn-primary"><%- t('submit') %></button>
        <button type="reset" class="btn btn-default"><%- t('reset') %></button>
      </div>
    ''')

    events:
      'click a.expand': '_onClickExpand'
      'keydown input': '_onKeydown'
      'submit': '_onSubmit'
      'reset': '_onReset'

    initialize: (options) ->
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet

      @documentSet = options.documentSet

      @render()

    render: ->
      @$el.html(@template(t: t))

    _collapse: ->
      @$('input').val('')
      @$el.removeClass('expanded')

    _onClickExpand: (e) ->
      e.preventDefault()
      @$el.toggleClass('expanded')
      @$('input')[0].focus() if @$el.hasClass('expanded')

    _onKeydown: (e) ->
      if e.which == 27 # Escape
        e.stopPropagation()
        e.preventDefault()
        @_collapse()

    _onReset: (e) -> @_collapse()

    _onSubmit: (e) ->
      e.preventDefault()

      fields = @documentSet.get('metadataFields').slice(0)

      $input = @$('input')

      name = $input.val().trim()
      if name && name not in fields
        fields.push(name)
        @documentSet.patchMetadataFields(fields)

      @_collapse()
