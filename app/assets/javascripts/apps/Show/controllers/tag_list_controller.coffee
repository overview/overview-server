define [
  '../views/TagSelect'
  '../views/TagThis'
  './TagDialogController'
], (TagSelectView, TagThisView, TagDialogController) ->
  tag_list_controller = (options) ->
    openTagDialog = ->
      new TagDialogController(tags: options.tags, state: options.state)

    tagSelectView = new TagSelectView
      collection: options.tags
      state: options.state
      el: options.tagSelectEl

    tagThisView = new TagThisView
      tags: options.tags
      state: options.state
      keyboardController: options.keyboardController
      el: options.tagThisEl

    tagSelectView.on('organize-clicked', openTagDialog)
    tagThisView.on('organize-clicked', openTagDialog)

    tagSelectView: tagSelectView
    tagThisView: tagThisView
