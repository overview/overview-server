define [
  '../models/DocumentListParams'
  '../collections/TagStoreProxy'
  '../views/InlineTagList'
  './tag_form_controller'
  './TagDialogController'
  './logger'
], (DocumentListParams, TagStoreProxy, InlineTagListView, tag_form_controller, TagDialogController, Logger) ->
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
      log('added tag', "#{tag_to_short_string(tag)} to #{JSON.stringify(state.getApiSelection())}")
      cache.addTagToSelection(tag, state.getApiSelection())
        .done(-> cache.refresh_tagcounts(tag))
      state.set(taglike: { tagId: tag.id })

    view.on 'remove-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('removed tag', "#{tag_to_short_string(tag)} from #{JSON.stringify(state.getApiSelection())}")
      cache.removeTagFromSelection(tag, state.getApiSelection())
        .done(-> cache.refresh_tagcounts(tag))
      state.set(taglike: { tagId: tag.id })

    view.on 'name-clicked', (tag) ->
      tag = proxy.unmap(tag)
      log('selected tag', "#{tag_to_short_string(tag)}")
      state.setDocumentListParams(DocumentListParams.byTagId(tag.id))
      state.set(taglike: { tagId: tag.id })

    view.on 'create-submitted', (name) ->
      tag = { name: name }
      log('created tag', "#{tag_to_short_string(tag)} on #{JSON.stringify(state.getApiSelection())}")
      tag = cache.add_tag(tag)
      cache.create_tag(tag)
      cache.addTagToSelection(tag, state.getApiSelection())
        .done(-> cache.refresh_tagcounts(tag))
      state.set(taglike: { tagId: tag.id })

    view.on 'organize-clicked', ->
      TagDialogController(tag_store, cache)

    view.on 'untagged-clicked', ->
      state.setDocumentListParams(DocumentListParams.untagged())
      state.set(taglike: { untagged: true })

    { view: view }
