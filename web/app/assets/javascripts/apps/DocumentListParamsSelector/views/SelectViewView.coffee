define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListParamsSelector.SelectViewView')

  MaxRecentSelections = 5

  # Lets the user select whether to show documents in the entire docset or in
  # a particular View.
  #
  # This View lets the user choose to search within:
  #
  # * the entire document set
  # * the last-seen View
  # * within the last-seen View, the last-seen object/node/title combination
  # * within the last-seen View, the second-last-seen object/node/title combination
  # * ... et cetera.
  #
  # The View listens to @model to detect when the user changes stuff. It can
  # _set_ `view`, `objectIds`, `nodeIds` and `title` on the model.
  class SelectViewView extends Backbone.View
    templates:
      withView: _.template('''
        <div class="dropdown">
          <a data-target="#" href="#" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">
            <span class="text"><%- selectedOption.text %></span>
            <span class="caret"></span>
          </a>
          <ul class="dropdown-menu">
            <% options.forEach(function(option) { %>
              <li><a href="#" data-attrs="<%- option.attrs %>"><%- option.text %></a>
            <% }); %>
          </ul>
        </div>
      ''')

    events:
      'click a[data-attrs]': '_onClickOption'

    initialize: (options) ->
      if 'model' not of options
        throw new Error('Must set options.model, a DocumentListParamsModel')
      if 'views' not of options
        throw new Error('Must set options.views, a Backbone.Collection of Models with `title` attributes')

      @model = options.model
      @state = options.state
      @views = options.views

      @listenTo(@model, 'change', @render)
      @listenTo(@state, 'change:view', @render)

      @render()

    # Encodes relevant DocumentListParams properties into a string.
    #
    # Guarantees:
    # * There is only one String representation for any parameters.
    # * Decoding will never fail.
    _encodeParamParts: (paramParts) ->
      [
        paramParts.view?.cid || ''
        paramParts.objectIds.sort().join(',') # sort() for uniqueness guarantee
        paramParts.nodeIds.sort().join(',')
        paramParts.title
      ].join(':')

    # Reverses _encodeParamParts
    _decodeParamParts: (paramPartsString) ->
      # title may contain ':', so we can't use paramPartsString.split(':', 4).

      # Personality test: do you see happy smileys or sad smileys?
      m = /([^:]*):([^:]*):([^:]*):(.*)/.exec(paramPartsString)

      parseArray = (s) ->
        if !s
          []
        else
          s.split(',').map((t) -> parseInt(t, 10))

      view: @views.get(m[1]) || null # m[1] may be empty, which makes this null
      objectIds: parseArray(m[2])
      nodeIds: parseArray(m[3])
      title: m[4]

    _encodeCurrentParams: ->
      @_encodeParamParts
        view: @model.get('view')
        objectIds: @model.get('objectIds')
        nodeIds: @model.get('nodeIds')
        title: @model.get('title')

    render: ->
      if (view = @state.get('view'))?
        options = [
          { text: t('inDocumentSet'), attrs: @_encodeParamParts(view: null, objectIds: [], nodeIds: [], title: '') }
          { text: t('inView', view.get('title')), attrs: @_encodeParamParts(view: view, objectIds: [], nodeIds: [], title: '') }
        ]
        @_maybeLogCurrentParams()
        options = options.concat(@_getLoggedParamOptions())

        selectedOption = if @model.get('objectIds').length + @model.get('nodeIds').length > 0
          options[2]
        else if @model.get('view') == view
          options[1]
        else
          options[0]

        @$el
          .html(@templates.withView(selectedOption: selectedOption, options: options))
          .attr(class: 'has-view')
      else
        @$el
          .text(t('inDocumentSet'))
          .attr(class: 'has-no-view')

    # Saves the current state of the model into history, MAYBE.
    #
    # We save the state when an object or node is selected.
    _maybeLogCurrentParams: ->
      return if @model.get('objectIds').length + @model.get('nodeIds').length == 0

      view = @model.get('view') # assume non-null

      # History doesn't persist between Views
      if view != @_pastOptionsView
        @_pastOptionsView = view
        @_pastOptions = []

      currentAttrs = @_encodeCurrentParams()

      # Remove entries that will become duplicates
      @_pastOptions = @_pastOptions.filter((attrs) -> attrs != currentAttrs)

      # Log this option
      @_pastOptions.unshift(currentAttrs)

      # Truncate so we only have the most recent entries
      @_pastOptions = @_pastOptions.slice(0, MaxRecentSelections)

    _getLoggedParamOptions: ->
      for encodedAttrs in (@_pastOptions || [])
        attrs = @_decodeParamParts(encodedAttrs)
        { text: attrs.title.replace('%s ', ''), attrs: encodedAttrs }

    _onClickOption: (e) ->
      attrs = @_decodeParamParts(e.currentTarget.getAttribute('data-attrs'))

      @model.set(attrs)
