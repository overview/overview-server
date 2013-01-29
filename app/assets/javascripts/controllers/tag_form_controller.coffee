TagFormView = require('views/tag_form_view').TagFormView

get_default_log = () -> require('globals').logger.for_component('tag_form')

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

# Pops up a modal dialog to modify/delete a tag
#
# Handles logging and hiding the dialog. Just call and forget.
tag_form_controller = (tag, cache, state, options=undefined) ->
  log = options?.log || get_default_log()

  log('began editing tag', tag_to_short_string(tag))

  form = options?.create_form?(tag) || new TagFormView(tag)

  form.observe 'closed', ->
    log('stopped editing tag', tag_to_short_string(tag))
    form = undefined

  form.observe 'change', (new_tag) ->
    log('edited tag', "#{tag.id}:#{tag_diff_to_string(tag, new_tag)}")
    cache.update_tag(tag, new_tag)

  form.observe 'delete', ->
    log('deleted tag', tag_to_short_string(tag))
    if state.focused_tag?.id == tag.id
      state.set('focused_tag', undefined)
    state.set('selection', state.selection.minus({ tags: [ tag.id ] }))
    cache.delete_tag(tag)

  undefined

exports = require.make_export_object('controllers/tag_form_controller')
exports.tag_form_controller = tag_form_controller
