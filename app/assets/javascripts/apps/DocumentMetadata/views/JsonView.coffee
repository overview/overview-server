define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.JsonView')

  DefaultValue = ''

  class JsonView extends Backbone.View
    tagName: 'form'
    className: 'metadata-json form-horizontal'

    events:
      'change input': '_onChangeInput'
      'focus input': '_onFocusInput'
      'mousedown input': '_onMousedownInput'
      'mouseup input': '_onMouseupInput'
      'click button.delete': '_onClickDelete'
      'submit': '_onSubmit'

    templates:
      row: _.template('''
        <% var randomInputId = 'json-view-input-' + Math.random().toString().split('.')[1]; %>
        <div class="form-group form-group-sm">
          <label for="<%= randomInputId %>" class="col-sm-2 control-label"><%- fieldName %></label>
          <div class="col-sm-10">
            <input id="<%= randomInputId %>" class="form-control" name="<%- fieldName %>" value="<%- value %>"/>
            <button type="button" class="delete" data-field-name="<%- fieldName %>" title="<%- t('delete') %>" data-confirm="<%- t('confirmDelete', fieldName) %>">
              <i class="overview-icon-trash"></i>
            </button>
          </div>
        </div>
      ''')

    initialize: (options) ->
      throw 'Must specify options.documentSet, a Backbone.Model with metadataFields' if !options.documentSet
      throw 'Must specify options.document, a Backbone.Model with metadata' if !options.document

      @documentSet = options.documentSet
      @document = options.document

      @listenTo(@documentSet, 'change:metadataFields', @render)
      @render()

    render: ->
      # Maintain previous HTML elements -- that way, we don't overwrite the
      # user's stuff during render
      alreadyRendered = {} # Hash of fieldName => HTMLEntity
      children = @$el.children().toArray()
      for fieldName, i in (@_renderedMetadataFields ? [])
        alreadyRendered[fieldName] = children[i]

      @_renderedMetadataFields = @documentSet.get('metadataFields')
      metadata = @document.get('metadata') ? {}
      els = @_renderedMetadataFields
        .map (f) =>
          if f of alreadyRendered
            alreadyRendered[f]
          else
            $(@templates.row({ fieldName: f, t: t, value: metadata[f] ? DefaultValue }))[0]

      if els.length
        @$el.empty().append(els)
      else
        @$el.empty().append($('<p class="help"></p>').html(t('help_html')))

      @

    _onChangeInput: -> @_saveChanges()
    _onSubmit: (e) -> e.preventDefault(); @_saveChanges()

    _onFocusInput: (e) ->
      # https://code.google.com/p/chromium/issues/detail?id=4505
      #
      # We want to select-all when focusing. But we *don't* want to select-all
      # when clicking in the text field a second time, because that would
      # prevent the user from moving the cursor to a position within the field.
      $(e.currentTarget).select() if !@_clickingAndAlreadyFocused

    _onMousedownInput: (e) ->
      @_clickingAndAlreadyFocused = $(e.currentTarget).is(':focus')
      undefined # avoid "return false"

    _onMouseupInput: (e) ->
      e.preventDefault() if !@_clickingAndAlreadyFocused
      @_clickingAndAlreadyFocused = false
      undefined # avoid "return false"

    _onClickDelete: (e) ->
      confirm = e.currentTarget.getAttribute('data-confirm')
      if window.confirm(confirm)
        fieldName = e.currentTarget.getAttribute('data-field-name')
        newFields = @documentSet.get('metadataFields').filter((n) -> n != fieldName)
        @documentSet.patchMetadataFields(newFields)
      e.preventDefault()

    # Sends a PATCH for this document's "metadata"
    _saveChanges: ->
      metadata = {}
      for input in @$('input')
        $input = $(input)
        metadata[$input.attr('name')] = $input.val()

      @document.save({ metadata: metadata }, patch: true)
