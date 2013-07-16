define [ 'jquery', 'underscore', 'backbone', 'i18n', 'bootstrap-dropdown' ], ($, _, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.InlineSearchResultList.#{key}", args...)

  # A list of inline search results
  #
  # Parameters:
  #
  # * collection, a Backbone.Collection of search-result models
  # * searchResultIdToModel, a function mapping search-result ID to search-result model
  # * state, a State
  # * (optional) canCreateTagFromSearchResult, a function mapping search-result model to boolean.
  #
  # Events:
  #
  # * search-result-clicked(searchResult): A search result was clicked
  # * create-submitted(query): The user requested adding a new search with
  #   the given query. query is guaranteed not to match any search results in
  #   the collection, and the query will always be trimmed of whitespace.
  # * create-tag-clicked(searchResult): A "create tag" button was clicked
  Backbone.View.extend
    id: 'search-result-list'

    events:
      'click li.search-result': '_onClickSearchResult'
      'click .dropdown-toggle': '_onClickDropdownToggle'
      'click a.create-tag:not(.disabled)': '_onClickCreateTag'
      'input input[type=text]': '_onInput'
      'submit form': '_onSubmit'

    template: _.template("""
      <form method="post" action="#" class="input-append">
        <%= window.csrfTokenHtml %>
        <input
          type="text"
          name="query"
          placeholder="<%- t('query_placeholder') %>"
          <% if (selectedSearchResult) { %>
            class="state-<%- (selectedSearchResult.get('state') || 'InProgress').toLowerCase() %>"
          <% } %>
          value="<%- selectedSearchResult ? selectedSearchResult.get('query') : '' %>"
          />
        <div class="btn-group">
          <input type="submit" value="<%- t('search') %>" class="btn" />
          <% if (collection.length) { %>
            <button class="btn dropdown-toggle" data-toggle="dropdown">
              <span class="caret"></span>
            </button>
            <ul class="dropdown-menu">
              <% _.each(collection.models.slice(-15), function(searchResult) { %>
                <li
                    class="search-result state-<%- (searchResult.get('state') || 'InProgress').toLowerCase() %>"
                    data-cid="<%- searchResult.cid %>">
                  <a href="#"><%- searchResult.get('query') %></a>
                </li>
              <% }); %>
            </ul>
          <% } %>
        </div>
      </form>
      <% if (canCreateTagFromSearchResult) { %>
        <a
          class="create-tag"
          data-cid="<%- selectedSearchResult.cid %>"
          ><i class="icon-tag"></i><%- t('create_tag') %></a>
      <% } %>
    """)

    initialize: ->
      throw 'Must set collection, a Backbone.Collection of search-result models' if !@collection
      throw 'Must pass options.searchResultIdToModel, a function mapping id to Backbone.Model' if !@options.searchResultIdToModel
      throw 'Must set options.state, a State' if !@options.state

      @searchResultIdToModel = @options.searchResultIdToModel
      @state = @options.state

      @stateCallbacks = {
        'selection-changed': => @render()
      }
      for key, callback of @stateCallbacks
        @state.observe(key, callback)

      @collection.on('change', => @render())
      @collection.on('add', => @render())
      @collection.on('remove', => @render())
      @collection.on('reset', => @render())
      @render()

    remove: ->
      for key, callback of @stateCallbacks
        @state.unobserve(key, callback)
      Backbone.View.prototype.remove.apply(this)

    render: ->
      selectedSearchResultId = @state.selection.searchResults[0]
      selectedSearchResult = selectedSearchResultId && @searchResultIdToModel(selectedSearchResultId)

      # If the currently-selected search result ID is changing, the selection
      # might have an ID that isn't in the collection. In that case, don't
      # render: another event is about to come and cause a render anyway
      return if selectedSearchResultId? && !selectedSearchResult

      html = @template({
        collection: @collection
        selectedSearchResult: selectedSearchResult
        canCreateTagFromSearchResult: @options.canCreateTagFromSearchResult?(selectedSearchResult)
        t: t
      })

      @$el.html(html)

    _eventToModel: (e) ->
      cid = $(e.currentTarget).closest('[data-cid]').attr('data-cid')
      @collection.get(cid)

    _onClickSearchResult: (e) ->
      e.preventDefault()
      model = @_eventToModel(e)
      @trigger('search-result-clicked', model)

    _onClickDropdownToggle: (e) ->
      e.preventDefault() # don't submit the form

    _onClickCreateTag: (e) ->
      e.preventDefault()
      model = @_eventToModel(e)
      $(e.target).closest('a')
        .addClass('disabled')
        .fadeOut(-> $(e.target).remove())
      @trigger('create-tag-clicked', model)

    _onInput: (e) ->
      e.currentTarget.className = 'editing'

    _onSubmit: (e) ->
      e.preventDefault()

      $input = @$('input[type=text]')
      query = $input.val().replace(/^\s*(.*?)\s*$/, '$1')

      existing = @collection.findWhere({ query: query })

      if existing?
        @trigger('search-result-clicked', existing)
      else if query
        @trigger('create-submitted', query)

      $input.val('')
