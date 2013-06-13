define [ 'jquery', 'underscore', 'backbone', 'bootstrap-dropdown' ], ($, _, Backbone) ->
  # FIXME i18n

  # A list of inline search results
  #
  # Events:
  #
  # * search-result-clicked(searchResult): A search result was clicked
  # * create-submitted(query): The user requested adding a new search with
  #   the given query. query is guaranteed not to match any search results in
  #   the collection, and the query will always be trimmed of whitespace.
  Backbone.View.extend
    id: 'search-result-list'

    events:
      'click li.search-result': '_onClickSearchResult'
      'click .dropdown-toggle': '_onClickDropdownToggle'
      'input input[type=text]': '_onInput'
      'submit form': '_onSubmit'

    template: _.template("""
      <form method="post" action="#" class="input-append">
        <input
          type="text"
          name="query"
          placeholder="Search all documents"
          <% if (selectedSearchResult) { %>
            class="state-<%- (selectedSearchResult.get('state') || 'InProgress').toLowerCase() %>"
          <% } %>
          value="<%- selectedSearchResult ? selectedSearchResult.get('query') : '' %>"
          />
        <div class="btn-group dropup">
          <input type="submit" value="Search" class="btn" />
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

    _onInput: (e) ->
      e.currentTarget.className = 'editing'

    _onSubmit: (e) ->
      e.preventDefault()

      $input = @$('input[type=text]')
      query = $input.val().replace(/^\s*(.*?)\s*$/, '$1')

      existing = @collection.findWhere({ query: query })

      if existing?
        @trigger('search-result-clicked', existing)
      else
        @trigger('create-submitted', query)

      $input.val('')
