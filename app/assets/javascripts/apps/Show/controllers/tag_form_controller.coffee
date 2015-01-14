define [ '../views/tag_form_view' ], (TagFormView) ->
  # Pops up a modal dialog to modify/delete a tag
  #
  # Handles logging and hiding the dialog. Just call and forget.
  tag_form_controller = (tag, state, options=undefined) ->
    form = options?.create_form?(tag) || new TagFormView(tag)

    form.observe 'closed', ->
      form = undefined

    form.observe 'change', (new_attrs) ->
      tag.save(new_attrs)
      tag.collection?.sort()

    form.observe 'delete', ->
      if (params = state.get('documentListParams'))? && params.type == 'tag' && params.tag == tag
        state.resetDocumentListParams().all()
      if (params = state.get('highlightedDocumentListParams'))? && params.type == 'tag' && params.tag == tag
        state.set(highlightedDocumentListParams: null)
      tag.destroy()

    undefined
