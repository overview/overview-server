define [
  '../models/selection'
  '../collections/TagStoreProxy'
  '../views/InlineTagList'
  './tag_form_controller'
  './TagDialogController'
  './logger'
], (Selection, TagStoreProxy, InlineTagListView, tag_form_controller, TagDialogController, Logger) ->
  log = Logger.for_component('tag_list')

  tag_to_short_string = (tag) ->
    "#{tag.id} (#{tag.name})"

  tag_list_controller = (remote_tag_list, state) ->
    proxy = new TagStoreProxy(remote_tag_list.tag_store)
    collection = proxy.collection
    view = new InlineTagListView({
      collection: proxy.collection
      tagIdToModel: (id) -> proxy.map(id)
      state: state
    })

    view.on 'add-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('added tag', "#{tag_to_short_string(tag)} to #{state.selection.to_string()}")
      remote_tag_list.add_tag_to_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.on 'remove-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('removed tag', "#{tag_to_short_string(tag)} from #{state.selection.to_string()}")
      remote_tag_list.remove_tag_from_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.on 'name-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('selected tag', "#{tag_to_short_string(tag)}")
      state.set('selection', new Selection({ tags: [tag.id] })) # even if id is negative
      state.set('focused_tag', tag)

    view.on 'create-submitted', (name) ->
      tag = { name: name }
      log('created tag', "#{tag_to_short_string(tag)} on #{state.selection.to_string()}")
      tag = remote_tag_list.cache.add_tag(tag)
      remote_tag_list.cache.create_tag(tag)
      remote_tag_list.add_tag_to_selection(tag, state.selection)
      state.set('focused_tag', tag)

    view.on 'organize-clicked', ->
      TagDialogController(remote_tag_list.tag_store, remote_tag_list.cache)

    { view: view }
