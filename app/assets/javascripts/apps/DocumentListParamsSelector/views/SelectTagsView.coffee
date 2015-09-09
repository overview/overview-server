define [
  'underscore'
  'backbone'
  './SelectTagsDropdownView'
  'i18n'
], (_, Backbone, SelectTagsDropdownView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagSelect')

  # Displays the tag selection, and lets the user open a SelectTagsDropdownView
  # to modify it.
  class SelectTagsView extends Backbone.View
    attributes:
      class: 'select-tags'

    templates:
      main: _.template('''
        <div class="collapsed">
          <a href="#" class="description"></a>
          <a href="#" class="nix">&times;</a>
        </div>
        <div class="expanded"></div>
      ''')

      tag: _.template('''
        <span class="selected-tag" data-tag-id="<%- tag.id %>">
          <span class="<%- tag.className %>" style="<%- tag.style %>"></span>
          <span class="name"><%- tag.name %></span>
        </span>
      ''')

    events:
      'click .description': '_onClickDescription'
      'click a.nix': '_onClickNix'
#      'mouseenter li': '_onMouseenterLi'
#      'mouseup li a': '_onClick' # mousedown to focus; mouseup on selection. Otherwise, this is a click.

    initialize: (options) ->
      throw 'Must set options.model, a Backbone.Model with `tags`' if !@options.model
      throw 'Must set options.tags, a Tag Collection' if !@options.tags
      throw 'Must set options.state, an Object with a refineDocumentListParams method' if !@options.state

      @state = options.state
      @tags = options.tags

      @listenTo(@tags, 'change', @render) # handle tag name change
      @listenTo(@model, 'change:tags', @render)
      @_initialRender()

    _initialRender: ->
      @$el.html(@templates.main())

      @ui =
        description: @$('.collapsed .description')
        expanded: @$('.expanded')

      @render()

    _renderDescription: ->
      selection =
        ids: @model.get('tags')?.ids || []
        operation: @model.get('tags')?.operation || 'any'
        tagged: @model.get('tags')?.tagged

      tags = []

      for tagId in selection.ids
        if (tag = @tags.get(tagId))?
          tags.push
            id: tag.id
            name: tag.get('name')
            className: tag.getClass()
            style: tag.getStyle()

      tags.sort((tag1, tag2) -> tag1.name.localeCompare(tag2.name))

      if selection.tagged?
        tags.push
          id: selection.tagged && 'tagged' || 'untagged'
          name: selection.tagged && t('tagged') || t('untagged')
          className: 'tag tag-light'
          style: 'background-color: #dddddd'

      selection.operation = 'any' if tags.length == 1 && selection.operation == 'all'

      if tags.length == 0
        @ui.description
          .addClass('placeholder')
          .text(t('placeholder'))
          .prepend('<i class="icon icon-tag"></i>')
      else
        key = "description.#{tags.length == 1 && 'single' || 'multiple'}.#{selection.operation}_html"
        htmls = (@templates.tag(tag: tag) for tag in tags)
        @ui.description
          .removeClass('placeholder')
          .html(t(key, "<span class='tags'>#{htmls.join('')}</span>"))

    render: ->
      @_renderDescription()
      @_renderEmpty()

    _renderEmpty: ->
      selection = @model.get('tags') || {}
      nOptions = (selection.tagged? && 1 || 0) + (selection.ids || []).length
      @$el.toggleClass('empty', nOptions == 0)

    _onClickDescription: (e) ->
      e.preventDefault()

      if !@child?
        e.stopPropagation() # SelectTagsDropdownView will listen for document.click
        @child = new SelectTagsDropdownView(model: @model, tags: @tags, state: @state)
        @ui.expanded.append(@child.el)
        @$el.addClass('open')
        @listenToOnce @child, 'close', =>
          @$el.removeClass('open')
          @child.remove()
          @child = null

    _onClickNix: (e) ->
      e.preventDefault()
      @state.refineDocumentListParams(tags: null)

#      allTags = @tags.toArray().sort(compareTags)
#
#      tag = if (tagId = @model.get('tags')?.ids?[0])?
#        @tags.get(tagId)
#      else if @model.get('tags')?.tagged == false
#        Untagged
#
#      html = @templates.main(t: t, tag: tag)
#      @$el.html(html)
#
#      @$el.toggleClass('empty', !@model.get('tags')?)
#
#      @ui =
#        description: @$('.collapsed .description')
#        expanded: @$('.expanded')
#        operations:
#          ul: @$('ul.operation')
#          any: @$('li[data-tag-operation=any] a')
#          all: @$('li[data-tag-operation=all] a')
#          none: @$('li[data-tag-operation=none] a')
#
#      @_renderDescription()
#
#    _renderOperations: ->
#      selection = @state.get('documentList')?.params?.tags
#
#      nOptions = selection?.ids?.length + (selection?.tagged? && 1 || 0)
#
#      operation = selection?.operation || 'any'
#
#      if nOptions == 0
#        @ui.operations.ul.hide()
#      else if nOptions == 1
#        @ui.operations.ul.show()
#        @ui.operations.any.html(t('operation.single.any_html'))
#        @ui.operations.all.hide()
#        @ui.operations.none.html(t('operation.single.none_html'))
#        operation = 'any' if operation == 'all'
#      else
#        @ui.operations.ul.show()
#        @ui.operations.any.html(t('operation.multiple.any_html'))
#        @ui.operations.all.html(t('operation.multiple.all_html'))
#        @ui.operations.none.html(t('operation.multiple.none_html'))
#
#      for op in [ 'any', 'all', 'none' ]
#        @ui.operations[op].parent().toggleClass('selected', op == operation)
#
#      null
#
#    _renderExpanded: (search) ->
#      search ||= ''
#
#      tags = @tags
#        .filter((tag) -> fuzzyContains(tag.get('name'), search))
#        .sort(compareTags)
#
#      showUntagged = fuzzyContains(t('untagged'), search)
#
#      html = @templates.expanded
#        t: t
#        tags: tags
#        highlight: search
#        showUntagged: showUntagged
#      @ui.expanded.html(html)
#
#      @ui.tags = @$('ul.tags')
#
#      @_highlightedIndex = null
#      @_nLis = @$('li').length # both ul.tags and ul.actions
#      @_renderOperations()
#
#    open: (e) ->
#      return if @$el.hasClass('open')
#      @$el.addClass('open')
#
#      @_renderExpanded()
#
#    close: ->
#      return if !@$el.hasClass('open')
#      @$el.removeClass('open')
#      # We need to re-render: the user may have changed the input text, and we
#      # want to reset it to what it was before.
#      @render()
#
#    # Marks one <li> as the active one. Affects Enter, Up, Down.
#    _highlight: (index) ->
#      # Wrap
#      if index?
#        index = @_nLis - 1 if index < 0
#        index = 0 if index >= @_nLis
#
#      @$('li.active').removeClass('active')
#      @_highlightedIndex = index
#      @$('li').eq(@_highlightedIndex).addClass('active') if @_highlightedIndex?
#
#    _onMouseenterLi: (e) ->
#      index = @$('li').index(e.currentTarget)
#      @_highlight(index)
#
#    _onMousedownDescription: (e) ->
#      e.preventDefault()
#
#      @open()
#
#    _onInput: (e) ->
#      search = $(e.currentTarget).val().trim()
#
#      @_renderExpanded(search)
#      if search
#        @_highlight(0)
#      else
#        @_highlight(null)
#
#    _onKeydown: (e) ->
#      switch e.keyCode
#        when 27 # Escape
#          @close()
#        when 38 # Up
#          @_highlight((@_highlightedIndex ? 0) - 1)
#        when 40 # Down
#          @_highlight((@_highlightedIndex ? -1) + 1)
#        when 13 # Enter
#          @_onPressEnter(e)
#
#    _onPressEnter: (e) ->
#      e.preventDefault()
#      a = @$('li').eq(@_highlightedIndex).children('a')
#      @_activateLink(a)
#
#    _onClick: (e) ->
#      e.preventDefault()
#      @_activateLink(e.currentTarget)
#
#    _activateLink: (a) ->
#      $a = $(a)
#
#      tags = if $a.hasClass('untagged')
#        tagged: false
#      else if $a.hasAttr('data-cid')
#        cid = $a.attr('data-cid')
#        tag = @tags.get(cid)
#        tag? && { ids: [ tag.id ] } || null
#
#      @state.refineDocumentListParams(tags: tags)
#      @close() # Gotta do this manually on Firefox
