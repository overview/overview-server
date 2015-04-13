define [
  '../views/TagSelect'
  './TagDialogController'
], (TagSelectView, TagDialogController) ->
  tag_list_controller = (options) ->
    state = options.state
    tags = state.tags
    el = options.el

    view = new TagSelectView
      collection: tags
      state: state
      el: el

    view.on 'organize-clicked', ->
      new TagDialogController(tags: tags, state: state)

    { view: view }
