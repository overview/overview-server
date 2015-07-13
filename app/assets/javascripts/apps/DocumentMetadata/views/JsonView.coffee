define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  DefaultValue = ''

  class JsonView extends Backbone.View
    tagName: 'form'
    className: 'metadata-json'

    events:
      'change input': '_onChangeInput'
      'submit': '_onSubmit'

    templates:
      main: _.template('<dl class="dl-horizontal"></dl>') # http://getbootstrap.com/css/#horizontal-description

      row: _.template('''
        <dt><%- fieldName %></dt>
        <dd><input name="<%- fieldName %>" value="<%- value %>"/></dd>
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
      alreadyRendered = {}
      children = @$('dl').children().toArray()
      for fieldName, i in (@_renderedMetadataFields ? [])
        alreadyRendered[fieldName] = children.slice(i * 2, i * 2 + 2)

      @_renderedMetadataFields = @documentSet.get('metadataFields')
      metadata = @document.get('metadata') ? {}
      els = @_renderedMetadataFields
        .map (f) =>
          if f of alreadyRendered
            alreadyRendered[f] # Array(HTMLElement,HTMLElement)
          else
            $(@templates.row({ fieldName: f, value: metadata[f] ? DefaultValue })).toArray() # Array(HTMLElement)
        .reduce(((sum, arr) -> sum.concat(arr)), []) # flatten into a single Array of HTMLElements

      $main = $(@templates.main()).append(els)

      @$el.empty().append($main)
      @

    _onChangeInput: -> @_saveChanges()
    _onSubmit: -> @_saveChanges()

    # Sends a PATCH for this document's "metadata"
    _saveChanges: ->
      metadata = {}
      for input in @$('input')
        $input = $(input)
        metadata[$input.attr('name')] = $input.val()

      @document.save({ metadata: metadata }, patch: true)
