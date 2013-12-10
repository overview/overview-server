define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.InlineTagList.#{key}", args...)

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

    template: _.template("""
      <div class="label">Tags</div>
      <ul class="btn-toolbar">
        <% collection.each(function(tag) { %>
          <li
              class="btn-group <%- (selectedTag && tag.cid == selectedTag.cid) ? 'selected' : '' %>"
              data-cid="<%- tag.cid %>"
              style="background-color:<%- tag.get('color') %>">
            <a class="btn tag-name"><%- tag.get('name') %></a>
            <a class="btn tag-add" title="<%- t('add') %>"><i class="overview-icon-plus"></i></a>
            <a class="btn tag-remove" title="<%- t('remove') %>"><i class="overview-icon-minus"></i></a>
          </li>
        <% }); %>
        <li class="btn-group">
          <form method="post" action="#" class="input-append">
            <input type="text" name="tag_name" placeholder="tag name" class="input-small" />
            <input type="submit" value="<%- t('create') %>" class="btn" />
          </form>
        </li>
        <li class="btn-group">
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

      @listenTo(@state, 'change:selection', => @render())
      @listenTo(@collection, 'change add remove reset', => @render())
      @render()

    remove: ->
      for key, callback of @stateCallbacks
        @state.unobserve(key, callback)
      Backbone.View.prototype.remove.apply(this)

    render: ->
      selection = @state.get('selection')
      selectedTagId = selection.tags[0]
      selectedTag = undefined
      if selectedTagId? && selectedTagId != 0
        try
          selectedTag = @tagIdToModel(selectedTagId)
        catch e
          console.log(e) # FIXME Remove the proxy nonsense and use pure Backbone

      html = @template({
        t: t
        collection: @collection
        selectedTag: selectedTag
      })

      @$el.html(html)

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

      if name
        existing = @collection.findWhere({ name: name })

        if existing?
          @trigger('add-clicked', existing)
        else
          @trigger('create-submitted', name)

      $input.val('')
