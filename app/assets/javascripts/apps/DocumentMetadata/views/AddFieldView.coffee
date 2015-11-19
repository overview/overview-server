define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.AddFieldView')

  # A form for creating a new metadata field.
  #
  # On submit, the form resets and adds the desired field to the model (if it's
  # not a duplicate).
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
      throw 'Must pass model, a Backbone.Model with a `fields` attribute' if !@model?

      @render()

    render: ->
      @$el.html(@template(t: t))
      @$input = @$('input')
      @

    _collapse: ->
      @$('input').val('')
      @$el.removeClass('expanded')

    _onClickExpand: (e) ->
      e.preventDefault()
      e.stopPropagation() # Prevent redirect confirmation when in MassUpload dialog
      @$el.toggleClass('expanded')
      @$input[0].focus() if @$el.hasClass('expanded')

    _onKeydown: (e) ->
      if e.which == 27 # Escape
        e.stopPropagation()
        e.preventDefault()
        @_collapse()

    _onReset: (e) ->
      @$input.val('')
      @_collapse()

    _onSubmit: (e) ->
      e.preventDefault()

      name = @$input.val().trim()

      if name && name not in @model.get('fields')
        fields = @model.get('fields').slice(0)
        fields.push(name)
        @model.set(fields: fields)

      @_collapse()
