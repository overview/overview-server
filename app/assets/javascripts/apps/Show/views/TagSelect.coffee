define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
  'select2'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagSelect')

  class TagSelectView extends Backbone.View
    tagName: 'form'
    attributes:
      method: 'get'
      action: '#'
      class: 'tag-select'

    templates:
      main: _.template('''
        <form class="form-inline">
          <select class="form-control" name="tag-cid" value="">
            <option value="all" <%= selected === 'all' ? 'selected' : '' %>><%- t('all') %></option>
            <option value="untagged" <%= selected === 'untagged' ? 'selected' : '' %>><%- t('untagged') %></option>
            <% if (allTags.length > 0) { %>
              <optgroup label="<%- t('group.all') %>">
                <% allTags.forEach(function(tag) { %>
                  <option value="<%- tag.cid %>" <%= selected === tag ? 'selected' : '' %>><%- tag.get('name') %></option>
                <% }); %>
              </optgroup>
            <% } %>
          </select>
        </form>
        <a href="#" class="organize"><%- t('organize') %></a>
      ''')
      option: _.template('''
        <span class="tag" style="background-color: <%- tag.get('color') %>">
          <span class="name"><%- tag.get('name') %></span>
        </span>
      ''')

    events:
      'change select': '_onChangeSelect'
      'click .organize': '_onClickOrganize'

    initialize: (options) ->
      throw 'Must set options.collection, a Tag Collection' if !@collection
      throw 'Must set options.state, a State' if !@options.state

      @state = options.state
      @_idToLastHighlight = {} # cid -> Date

      @listenTo(@collection, 'add remove reset change', @render)
      @listenTo(@state, 'change:documentListParams', @_onHighlight)

      @render()

    _onHighlight: (__, params) ->
      for tagId in (params.tags || [])
        @_idToLastHighlight[tagId] = new Date()
      @render()

    render: ->
      selected = if (params = @state.get('documentListParams')?.toJSON())
        if (selectedId = params.tags?[0])
          @collection.get(selectedId) || 'all'
        else if params.tagged == false # false != null
          'untagged'
        else
          'all'
      else
        'all'
      allTags = @collection.sortBy('name')
      html = @templates.main(t: t, allTags: allTags, mostRecentTags: [], selected: selected)
      @$el.html(html)

      @_addSelect2()

    _onChangeSelect: (e) ->
      value = e.target.value

      switch value
        when 'all'
          @state.resetDocumentListParams().all()
        when 'untagged'
          @state.resetDocumentListParams().byUntagged()
        else
          tag = @collection.get(value)
          @state.resetDocumentListParams().byTag(tag) if tag

    _onClickOrganize: (e) ->
      e.preventDefault()
      @trigger('organize-clicked')

    # Fire-and-forget: make the select box pretty
    _addSelect2: ->
      renderOption = (option) =>
        tag = @collection.get(option.id)
        if tag?
          html = @templates.option(tag: tag)
          $(html)
        else
          $('<span class="special-tag"></span>')
            .text(option.text)
            .attr('data-special-tag-value', option.id)
      $select = @$('select')

      $.fn.select2.amd.require [
        'select2/utils'
        'select2/dropdown'
        'select2/dropdown/attachContainer'
        'select2/results'
      ], (Utils, DropdownAdapter, AttachContainer, ResultsAdapter) ->
        dropdownAdapter = Utils.Decorate(
          DropdownAdapter,
          AttachContainer
        )
        dropdownAdapter.prototype.bind = ->

        AvoidDataOnDestroy = (->
          # We re-render our <select> when the value changes. The value changes
          # in our change handler. This combination breaks the Results. Nix the
          # error by avoiding $.data() when we aren't in the DOM.

          Adapter = (decorated, args...) ->
            decorated.apply(@, args)

          Adapter.prototype.setClasses = (decorated, args...) ->
            if $.contains(document, @$element)
              decorated.apply(@, args)

          Adapter
        )()

        resultsAdapter = Utils.Decorate(ResultsAdapter, AvoidDataOnDestroy)

        $select
          .select2
            closeOnSelect: false
            templateResult: renderOption
            templateSelection: renderOption
            dropdownAdapter: dropdownAdapter
            resultsAdapter: resultsAdapter
            debug: true
