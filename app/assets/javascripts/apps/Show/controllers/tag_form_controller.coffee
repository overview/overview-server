define [ '../views/tag_form_view' ], (TagFormView) ->
  # Pops up a modal dialog to modify/delete a tag
  #
  # Handles logging and hiding the dialog. Just call and forget.
  tag_form_controller = (tag, cache, state, options=undefined) ->
    form = options?.create_form?(tag) || new TagFormView(tag)

    form.observe 'closed', ->
      form = undefined

    form.observe 'change', (new_attrs) ->
      tag.save(new_attrs)
      tag.collection?.sort()

    form.observe 'delete', ->
      if state.get('taglikeCid') == tag.cid
        state.set('taglikeCid', null)
      if (params = state.get('documentListParams'))? && params.type == 'tag' && params.tag == tag
        state.resetDocumentListParams.all()
      tag.destroy()

    undefined
