TagListView = require('views/tag_list_view').TagListView

TagFormView = require('views/tag_form_view').TagFormView
Selection = require('models/selection').Selection

tag_list_controller = (div, remote_tag_list, state) ->
  view = new TagListView(div, remote_tag_list, state)

  view.observe 'edit-clicked', (tag) ->
    form = new TagFormView(tag)
    form.observe('closed', -> form = undefined)
    form.observe('change', (new_tag) -> remote_tag_list.edit_tag(tag, new_tag))
    form.observe 'delete', ->
      if state.focused_tag?.id == tag.id
        state.set('focused_tag', undefined)
      state.set('selection', state.selection.minus({ tags: [ tag.id ] }))
      remote_tag_list.delete_tag(tag)

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
