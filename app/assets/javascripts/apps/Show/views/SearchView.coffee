define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.SearchView')

  class SearchView extends Backbone.View
    template: _.template("""
      <form method="post" action="#" class="form-inline input-group">
        <input
          class="input-sm form-control"
          type="text"
          name="query"
          placeholder="<%- t('query_placeholder') %>"
          />
        <%= window.csrfTokenHtml %>
        <span class="input-group-btn">
          <button class="btn" type="submit"><%- t('search') %></button>
        </span>
      </form>
    """)

    events:
      'input input[type=text]': '_onInput'
      'submit form': '_onSubmit'

    initialize: (options) ->
      throw 'Must pass options.state, a State' if !options.state

      @state = options.state

      @listenTo(@state, 'change:documentListParams', @render)

      @render()

    render: ->
      @initialRender() if !@$input

      @$input.val(@state.get('documentListParams').q || '')
      @_refreshChanging()
      @_refreshEmpty()

    _refreshChanging: ->
      realQ = @state.get('documentListParams').q || ''
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
      q = @$input.val().trim()
      @trigger('search', q)

    initialRender: ->
      html = @template(t: t)
      @$el.html(html)
      @$input = @$('input')
