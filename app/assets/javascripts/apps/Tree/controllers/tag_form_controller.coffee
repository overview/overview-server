define [ '../views/tag_form_view', '../models/DocumentListParams', './logger' ], (TagFormView, DocumentListParams, Logger) ->
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
    log = options?.log || Logger.for_component('tag_form')

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
      if state.get('taglike')?.tagId == tag.id
        state.set('taglike', null)
      if (params = state.get('documentListParams'))? && params.type == 'tag' && params.tagId == tag.id
        state.setDocumentListParams(DocumentListParams.all())
      cache.delete_tag(tag)

    undefined
