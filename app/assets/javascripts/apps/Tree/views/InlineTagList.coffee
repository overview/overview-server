define [ 'jquery', 'underscore', 'backbone' ], ($, _, Backbone) ->
  # FIXME i18n

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
    events:
      'click a.tag-name': '_onClickName'
      'click a.tag-add': '_onClickAdd'
      'click a.tag-remove': '_onClickRemove'
      'click a.organize': '_onClickOrganize'
      'submit form': '_onSubmit'

    template: _.template("""
      <ul class="btn-toolbar">
        <% collection.each(function(tag) { %>
          <li
              class="btn-group <%- (selectedTag && tag.cid == selectedTag.cid) ? 'selected' : '' %>"
              data-cid="<%- tag.cid %>"
              style="background-color:<%- tag.get('color') %>">
            <a class="btn tag-name"><%- tag.get('name') %></a>
            <a class="btn tag-add" title="add tag to selection"><i class="icon-plus"></i></a>
            <a class="btn tag-remove" title="remove tag from selection"><i class="icon-minus"></i></a>
          </li>
        <% }); %>
        <li class="btn-group">
          <form method="post" action="#" class="input-append">
            <input type="text" name="tag_name" placeholder="tag name" class="input-mini" />
            <input type="submit" value="Create new tag" class="btn" />
          </form>
        </li>
        <li class="btn-group">
          <a class="btn organize" href="#">organize tags…</a>
        </li>
      </ul>
    """)

    initialize: ->
      throw 'Must set collection, a Backbone.Collection of tag models' if !@collection
      throw 'Must pass options.tagIdToModel, a function mapping id to Backbone.Model' if !@options.tagIdToModel
      throw 'Must set options.state, a State' if !@options.state

      @tagIdToModel = @options.tagIdToModel
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
      selectedTagId = @state.selection.tags[0]
      selectedTag = selectedTagId && @tagIdToModel(selectedTagId)

      html = @template({
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

    _onSubmit: (e) ->
      e.preventDefault()

      $input = @$('input[type=text]')
      name = $input.val().replace(/^\s*(.*?)\s*$/, '$1')

      existing = @collection.findWhere({ name: name })

      if existing?
        @trigger('add-clicked', existing)
      else
        @trigger('create-submitted', name)

      $input.val('')
