TagListView = require('views/tag_list_view').TagListView

tag_list_controller = (div, remote_tag_list, state) ->
  view = new TagListView(div, remote_tag_list)

  view.observe('add-clicked', (tag) -> remote_tag_list.add_tag_to_selection(tag, state.selection))
  view.observe('remove-clicked', (tag) -> remote_tag_list.remove_tag_from_selection(tag, state.selection))
  view.observe('create-submitted', (tag) -> remote_tag_list.create_tag(tag.name))

exports = require.make_export_object('controllers/tag_list_controller')
exports.tag_list_controller = tag_list_controller
