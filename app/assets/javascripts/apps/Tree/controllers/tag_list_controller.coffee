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
      log('added tag', "#{tag_to_short_string(tag)} to #{state.get('selection').to_string()}")
      cache.addTagToSelection(tag, state.get('selection'))
        .done(-> cache.refresh_tagcounts(tag))
      state.set('taglike', tag)

    view.on 'remove-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('removed tag', "#{tag_to_short_string(tag)} from #{state.get('selection').to_string()}")
      cache.removeTagFromSelection(tag, state.get('selection'))
        .done(-> cache.refresh_tagcounts(tag))
      state.set('taglike', tag)

    view.on 'name-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('selected tag', "#{tag_to_short_string(tag)}")
      state.set
        selection: new Selection({ tags: [tag.id] }) # even if id is negative
        taglike: tag

    view.on 'create-submitted', (name) ->
      tag = { name: name }
      log('created tag', "#{tag_to_short_string(tag)} on #{state.get('selection').to_string()}")
      tag = cache.add_tag(tag)
      cache.create_tag(tag)
      cache.addTagToSelection(tag, state.get('selection'))
        .done(-> cache.refresh_tagcounts(tag))
      state.set('taglike', tag)

    view.on 'organize-clicked', ->
      TagDialogController(tag_store, cache)

    view.on 'untagged-clicked', ->
      tag = { id: 0, name: 'untagged' }
      state.set
        selection: new Selection({ tags: [0] }) # even if id is negative
        taglike: tag


    { view: view }
