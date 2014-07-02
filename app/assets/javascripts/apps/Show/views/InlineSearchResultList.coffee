define [ 'jquery', 'underscore', 'backbone', 'i18n', 'bootstrap-dropdown' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.Tree.show.InlineSearchResultList')

  # A list of inline search results
  #
  # Parameters:
  #
  # * collection, a Backbone.Collection of SearchResults
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
  class InlineSearchResultList extends Backbone.View
    id: 'search-result-list'

    events:
      'click li.search-result': '_onClickSearchResult'
      'click .dropdown-toggle': '_onClickDropdownToggle'
      'click a.create-tag:not(.disabled)': '_onClickCreateTag'
      'input input[type=text]': '_onInput'
      'submit form': '_onSubmit'

    templates:
      dropdownContents: _.template("""
        <% _.each(searchResults, function(searchResult) { %>
          <li
              class="search-result state-<%- (searchResult.get('state') || 'InProgress').toLowerCase() %>"
              data-cid="<%- searchResult.cid %>">
            <a href="#"><%- searchResult.get('query') %></a>
          </li>
        <% }); %>
        """)

      main: _.template("""
        <form method="post" action="#" class="form-inline input-group">
          <input
            class="input-sm form-control"
            type="text"
            name="query"
            placeholder="<%- t('query_placeholder') %>"
            />
          <%= window.csrfTokenHtml %>
          <span class="input-group-btn">
            <input type="submit" value="<%- t('search') %>" class="btn" />
            <button class="btn dropdown-toggle" data-toggle="dropdown">
              <span class="caret"></span>
            </button>
            <ul class="dropdown-menu">
            </ul>
          </span>
        </form>
        <a
          class="create-tag"
          style="display:none;"
          ><i class="overview-icon-tag"></i><%- t('create_tag') %></a>
        """)

    initialize: ->
      throw 'Must set collection, a SearchResults' if !@collection
      throw 'Must set options.state, a State' if !@options.state

      @state = @options.state

      @listenTo(@state, 'change:documentListParams', @render)
      @listenTo(@collection, 'change add remove reset', @render)

      @initialRender()

    _getSelectedSearchResult: ->
      if (params = @state.get('documentListParams'))? && params.type == 'searchResult'
        params.searchResult
      else
        null

    _renderCreateTag: ->
      model = @_getSelectedSearchResult()
      enabled = model? && @options.canCreateTagFromSearchResult?(model)
      @_$els.createTag
        .attr('data-cid', model?.cid || '')
        .prop('disabled', !enabled)
        .toggleClass('disabled', !enabled)
        .toggle(enabled)

    _renderDropdown: ->
      html = @templates.dropdownContents
        searchResults: @collection.models.slice(0, 15)

      @_$els.dropdown.html(html)
      @_$els.dropdownButton.toggle(@collection.length > 0)

    _setInputClass: (newClass) ->
      @lastClass ?= null
      if newClass != @lastClass
        if @lastClass?
          @_$els.input.removeClass(@lastClass)
        if newClass?
          @_$els.input.addClass(newClass)
        @lastClass = newClass

    _renderInput: ->
      model = @_getSelectedSearchResult()

      if model
        @_setInputClass("state-#{(model.get('state') || 'InProgress').toLowerCase()}")
        @_$els.input
          .prop('value', model.get('query'))
      else
        @_setInputClass(null)
        @_$els.input
          .prop('value', '')

    initialRender: ->
      selectedSearchResult = @_getSelectedSearchResult()

      html = @templates.main
        t: t

      @$el.html(html)

      @_$els =
        createTag: @$('a.create-tag')
        dropdownButton: @$('button.dropdown-toggle')
        dropdown: @$('.dropdown-menu')
        input: @$('input[name=query]')

      @render()

      this

    render: ->
      @_renderCreateTag()
      @_renderDropdown()
      @_renderInput()

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
        .fadeOut()
      @trigger('create-tag-clicked', model)

    _onInput: (e) ->
      @_setInputClass('editing')

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
