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

  # Displays a form with the current tag selection, and lets the user modify it.
  #
  # Calls:
  # * state.refineDocumentListParams(tags: [ tag.id ])
  # * state.refineDocumentListParams(tagged: false)
  # * state.refineDocumentListParams(tags: [ tag.id ], tagged: false, operation: 'any')
  # * state.refineDocumentListParams(tags: null)
  #
  # Triggers:
  # * close: when you click outside it
  #
  # Responds to:
  # * handleKeydown(HTMLEvent): the owner listens for keydown and passes it to
  #   this method.
  class SelectTagsDropdownView extends Backbone.View
    tagName: 'form'
    attributes:
      method: 'get'
      action: '#'

    templates:
      main: _.template('''
        <div class="tags"></div>
        <div class="operation">
          <div class="radio operation-any">
            <label>
              <input type="radio" name="operation" value="any" tabindex="-1" <%= operation == 'any' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
          <div class="radio operation-all">
            <label>
              <input type="radio" name="operation" value="all" tabindex="-1" <%= operation == 'all' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
          <div class="radio operation-none">
            <label>
              <input type="radio" name="operation" value="none" tabindex="-1" <%= operation == 'none' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
        </div>
      ''')

      tag: _.template('''
        <div class="checkbox">
          <label>
            <input type="checkbox" name="tag" value="<%- id %>" tabindex="-1" <%= checked && 'checked' || '' %>>
            <span class="<%- className %>" style="<%- style %>"></span>
            <span class="name"><%- name %></span>
          </label>
        </div>
      ''')

    initialize: (options) ->
      throw 'Must set options.model, a Backbone.Model with `tags`' if !options.model
      throw 'Must set options.tags, a Tag Collection' if !options.tags
      throw 'Must set options.state, an Object with a refineDocumentListParams method' if !options.state

      @state = options.state
      @tags = @_buildTags(options.tags, options.model)
      @render()

      @_initialRender(options.model.get('tagOperation') || 'any')
      @_renderOperations()

      $(document).on 'click.select-tags-dropdown', =>
        # Defer, so _setStateSelection can run if required
        window.setTimeout((=> @trigger('close')), 0)

    remove: ->
      $(document).off('click.select-tags-dropdown')
      super()

    _buildTags: (collection, model) ->
      selectedIds = {}
      (selectedIds[id] = null) for id in (model.get('tags') || [])

      tags = []
      collection.forEach (tag) ->
        if tag.id
          tags.push
            id: tag.id
            name: tag.get('name')
            className: tag.getClass()
            style: tag.getStyle()
            checked: tag.id of selectedIds

      tags.sort((tag1, tag2) -> tag1.name.localeCompare(tag2.name))

      tags.push
        id: 'untagged'
        name: t('untagged')
        className: 'tag tag-light'
        style: 'background-color: #dddddd'
        checked: model.get('tags')?.tagged == false

      tags

    events:
      'change input': '_onChange'
      'click input': '_onClickInput'
      'mouseenter div.radio, div.checkbox': '_onMouseenterDiv'
      'submit': '_onSubmit'

    _initialRender: (operation) ->
      @$el.html(@templates.main(operation: operation))

      $tagEls = for tag in @tags
        tag.$el = $(@templates.tag(tag))

      @ui =
        tags: @$('.tags')
        operation: @$('.operation')
        operations:
          any:
            container: @$('.operation-any')
            input: @$('.operation-any input')
            name: @$('.operation-any .name')
          all:
            container: @$('.operation-all')
            input: @$('.operation-all input')
            name: @$('.operation-all .name')
          none:
            container: @$('.operation-none')
            input: @$('.operation-none input')
            name: @$('.operation-none .name')

      @ui.tags.append($tagEls)

    _renderOperations: ->
      nOptions = @$el.serializeArray().filter((o) -> o.name == 'tag').length
      operation = @$('input[name=operation]:checked').val() || 'any'

      if nOptions == 0
        @ui.operation.addClass('disabled')
        # Disable the radios, so they don't take over the keyboard
        for __, obj of @ui.operations
          obj.container.addClass('disabled')
          obj.input.prop('disabled', obj.disabled = true)
      else
        @ui.operation.removeClass('disabled')
        if nOptions == 1
          for op, obj of @ui.operations
            obj.name.html(t("operation.single.#{op == 'all' && 'any' || op}_html"))
            # Support this workflow:
            #
            # 1. Select two tags
            # 2. Choose "all"
            # 3. Deselect a tag
            # 4. Select a different tag
            #
            # Expect: "all" should remain selected throughout. After step 3,
            # there should not be a prompt for "any".
            #
            # Ditto if you swap "all" with "any" in the above instructions.
            #
            # Our trick is to make "all" and "any" *appear* identical. We only
            # show the active one, and we hide the other.
            enable = {
              any: { all: false, any: true, none: true }
              all: { all: true, any: false, none: true }
              none: { all: false, any: true, none: true }
            }[operation][op]
            obj.container.toggleClass('disabled', !enable)
            obj.input.prop('disabled', obj.disabled = !enable)
        else
          for op, obj of @ui.operations
            obj.name.html(t("operation.multiple.#{op}_html"))
            obj.container.removeClass('disabled')
            obj.input.prop('disabled', obj.disabled = false)

    _onChange: ->
      @_setStateSelection()
      @_renderOperations()

    _onClickInput: (ev) ->
      # When the user clicks on the checkbox/radio itself, do not close the
      # dropdown.
      ev.stopPropagation()

    _onMouseenterDiv: (ev) ->
      $divs = @$('div.radio:not(.disabled), div.checkbox:not(.disabled)')
      @_highlight($divs.index(ev.currentTarget))

    _onSubmit: (ev) ->
      ev.preventDefault()
      @_setStateSelection()
      @_renderOperations()

    _setStateSelection: ->
      selection =
        tags: []
        tagOperation: undefined
        tagged: undefined

      for o in @$el.serializeArray()
        switch o.name
          when 'tag'
            if o.value == 'untagged'
              selection.tagged = false
            else
              selection.tags.push(parseInt(o.value, 10))
          when 'operation'
            if o.value != 'any' # 'any' is the default
              selection.tagOperation = o.value

      if selection.tags.length == 0
        selection.tags = null

      @state.refineDocumentListParams(selection)

    _highlight: (newIndex) ->
      $divs = @$('div.radio:not(.disabled), div.checkbox:not(.disabled)')

      newIndex = 0 if newIndex >= $divs.length
      newIndex = -1 if newIndex < 0

      if @_highlightedIndex?
        $divs.eq(@_highlightedIndex).removeClass('hover')

      @_highlightedIndex = newIndex
      $divs.eq(@_highlightedIndex).addClass('hover')

    _toggleHighlightedElement: ->
      return if !@_highlightedIndex?

      $input = @$('input:not(:disabled)').eq(@_highlightedIndex)

      if $input.attr('type') == 'checkbox'
        $input.prop('checked', !$input.prop('checked'))
      else if $input.attr('type') == 'radio'
        $input.prop('checked', true)

      @_setStateSelection()
      @_renderOperations()

    handleKeydown: (ev) ->
      handled = true

      switch ev.keyCode
        when 27 # Escape: close
          @trigger('close')
        when 38 # Up: Move up
          @_highlight((@_highlightedIndex ? 0) - 1)
        when 40 # Down: Move down
          @_highlight((@_highlightedIndex ? -1) + 1)
        when 32 # Space: Toggle current element
          @_toggleHighlightedElement()
        when 13 # Enter: Toggle current element and close
          @_toggleHighlightedElement()
          # The parent will close it
        else
          handled = false

      ev.stopPropagation() if handled
