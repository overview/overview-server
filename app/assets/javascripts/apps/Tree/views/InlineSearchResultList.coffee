define [ 'jquery', 'underscore', 'backbone' ], ($, _, Backbone) ->
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
      'click a.search-result': '_onClickSearchResult'
      'submit form': '_onSubmit'

    template: _.template("""
      <div class="label">Search results</div>
      <ul class="btn-toolbar">
        <% collection.each(function(searchResult) { %>
          <li
              class="<%- (selectedSearchResult && searchResult.cid == selectedSearchResult.cid) ? 'selected' : '' %>"
              data-cid="<%- searchResult.cid %>">
            <button type="button" class="btn">
              <span class="message"><%- searchResult.get('query') %></span>
            </button>
          </li>
        <% }); %>
        <li class="btn-group">
          <form method="post" action="#" class="input-append">
            <input type="text" name="query" placeholder="Search terms" class="input-small" />
            <input type="submit" value="Search all documents" class="btn" />
          </form>
        </li>
      </ul>
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

    _onSubmit: (e) ->
      e.preventDefault()

      $input = @$('input[type=text]')
      query = $input.val().replace(/^\s*(.*?)\s*$/, '$1')

      existing = @collection.findWhere({ query: query })

      if !existing?
        @trigger('create-submitted', query)

      $input.val('')
