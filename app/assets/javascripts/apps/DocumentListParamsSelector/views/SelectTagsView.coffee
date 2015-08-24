define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagSelect')

  fuzzyContains = (full, partial) ->
    full = full.toLowerCase().trim()
    partial = partial.toLowerCase().trim()
    full.indexOf(partial) == 0

  compareTags = (tag1, tag2) ->
    tag1.get('name').localeCompare(tag2.get('name'))

  Untagged =
    getClass: -> 'icon icon-tag'
    getStyle: -> ''
    get: -> t('untagged') # always just the name

  # Lets the user select a Tag.
  #
  # Calls:
  # * state.refineDocumentListParams(tags: { ids: [ tag.id ] })
  # * state.refineDocumentListParams(tags: { tagged: false })
  # * state.refineDocumentListParams(tags: null)
  class SelectTagsView extends Backbone.View
    tagName: 'form'
    attributes:
      method: 'get'
      action: '#'
      class: 'tag-select'

    templates:
      main: _.template('''
        <div class="input-group input-group-sm">
          <span class="input-group-addon">
            <label
              class="<%- tag ? tag.getClass() : 'icon icon-tag' %>"
              style="<%- tag ? tag.getStyle() : '' %>"
              for="tag-select-input"
            ></label>
          </span>
          <input
            id="tag-select-input"
            class="form-control"
            autocomplete="off"
            type="text"
            name="tag"
            placeholder="<%- t('placeholder') %>"
            value="<%- tag ? tag.get('name') : '' %>"
          />
        </div>
        <div class="expanded">
        </div>
        <a href="#" class="nix">&times;</a>
      ''')

      expanded: _.template('''
        <ul class="tags">
          <% tags.forEach(function(tag) { %>
            <li>
              <a href="#" tabindex="-1" data-cid="<%- tag.cid %>">
                <span class="<%- tag.getClass() %>" style="<%- tag.getStyle() %>"></span>
                <span class="name">
                  <% if (highlight) { %>
                    <u><%- tag.get('name').substring(0, highlight.length) %></u
                  ><% } %><%- tag.get('name').substring(highlight.length) %>
                </span>
              </a>
            </li>
          <% }); %>
        </ul>
        <% if (showUntagged) { %>
          <ul class="actions">
            <% if (showUntagged) { %>
              <li class="untagged">
                <a href="#" tabindex="-1" class="untagged">
                  <span class="name"><%- t('untagged') %></span>
                </a>
              </li>
            <% } %>
          </ul>
        <% } %>
      ''')

    events:
      'focus input': '_onFocus'
      'blur input': '_onBlur'
      'input input': '_onInput'
      'keydown input': '_onKeydown'
      'mouseenter li': '_onMouseenterLi'
      'mousedown a': '_onClick' # mousedown because it comes before blur
      'mouseup a': '_onClick' # mousedown to focus; mouseup on selection
      'click a.nix': '_onClickNix'

    initialize: (options) ->
      throw 'Must set options.model, a Backbone.Model with `tags`' if !@options.model
      throw 'Must set options.tags, a Tag Collection' if !@options.tags
      throw 'Must set options.state, an Object with a refineDocumentListParams method' if !@options.state

      @state = options.state
      @tags = options.tags

      @listenTo(@tags, 'change', @render) # handle tag name change
      @listenTo(@model, 'change:tags', @render)
      @render()

    render: ->
      allTags = @tags.toArray().sort(compareTags)

      tag = if (tagId = @model.get('tags')?.ids?[0])?
        @tags.get(tagId)
      else if @model.get('tags')?.tagged == false
        Untagged

      html = @templates.main(t: t, tag: tag)
      @$el.html(html)

      @$el.toggleClass('empty', !@model.get('tags')?)

      @ui =
        input: @$('input')
        expanded: @$('.expanded')

    _renderExpanded: (search) ->
      search ||= ''

      tags = @tags
        .filter((tag) -> fuzzyContains(tag.get('name'), search))
        .sort(compareTags)

      showUntagged = fuzzyContains(t('untagged'), search)

      html = @templates.expanded
        t: t
        tags: tags
        highlight: search
        showUntagged: showUntagged
      @ui.expanded.html(html)

      @ui.tags = @$('ul.tags')

      @_highlightedIndex = null
      @_nLis = @$('li').length # both ul.tags and ul.actions

    open: ->
      return if @$el.hasClass('open')
      @$el.addClass('open')

      @_renderExpanded()

    close: ->
      return if !@$el.hasClass('open')
      @$el.removeClass('open')
      # We need to re-render: the user may have changed the input text, and we
      # want to reset it to what it was before.
      @render()

    _onFocus: -> @open()
    _onBlur: -> @close()

    # Marks one <li> as the active one. Affects Enter, Up, Down.
    _highlight: (index) ->
      # Wrap
      if index?
        index = @_nLis - 1 if index < 0
        index = 0 if index >= @_nLis

      @$('li.active').removeClass('active')
      @_highlightedIndex = index
      @$('li').eq(@_highlightedIndex).addClass('active') if @_highlightedIndex?

    _onMouseenterLi: (e) ->
      index = @$('li').index(e.currentTarget)
      @_highlight(index)

    _onInput: (e) ->
      search = $(e.currentTarget).val().trim()

      @_renderExpanded(search)
      if search
        @_highlight(0)
      else
        @_highlight(null)

    _onKeydown: (e) ->
      switch e.keyCode
        when 27 # Escape
          @close()
        when 38 # Up
          @_highlight((@_highlightedIndex ? 0) - 1)
        when 40 # Down
          @_highlight((@_highlightedIndex ? -1) + 1)
        when 13 # Enter
          @_onPressEnter(e)

    _onPressEnter: (e) ->
      e.preventDefault()
      a = @$('li').eq(@_highlightedIndex).children('a')
      @_activateLink(a)

    _onClick: (e) ->
      e.preventDefault()
      @_activateLink(e.currentTarget)

    _activateLink: (a) ->
      $a = $(a)

      tags = if $a.hasClass('untagged')
        tagged: false
      else
        cid = $a.attr('data-cid')
        tag = @tags.get(cid)
        tag? && { ids: [ tag.id ] } || null

      @state.refineDocumentListParams(tags: tags)

    _onClickNix: (e) ->
      e.preventDefault()
      @state.refineDocumentListParams(tags: null)
