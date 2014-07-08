define [
  '../models/DocumentListParams'
  '../views/InlineTagList'
  './tag_form_controller'
  './TagDialogController'
], (DocumentListParams, InlineTagListView, tag_form_controller, TagDialogController) ->
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
      documentSet.tag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'remove-clicked', (tag) ->
      documentSet.untag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'name-clicked', (tag) ->
      state.resetDocumentListParams().byTag(tag)
      state.set(taglikeCid: tag.cid)

    view.on 'create-submitted', (name) ->
      tag = tags.create(name: name)
      documentSet.tag(tag, state.getSelection())
      state.set(taglikeCid: tag.cid)

    view.on 'organize-clicked', ->
      new TagDialogController(tags: tags, state: state)

    view.on 'untagged-clicked', ->
      state.resetDocumentListParams().untagged()
      state.set(taglikeCid: 'untagged')

    { view: view }
