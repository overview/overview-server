TagListView = require('views/tag_list_view').TagListView

TagFormView = require('views/tag_form_view').TagFormView
Selection = require('models/selection').Selection

log = require('globals').logger.for_component('tag-list')

tag_to_short_string = (tag) ->
  "#{tag.id} (#{tag.name})"

tag_diff_to_string = (tag1, tag2) ->
  changed = false
  s = ''
  if tag1.name != tag2.name
    s += " name: <<#{tag1.name}>> to <<#{tag2.name}>>"
    changed = true
  if tag1.color != tag2.color
    s += " color: <<#{tag1.color}>> to <<#{tag2.color}>>"
    changed = true
  if !changed
    s += " (no change)"
  s

tag_list_controller = (div, remote_tag_list, state) ->
  view = new TagListView(div, remote_tag_list, state)

  view.observe 'edit-clicked', (tag) ->
    log('began editing tag', tag_to_short_string(tag))
    form = new TagFormView(tag)

    form.observe 'closed', ->
      log('stopped editing tag', tag_to_short_string(tag))
      form = undefined

    form.observe 'change', (new_tag) ->
      log('edited tag', "#{tag.id}: #{tag_diff_to_string(tag, new_tag)}")
      remote_tag_list.cache.update_tag(tag, new_tag)

    form.observe 'delete', ->
      log('deleted tag', tag_to_short_string(tag))
      if state.focused_tag?.id == tag.id
        state.set('focused_tag', undefined)
      state.set('selection', state.selection.minus({ tags: [ tag.id ] }))
      remote_tag_list.cache.delete_tag(tag)

  view.observe 'add-clicked', (tag) ->
    log('added tag', "#{tag_to_short_string(tag)} to #{state.selection.to_string()}")
    remote_tag_list.add_tag_to_selection(tag, state.selection)
    state.set('focused_tag', tag)

  view.observe 'remove-clicked', (tag) ->
    log('removed tag', "#{tag_to_short_string(tag)} from #{state.selection.to_string()}")
    remote_tag_list.remove_tag_from_selection(tag, state.selection)
    state.set('focused_tag', tag)

  view.observe 'tag-clicked', (tag) ->
    log('selected tag', "#{tag_to_short_string(tag)}")
    state.set('selection', new Selection({ tags: [tag.id] })) # even if id is negative
    state.set('focused_tag', tag)

  view.observe 'create-submitted', (tag) ->
    log('created tag', "#{tag_to_short_string(tag)} on #{state.selection.to_string()}")
    tag = remote_tag_list.create_tag(tag.name)
    remote_tag_list.add_tag_to_selection(tag, state.selection)
    state.set('focused_tag', tag)

exports = require.make_export_object('controllers/tag_list_controller')
exports.tag_list_controller = tag_list_controller
