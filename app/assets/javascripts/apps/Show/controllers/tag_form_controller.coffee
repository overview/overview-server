define [ '../views/tag_form_view', './logger' ], (TagFormView, Logger) ->
  tag_to_short_string = (tag) -> "#{tag.id} (#{tag.attributes.name})"

  tag_diff_to_string = (tag, attrs) ->
    changed = false
    s = ''
    if tag.attributes.name != attrs.name
      s += " name: <<#{tag.attributes.name}>> to <<#{attrs.name}>>"
      changed = true
    if tag.attributes.color != attrs.color
      s += " color: <<#{tag.attributes.color}>> to <<#{attrs.color}>>"
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

    form.observe 'change', (new_attrs) ->
      log('edited tag', "#{tag.id}:#{tag_diff_to_string(tag, new_attrs)}")
      tag.save(new_attrs)
      tag.collection?.sort()

    form.observe 'delete', ->
      log('deleted tag', tag_to_short_string(tag))
      if state.get('taglikeCid') == tag.cid
        state.set('taglikeCid', null)
      if (params = state.get('documentListParams'))? && params.type == 'tag' && params.tag == tag
        state.resetDocumentListParams.all()
      tag.destroy()

    undefined
