define [ 'backbone', 'underscore', 'util/humanReadableSize' ], (Backbone, _, humanReadableSize) ->
  # Shows a progress bar (must be extended)
  Backbone.View.extend
    className: 'list-files-progress'

    # Text to show before the progress bar
    preamble: -> ''

    # Attribute in MassUpload that is a { loaded: N, total: N } object
    progressProperty: ''

    # Attribute in MassUpload that is a developer-defined error
    errorProperty: ''

    # Text to show to prompt for retry
    retryText: ''

    events:
      'click .retry': '_onRetry'

    initialize: ->
      throw 'Must specify model, a MassUpload object' if !@model?
      @listenTo(@model, "change:#{@progressProperty}", => @_updateProgress())
      @listenTo(@model, "change:#{@errorProperty}", => @_updateError())
      @render()

    template: _.template("""
      <div class="error">
        <span class="message"><%- error ? error : '' %></span>
        <a href="#" class="retry"><%- retryText %></a>
      </div>
      <div class="preamble">
        <%- preamble() %>
      </div>
      <div class="progress">
        <progress value="<%= progress.loaded / progress.total * 100 %>" max="100"></progress>
        <span class="text"><%= humanReadableSize(progress.loaded) %> / <%= humanReadableSize(progress.total) %></span>
      </div>
    """)

    getProgress: -> @model.get(@progressProperty) || { loaded: 0, total: 0 }

    getError: -> @model.get(@errorProperty) || null

    render: ->
      progress = @getProgress()
      error = @getError()

      html = @template
        error: error
        preamble: @preamble
        retryText: @retryText
        progress: progress
        humanReadableSize: humanReadableSize

      @$el.html(html)
      @progressEl = @$el.find('progress')[0]
      @textNode = @$el.find('.text')[0].childNodes[0]
      @errorEl = @$el.find('.message')[0]

    _updateProgress: ->
      progress = @getProgress()

      @_setIfChanged('progress numerator', @progressEl, 'value', Math.round(progress.loaded / progress.total * 10000) / 100)
      @_setIfChanged('progress text', @textNode, 'data', "#{humanReadableSize(progress.loaded)} / #{humanReadableSize(progress.total)}")

      undefined

    # Caches the last-set value, to avoid spurious sets.
    _setIfChanged: (key, node, prop, value) ->
      nodeValueCache = (@_nodeValueCache ||= {})
      if key not of nodeValueCache or nodeValueCache[key] != value
        nodeValueCache[key] = value
        node[prop] = value
      undefined

    _updateError: ->
      error = @getError()
      Backbone.$(@errorEl).text(error || '')
      undefined

    _onRetry: (e) ->
      e.preventDefault()
      @trigger('retry')
#
