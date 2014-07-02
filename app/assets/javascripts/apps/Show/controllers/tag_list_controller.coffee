define [
  '../models/DocumentListParams'
  '../views/InlineTagList'
  './tag_form_controller'
  './TagDialogController'
  './logger'
], (DocumentListParams, InlineTagListView, tag_form_controller, TagDialogController, Logger) ->
  log = Logger.for_component('tag_list')

  tag_to_short_string = (tag) ->
    "#{tag.id} (#{tag.attributes.name})"

  tag_list_controller = (options) ->
    documentSet = options.documentSet
    tags = documentSet.tags
    state = options.state
    el = options.el

    view = new InlineTagListView
      collection: tags
      state: state
      el: el

    view.on 'add-clicked', (tag) ->
      log('added tag', "#{tag_to_short_string(tag)} to #{JSON.stringify(state.getSelection())}")
      documentSet.tag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'remove-clicked', (tag) ->
      log('removed tag', "#{tag_to_short_string(tag)} from #{JSON.stringify(state.getSelection())}")
      documentSet.untag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'name-clicked', (tag) ->
      log('selected tag', "#{tag_to_short_string(tag)}")
      state.resetDocumentListParams().byTag(tag)
      state.set(taglikeCid: tag.cid)

    view.on 'create-submitted', (name) ->
      tag = tags.create(name: name)
      log('created tag', "#{tag_to_short_string(tag)} on #{JSON.stringify(state.getSelection())}")
      documentSet.tag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'organize-clicked', ->
      new TagDialogController(tags: tags, state: state)

    view.on 'untagged-clicked', ->
      state.resetDocumentListParams().untagged()
      state.set(taglikeCid: 'untagged')

    { view: view }
