define [
  '../models/selection'
  '../views/tag_list_view'
  './tag_form_controller'
  './TagDialogController'
  './logger'
], (Selection, TagListView, tag_form_controller, TagDialogController, Logger) ->
  log = Logger.for_component('tag_list')

  tag_to_short_string = (tag) ->
    "#{tag.id} (#{tag.name})"

  tag_list_controller = (div, remote_tag_list, state) ->
    view = new TagListView(div, remote_tag_list, state)

    view.observe 'edit-clicked', (tag) ->
      log('clicked edit tag', "#{tag.id}")
      tag_form_controller(tag, remote_tag_list.cache, state)

    view.observe 'add-clicked', (tag) ->
      log('added tag', "#{tag_to_short_string(tag)} to #{state.selection.to_string()}")
      remote_tag_list.add_tag_to_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.observe 'remove-clicked', (tag) ->
      log('removed tag', "#{tag_to_short_string(tag)} from #{state.selection.to_string()}")
      remote_tag_list.remove_tag_from_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.observe 'tag-clicked', (tag) ->
      log('selected tag', "#{tag_to_short_string(tag)}")
      state.set('selection', new Selection({ tags: [tag.id] })) # even if id is negative
      state.set('focused_tag', tag)

    view.observe 'create-submitted', (tag) ->
      log('created tag', "#{tag_to_short_string(tag)} on #{state.selection.to_string()}")
      tag = remote_tag_list.create_tag(tag.name)
      remote_tag_list.add_tag_to_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.observe 'organize-clicked', ->
      TagDialogController(remote_tag_list.tag_store, remote_tag_list.cache)
