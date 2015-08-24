define [
  '../views/TagThis'
  './TagDialogController'
], (TagThisView, TagDialogController) ->
  tag_list_controller = (options) ->
    openTagDialog = ->
      new TagDialogController(tags: options.tags, state: options.state)

    tagThisView = new TagThisView
      tags: options.tags
      state: options.state
      keyboardController: options.keyboardController
      el: options.tagThisEl

    tagThisView.on('organize-clicked', openTagDialog)

    tagThisView: tagThisView
