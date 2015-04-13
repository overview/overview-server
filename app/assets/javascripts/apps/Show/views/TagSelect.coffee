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
    getClass: -> 'tag untagged'
    getStyle: -> ''
    get: -> t('untagged') # always just the name

  # Lets the user select a Tag.
  #
  # Calls:
  # * state.resetDocumentListParams().byTag(tag)
  # * state.resetDocumentListParams().byUntagged()
  # * state.resetDocumentListParams().all()
  #
  # Emits `organize-clicked`
  class TagSelectView extends Backbone.View
    tagName: 'form'
    attributes:
      method: 'get'
      action: '#'
      class: 'tag-select'

    templates:
      main: _.template('''
        <div class="input-group">
          <span class="input-group-addon"><i class="icon icon-tag"></i></span>
          <input
            class="form-control <%- tag && tag.getClass() || '' %>"
            style="<%- tag && tag.getStyle() || '' %>"
            autocomplete="off"
            type="text"
            name="tag"
            placeholder="<%- t('placeholder') %>"
            value="<%- tag && tag.get('name') || '' %>"
          />
        </div>
        <div class="expanded">
        </div>
      ''')

      expanded: _.template('''
        <ul class="tags">
          <% tags.forEach(function(tag) { %>
            <li>
              <a href="#" tabindex="-1" data-cid="<%- tag.cid %>">
                <span class="<%- tag.getClass() %>" style="<%- tag.getStyle() %>">
                  <span class="name">&nbsp;</span>
                </span>
                <span class="name">
                  <% if (highlight) { %>
                    <u><%- tag.get('name').substring(0, highlight.length) %></u
                  ><% } %><%- tag.get('name').substring(highlight.length) %>
                </span>
              </a>
            </li>
          <% }); %>
        </ul>
        <% if (showAll || showUntagged || showOrganize) { %>
          <ul class="actions">
            <% if (showAll) { %>
              <li class="all">
                <a href="#" tabindex="-1" class="all">
                  <span class="name"><%- t('all') %></span>
                </a>
              </li>
            <% } %>
            <% if (showUntagged) { %>
              <li class="untagged">
                <a href="#" tabindex="-1" class="untagged">
                  <span class="name"><%- t('untagged') %></span>
                </a>
              </li>
            <% } %>
            <% if (showOrganize) { %>
              <li class="organize">
                <a href="#" tabindex="-1" class="organize">
                  <span class="name"><%- t('organize') %></span>
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

    initialize: (options) ->
      throw 'Must set options.collection, a Tag Collection' if !@collection
      throw 'Must set options.state, a State' if !@options.state

      @state = options.state

      @listenTo(@collection, 'change', @render) # handle tag name change
      @listenTo(@state, 'change:documentListParams', @render)
      @render()

    render: ->
      allTags = @collection.toArray().sort(compareTags)

      params = @state.get('documentListParams')?.toJSON()
      tag = if (tagId = params?.tags?[0])?
        @collection.get(tagId)
      else if params?.tagged == false
        Untagged

      html = @templates.main(t: t, tag: tag)
      @$el.html(html)

      @ui =
        input: @$('input')
        expanded: @$('.expanded')

    _renderExpanded: (search) ->
      search ||= ''

      tags = @collection
        .filter((tag) -> fuzzyContains(tag.get('name'), search))
        .sort(compareTags)

      showUntagged = fuzzyContains(t('untagged'), search)
      showAll = !search
      showOrganize = !search

      html = @templates.expanded
        t: t
        tags: tags
        highlight: search
        showAll: showAll
        showUntagged: showUntagged
        showOrganize: showOrganize
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
      console.log(e)
      e.preventDefault()
      @_activateLink(e.currentTarget)

    _activateLink: (a) ->
      console.log(a)
      $a = $(a)
      if $a.hasClass('untagged')
        @state.resetDocumentListParams().byUntagged()
      else if $a.hasClass('organize')
        @trigger('organize-clicked')
      else if $a.hasClass('all')
        @state.resetDocumentListParams().all()
      else
        cid = $a.attr('data-cid')
        tag = @collection.get(cid)
        console.log(cid, tag)
        if tag?
          @state.resetDocumentListParams().byTag(tag)

      @close() # We only need this for organize-tags, but what the heck
