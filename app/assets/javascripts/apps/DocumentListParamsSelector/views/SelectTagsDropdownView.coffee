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
  # * state.refineDocumentListParams(tags: { ids: [ tag.id ] })
  # * state.refineDocumentListParams(tags: { tagged: false })
  # * state.refineDocumentListParams(tags: { ids: [ tag.id ], tagged: false, operation: 'any' })
  # * state.refineDocumentListParams(tags: null)
  #
  # Triggers:
  # * close: when you click outside it
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
              <input type="radio" name="operation" value="any" <%= operation == 'any' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
          <div class="radio operation-all">
            <label>
              <input type="radio" name="operation" value="all" <%= operation == 'all' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
          <div class="radio operation-none">
            <label>
              <input type="radio" name="operation" value="none" <%= operation == 'none' && 'checked' || '' %>>
              <span class="name"></span>
            </label>
          </div>
        </div>
      ''')

      tag: _.template('''
        <div class="checkbox">
          <label>
            <input type="checkbox" name="tag" value="<%- id %>" <%= checked && 'checked' || '' %>>
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

      @_initialRender(options.model.get('tags')?.operation || 'any')
      @_renderOperations()

      $(document).on 'click.select-tags-dropdown', =>
        # Defer, so _setStateSelection can run if required
        window.setTimeout((=> @trigger('close')), 0)

    remove: ->
      $(document).off('click.select-tags-dropdown')
      super()

    _buildTags: (collection, model) ->
      selectedIds = {}
      (selectedIds[id] = null) for id in (model.get('tags')?.ids || [])

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
          obj.input.prop('disabled', true)
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
            obj.input.prop('disabled', !enable)
        else
          for op, obj of @ui.operations
            obj.name.html(t("operation.multiple.#{op}_html"))
            obj.container.removeClass('disabled')
            obj.input.prop('disabled', false)

    _onChange: ->
      @_setStateSelection()
      @_renderOperations()

    _onClickInput: (ev) ->
      # When the user clicks on the checkbox/radio itself, do not close the
      # dropdown.
      ev.stopPropagation()

    _onSubmit: (ev) ->
      ev.preventDefault()
      @_setStateSelection()
      @_renderOperations()

    _setStateSelection: ->
      selection =
        ids: []
        operation: 'any'
        tagged: undefined

      for o in @$el.serializeArray()
        switch o.name
          when 'tag'
            if o.value == 'untagged'
              selection.tagged = false
            else
              selection.ids.push(parseInt(o.value, 10))
          when 'operation'
            @operation = selection.operation = o.value

      if selection.ids.length == 0 && !selection.tagged?
        selection = null

      @state.refineDocumentListParams(tags: selection)
