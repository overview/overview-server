define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.InlineTagList')

  # A list of inline tags, with controls for adding or removing them.
  #
  # Events:
  #
  # * name-clicked(tag): A tag was clicked
  # * add-clicked(tag): A tag "add" button was clicked
  # * remove-clicked(tag): A tag "remove" button was clicked
  # * create-submitted(newTagName): The user requested adding a new tag with
  #   the given name. newTagName is guaranteed not to match any tags in the
  #   collection (otherwise add-clicked will be triggered in its stead), and
  #   the name will always be trimmed of whitespace.
  Backbone.View.extend
    id: 'tag-list'

    events:
      'click a.tag-name': '_onClickName'
      'click a.tag-add': '_onClickAdd'
      'click a.tag-remove': '_onClickRemove'
      'click a.organize': '_onClickOrganize'
      'click a.untagged': '_onClickUntagged'
      'submit form': '_onSubmit'

    templates:
      model: _.template("""
        <li
            class="btn-group"
            data-cid="<%- model.cid %>"
            style="background-color:<%- model.get('color') %>">
          <a class="btn tag-name"><%- model.get('name') %></a>
          <a class="btn tag-add" title="<%- t('add') %>"><i class="overview-icon-plus"></i></a>
          <a class="btn tag-remove" title="<%- t('remove') %>"><i class="overview-icon-minus"></i></a>
        </li>
      """)

      collection: _.template("""
        <div class="label">Tags</div>
        <ul class="btn-toolbar">
          <%= collection.map(renderModel).join('') %>
          <li class="btn-group">
            <form method="post" action="#" class="input-append">
              <input type="text" name="tag_name" placeholder="tag name" class="input-small" />
              <input type="submit" value="<%- t('create') %>" class="btn" />
            </form>
          </li>
          <li class="btn-group untagged">
            <a class="btn untagged" href="#"><%- t('show_untagged') %></a>
          </li>
          <li class="btn-group">
            <a class="btn organize" href="#"><%- t('organize') %></a>
          </li>
        </ul>
      """)

    initialize: ->
      throw 'Must set collection, a Backbone.Collection of tag models' if !@collection
      throw 'Must pass options.tagIdToModel, a function mapping id to Backbone.Model' if !@options.tagIdToModel
      throw 'Must set options.state, a State' if !@options.state

      @tagIdToModel = @options.tagIdToModel
      @state = @options.state

      @listenTo(@state, 'change:documentListParams', @_onChangeDocumentListParams)
      for k, v of { change: @_onChange, add: @_onAdd, remove: @_onRemove, reset: @_onReset }
        @listenTo(@collection, k, v)
      @render()

    remove: ->
      for key, callback of @stateCallbacks
        @state.unobserve(key, callback)
      Backbone.View.prototype.remove.apply(this)

    _onChangeDocumentListParams: ->
      @_renderSelected()

    _documentListParamsToTagCid: (params) ->
      return null if !params? || params.type != 'tag'

      tag = null
      try
        tag = @tagIdToModel(params.tagId)
      catch e
        console.log(e) # FIXME Remove the proxy nonsense and use pure Backbone

      return null if !tag?

      tag.cid

    # Adds a 'selected' class to the selection (either a tag or "untagged").
    _renderSelected: ->
      @_renderTagSelected()
      @_renderUntaggedSelected()

    # Adds a 'selected' class to the selected tag's <li>
    _renderTagSelected: ->
      oldCid = @$('li.selected').attr('data-cid') ? null
      newCid = @_documentListParamsToTagCid(@state.get('documentListParams'))

      return if oldCid == newCid

      if oldCid?
        @$('li.selected').removeClass('selected')

      if newCid?
        @$("li[data-cid='#{newCid}']").addClass('selected')

      undefined

    # Adds a 'selected' class to li.untagged
    _renderUntaggedSelected: ->
      oldUntagged = @$('li.untagged').hasClass('selected')
      newUntagged = @state.get('documentListParams')?.type == 'untagged'

      return if oldUntagged == newUntagged

      @$('li.untagged').toggleClass('selected', newUntagged)

    _onReset: ->
      @render()

    _onAdd: (model, collection, options) ->
      html = @templates.model
        model: model
        t: t

      $li = $(html)

      at = options.at ? @$('li[data-cid]').length

      @$("li:eq(#{at})").before($li)
      this

    _onRemove: (model) ->
      $li = @$("li[data-cid='#{model.cid}']")
      $li.remove()

    _onChange: (model) ->
      $li = @$("li[data-cid='#{model.cid}']")
      $li.css('background-color': model.get('color'))
      $li.find('.tag-name').text(model.get('name'))
      this

    render: ->
      renderModel = (model) => @templates.model(model: model, t: t)

      html = @templates.collection
        collection: @collection
        renderModel: renderModel
        t: t

      @$el.html(html)
      @_renderSelected()

      this

    _eventToModel: (e) ->
      cid = $(e.currentTarget).closest('[data-cid]').attr('data-cid')
      @collection.get(cid)

    _onEvent: (e, triggerEvent) ->
      e.preventDefault()
      model = @_eventToModel(e)
      @trigger(triggerEvent, model)

    _onClickName: (e) -> @_onEvent(e, 'name-clicked')
    _onClickAdd: (e) -> @_onEvent(e, 'add-clicked')
    _onClickRemove: (e) -> @_onEvent(e, 'remove-clicked')

    _onClickOrganize: (e) ->
      e.preventDefault()
      @trigger('organize-clicked')

    _onClickUntagged: (e) ->
      e.preventDefault()
      @trigger('untagged-clicked')

    _onSubmit: (e) ->
      e.preventDefault()

      $input = @$('input[type=text]')
      name = $input.val().replace(/^\s*(.*?)\s*$/, '$1')

      if !name
        $input.focus()
      else
        existing = @collection.findWhere({ name: name })

        if existing?
          @trigger('add-clicked', existing)
        else
          @trigger('create-submitted', name)

      $input.val('')
