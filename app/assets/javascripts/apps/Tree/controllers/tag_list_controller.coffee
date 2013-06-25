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

  tag_list_controller = (options) ->
    cache = options.cache
    tag_store = cache.tag_store
    state = options.state
    el = options.el

    proxy = new TagStoreProxy(tag_store)
    collection = proxy.collection
    view = new InlineTagListView({
      collection: proxy.collection
      tagIdToModel: (id) -> proxy.map(id)
      state: state
      el: el
    })

    view.on 'add-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('added tag', "#{tag_to_short_string(tag)} to #{state.selection.to_string()}")
      cache.addTagToSelection(tag, state.selection)
        .done(-> cache.refresh_tagcounts(tag))
      state.set('focused_tag', tag)

    view.on 'remove-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('removed tag', "#{tag_to_short_string(tag)} from #{state.selection.to_string()}")
      cache.removeTagFromSelection(tag, state.selection)
        .done(-> cache.refresh_tagcounts(tag))
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
      TagDialogController(tag_store, cache)

    { view: view }
