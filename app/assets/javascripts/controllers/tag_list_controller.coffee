TagListView = require('views/tag_list_view').TagListView
Selection = require('models/selection').Selection

tag_list_controller = (div, remote_tag_list, state) ->
  view = new TagListView(div, remote_tag_list, state)

  view.observe 'add-clicked', (tag) ->
    remote_tag_list.add_tag_to_selection(tag, state.selection)
    state.set('focused_tag', tag)
  view.observe 'remove-clicked', (tag) ->
    remote_tag_list.remove_tag_from_selection(tag, state.selection)
    state.set('focused_tag', tag)
  view.observe 'tag-clicked', (tag) ->
    state.set('selection', new Selection({ tags: [tag.id] })) # even if id is negative
    state.set('focused_tag', tag)

  view.observe 'create-submitted', (tag) ->
    tag = remote_tag_list.create_tag(tag.name)
    remote_tag_list.add_tag_to_selection(tag, state.selection)
    state.set('focused_tag', tag)

exports = require.make_export_object('controllers/tag_list_controller')
exports.tag_list_controller = tag_list_controller
