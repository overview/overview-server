define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.SearchView')

  StandardFieldNames = {
    'text': true,
    'title': true,
    'notes': true,
  }

  quoteField = (fieldName) ->
    # if there are any non-letters...
    if /[^\w]/.test(fieldName) or StandardFieldNames[fieldName]
      # double-quote; add backslashes before double-quotes or backslashes
      "\"#{fieldName.replace(/[\\\"]/g, (s) => "\\#{s}")}\""
    else
      fieldName

  # Prompts the user to enter a search; displays the active search.
  #
  # Listens to:
  # * model.change('q')
  #
  # Calls:
  # * state.refineDocumentListParams(q: 'a string')
  # * state.refineDocumentListParams(q: null)
  #
  # Triggers: nothing
  class SearchView extends Backbone.View
    template: _.template("""
      <form method="post" action="#">
        <i class="icon icon-search"></i>
        <input type="text" name="query" placeholder="<%- t('query_placeholder') %>" />
        <div class="help">
          <a href="#" class="btn btn-sm dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false"><span class="caret"></span></a>
          <ul class="dropdown-menu dropdown-menu-right filter-options">
            <li class="dropdown-header"><%- t('filter') %></li>
            <li><a class="dropdown-item" href="#" data-field="text"><%- t('field.text') %></a></li>
            <li><a class="dropdown-item" href="#" data-field="title"><%- t('field.title') %></a></li>
            <li><a class="dropdown-item" href="#" data-field="notes"><%- t('field.notes') %></a></li>
            <li class="dropdown-header metadata-fields"><%- t('field.metadataHeader') %></li>
            <li class="open-metadata-schema-editor"><a class="open-metadata-schema-editor" href="#"><%- t('openMetadataSchemaEditor') %></a></li>
          </ul>
        </div>
        <button class="btn btn-sm btn-primary"><%- t('button') %></button>
      </form>
      <a href="#" class="nix" title="<%- t('nix') %>">&times;</a>
    """)

    events:
      'input input[type=text]': '_onInput'
      'focus input': '_onFocus'
      'blur input': '_onBlur'
      'click a.nix': '_onClickNix'
      'click a[data-field]': '_onClickRefineBy'
      'click a.open-metadata-schema-editor': '_onClickOpenMetadataSchemaEditor'
      'submit form': '_onSubmit'

    initialize: (options) ->
      throw new Error('Must pass options.model, a Backbone.Model with a `q` attribute') if !options.model
      throw new Error('Must pass options.state, an Object with a refineDocumentListParams() method') if !options.state
      throw new Error('Must pass options.globalActions, an Object full of functions') if !options.globalActions

      @state = options.state
      @documentSet = @state.documentSet
      @globalActions = options.globalActions

      @listenTo(@model, 'change:q', @render)
      @listenTo(@documentSet, 'change:metadataSchema', @_renderMetadataFields)

      @render()

    render: ->
      @initialRender() if !@$input

      @$input.val(@model.get('q') || '')
      @_refreshChanging()
      @_refreshEmpty()

    _refreshChanging: ->
      realQ = @model.get('q') || ''
      q = @$input.val().trim()
      @$el.toggleClass('changing', q != realQ)

    _refreshEmpty: ->
      q = @$input.val().trim()
      @$el.toggleClass('empty', q.length == 0)

    _onInput: ->
      @_refreshChanging()
      @_refreshEmpty()

    _onSubmit: (e) ->
      e.preventDefault()
      q = @$input.val().trim() || null
      @state.refineDocumentListParams(q: q)

    _onClickNix: (e) ->
      e.preventDefault()
      @state.refineDocumentListParams(q: null)

    _onClickRefineBy: (e) ->
      e.preventDefault()
      field = e.target.getAttribute('data-field')
      input = @$input[0]

      # We'll be adding "text:" (for instance) to the old value, subtracting:
      # * surrounding whitespace
      # * a trailing " AND title:"
      # * the entire string "title:", if that's all the input is
      #
      # (regex may be more complex than string-replaces, but it passes the tests!
      m = /^(.*?)(?:(?:^| AND )(?:\w*|"(?:\w|\\")*"):)?$/.exec(input.value.trim())
      old_value = m[1]

      new_value = if old_value == ''
        "#{field}:"
      else
        "#{old_value} AND #{field}:"

      input.value = new_value
      @_refreshChanging()

      input.focus()

    _renderMetadataFields: ->
      $header = @$('.dropdown-header.metadata-fields')

      # Delete all metadata-field elements. They appear after the header.
      $header.nextAll('li:not(:last-child)').remove()

      # Add new ones
      fieldsHtml = @documentSet.get('metadataSchema').fields
        .map((f) => f.name)
        .map((field) => "<li><a class=\"dropdown-item\" href=\"#\" data-field=\"#{_.escape(quoteField(field))}\">#{_.escape(field)}</a></li>")
        .join('')

      $header.after(fieldsHtml)

    _onClickOpenMetadataSchemaEditor: (e) ->
      e.preventDefault()
      @globalActions.openMetadataSchemaEditor()

    _onFocus: (e) ->
      e.target.form.classList.add('focus')

    _onBlur: (e) ->
      e.target.form.classList.remove('focus')

    initialRender: ->
      html = @template(t: t)
      @$el.html(html)
      @$input = @$('input')
      @_renderMetadataFields()
