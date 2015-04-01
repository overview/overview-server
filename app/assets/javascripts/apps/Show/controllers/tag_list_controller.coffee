define [
  '../views/InlineTagList'
  './TagDialogController'
], (InlineTagListView, TagDialogController) ->
  tag_list_controller = (options) ->
    documentSet = options.documentSet
    tags = documentSet.tags
    state = options.state
    el = options.el

    view = new InlineTagListView
      collection: tags
      state: state
      el: el

    view.on 'name-clicked', (tag) ->
      state.resetDocumentListParams().byTag(tag)

    view.on 'organize-clicked', ->
      new TagDialogController(tags: tags, state: state)

    view.on 'untagged-clicked', ->
      state.resetDocumentListParams().byUntagged()

    { view: view }
