define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.SearchView')

  # Prompts the user to enter a search; displays the active search.
  #
  # Listens to:
  # * state.change('documentListParams')
  #
  # Calls:
  # * state.resetDocumentListParams().byQ()
  # * state.resetDocumentListParams().all()
  #
  # Triggers: nothing
  class SearchView extends Backbone.View
    template: _.template("""
      <form method="post" action="#">
        <div class="input-group">
          <input
            class="form-control"
            type="text"
            name="query"
            placeholder="<%- t('query_placeholder') %>"
            />
          <%= window.csrfTokenHtml %>
          <span class="input-group-btn">
            <button class="btn btn-default" type="submit"><i class="icon icon-search"></i></button>
          </span>
        </div>
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

      @$input.val(@state.get('documentListParams').params.q || '')
      @_refreshChanging()
      @_refreshEmpty()

    _refreshChanging: ->
      realQ = @state.get('documentListParams').params.q || ''
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
      if q
        @state.resetDocumentListParams().byQ(q)
      else
        @state.resetDocumentListParams().all()

    initialRender: ->
      html = @template(t: t)
      @$el.html(html)
      @$input = @$('input')
