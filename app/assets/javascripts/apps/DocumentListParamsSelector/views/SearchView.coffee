define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.SearchView')

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
        <div class="input-group input-group-sm">
          <span class="input-group-addon">
            <i class="icon icon-search"></i>
          </span>
          <input
            class="form-control"
            type="text"
            name="query"
            placeholder="<%- t('query_placeholder') %>"
            />
          <span class="input-group-btn">
            <button class="btn btn-primary"><%- t('button') %></button>
          </span>
        </div>
      </form>
    """)

    events:
      'input input[type=text]': '_onInput'
      'submit form': '_onSubmit'

    initialize: (options) ->
      throw new Error('Must pass options.model, a Backbone.Model with a `q` attribute') if !options.model
      throw new Error('Must pass options.state, an Object with a refineDocumentListParams() method') if !options.state

      @state = options.state

      @listenTo(@model, 'change:q', @render)

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

    initialRender: ->
      html = @template(t: t)
      @$el.html(html)
      @$input = @$('input')
