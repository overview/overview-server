define [
  'underscore'
  'backbone'
  'i18n'
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
            <label for="tag-this-name"></label>
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
      'input input': '_onInput'
      'submit form': '_onSubmit'

    initialize: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.tags, a Tags' if !options.tags

      @tags = options.tags
      @state = options.state

      @showDetails = false

      @listenTo(@state, 'change:oneDocumentSelected change:documentListParams change:document', @_onChangeState)

      @render()

    render: ->
      @_initialRender() if !@$button

      @_refreshShowingDetails()
      @_refreshButtonText()
      @_refreshInputValue()
      @_refreshLabelText()
      @_refreshButtonDisabled()

    _refreshShowingDetails: ->
      @$el.toggleClass('show-details', @showDetails)

    _refreshButtonText: ->
      variant = @state.get('oneDocumentSelected') && 'document' || 'list'
      @$button.text(t("button.#{variant}"))

    _refreshInputValue: ->
      @$input.val(@_getDefaultValue())

    _refreshLabelText: ->
      value = @$input.val()
      isNew = @tags.where(name: value).length == 0
      @$label.text(isNew && t('label.new') || t('label.existing'))

    _refreshButtonDisabled: ->
      value = @$input.val()
      disabled = (value.trim() == '')
      @$button.prop('disabled', disabled)

    _getDefaultValue: ->
      valueArgs = @state.get('documentListParams').toI18n?() || ['all']
      t("value.#{valueArgs[0]}", valueArgs.slice(1)...)

    _initialRender: ->
      @$el.html(@template(t: t))
      @$button = @$('button')
      @$input = @$('input')
      @$label = @$('label')

    _onClickPrompt: ->
      @showDetails = true
      @_refreshInputValue()
      @_refreshLabelText()
      @_refreshShowingDetails()
      @$input.focus().select()

    _onInput: ->
      @_refreshLabelText()
      @_refreshButtonDisabled()

    _onSubmit: (e) ->
      e.preventDefault()
      @trigger('tag', name: @$input.val().trim())
      @_reset()

    _onChangeState: -> @_reset()
    _onClickClose: (e) -> e.preventDefault(); @_reset()

    _reset: ->
      @showDetails = false
      @_refreshShowingDetails()
      @_refreshButtonText()
