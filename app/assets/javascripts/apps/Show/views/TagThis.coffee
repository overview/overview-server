define [
  'underscore'
  'backbone'
  'i18n'
  'typeahead'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagThis')

  class TagThis extends Backbone.View
    className: 'tag-this'

    template: _.template('''
      <div class="prompt">
        <button class="btn btn-default"></button>
      </div>
      <div class="details">
        <form method="post" action="#" class="form-inline">
          <div class="form-group">
            <a class="close"><%- t('hide') %></a>
          </div>
          <div class="form-group">
            <div class="input-group">
              <input type="text" class="form-control input-sm" name="name" placeholder="<%- t('placeholder') %>">
              <span class="input-group-btn">
                <button class="btn btn-primary" type="submit"></button>
              </span>
            </div>
          </div>
        </form>
      </div>
    ''')

    events:
      'click .prompt button': '_onClickPrompt'
      'click .close': '_onClickClose'
      'input input[name=name]': '_onInput'
      'keydown input[name=name]': '_onKeyDown'
      'submit form': '_onSubmit'

    initialize: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.tags, a Tags' if !options.tags

      @tags = options.tags
      @state = options.state
      @keyboardController = options.keyboardController # optional

      @showDetails = false

      if @keyboardController?
        @keyBindings =
          T: => @_open()
        @keyboardController.register(@keyBindings)

      @listenTo(@state, 'change:oneDocumentSelected change:documentListParams change:document', @_onChangeState)

      @render()

    remove: ->
      @keyboardController?.unregister(@keyBindings)
      super()

    render: ->
      @_initialRender() if !@ui?

      @_refreshShowingDetails()
      @_refreshButtonText()
      @_refreshInputValue()
      @_refreshButtonDisabled()

    _refreshShowingDetails: ->
      @$el.toggleClass('show-details', @showDetails)

    _refreshButtonText: ->
      variant = @state.get('oneDocumentSelected') && 'document' || 'list'
      @ui.buttons.text(t("button.#{variant}"))

    _refreshInputValue: ->
      @ui.input.typeahead('val', @_getDefaultValue())

    _refreshButtonDisabled: ->
      value = @ui.input.val()
      disabled = (value.trim() == '')
      @ui.submit.prop('disabled', disabled)

    _getDefaultValue: ->
      @state.get('documentListParams').title.replace('%s', t('documents'))

    _initialRender: ->
      @$el.html(@template(t: t))

      @ui =
        prompt: @$('.prompt button')
        input: @$('.details input[name=name]')
        submit: @$('.details button')
        buttons: @$('button')

      @ui.input.typeahead {},
        name: 'tags'
        source: (query, cb) =>
          tags = @tags
            .filter((tag) -> tag.get('name').toLowerCase().indexOf(query.toLowerCase()) == 0)
          cb(tags)
        displayKey: (tag) -> tag.get('name')
        templates:
          suggestion: _.template("""
            <p><span class="tag" style="background-color: <%- get('color') %>"><%- get('name') %></span></p>
          """)

      # Stupid typeahead JS has styles in it. Counter with more styles. This
      # dialog shows up at the bottom of the page, so it should open upwards.
      @ui.input.nextAll('.tt-dropdown-menu').css(top: 'auto', bottom: '100%')

    _onClickPrompt: ->
      @_open()

    _open: ->
      @showDetails = true
      @_refreshInputValue()
      @_refreshShowingDetails()
      @ui.input.focus().select()

    _onInput: ->
      @_refreshButtonDisabled()

    _onKeyDown: (e) ->
      if e.keyCode == 27 # Escape
        @_reset()

    _onSubmit: (e) ->
      e.preventDefault()
      @trigger('tag', name: @ui.input.val().trim())
      @_reset()

    _onChangeState: -> @_reset()
    _onClickClose: (e) -> e.preventDefault(); @_reset()

    _reset: ->
      @showDetails = false
      @_refreshShowingDetails()
      @_refreshButtonText()
