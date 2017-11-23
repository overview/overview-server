define [
  'backbone'
  './FilterView'
  'i18n'
], (Backbone, FilterView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagSelect')

  # Displays and edits the tag selection.
  class SelectTagsView extends Backbone.View
    attributes:
      class: 'select-tags'

    initialize: (options) ->
      throw 'Must set options.model, a Backbone.Model with `tags`' if !options.model
      throw 'Must set options.tags, a Tag Collection' if !options.tags
      throw 'Must set options.state, an Object with a refineDocumentListParams method' if !options.state

      @state = options.state
      @tags = options.tags
      @model = options.model

      @listenTo(@tags, 'change', @sendTagsToFilterView) # handle tag name change
      @listenTo(@model, 'change:tags change:tagged change:tagOperation', @sendSelectionToFilterView)

      @filterView = new FilterView({
        renderOptions: {
          iconClass: 'tag',
          messages: {
            placeholder: t('placeholder'),
            filtersEmpty: t('noTags'),
            selectOneHtml: t('selectOneHtml'),
            selectNotHtml: t('selectNotHtml'),
            selectAnyHtml: t('selectAnyHtml'),
            selectAllHtml: t('selectAllHtml'),
            selectNoneHtml: t('selectNoneHtml'),
            # Add '{0}' to these messages. If selectedOneHtml is "with tag {0}",
            # we want to pass "with tag {0}" to FilterView. If we just called
            # t('selectedOneHtml'), t() would replace the '{0}' with undefined.
            selectedOneHtml: t('selectedOneHtml', '{0}'),
            selectedNotHtml: t('selectedNotHtml', '{0}'),
            selectedAnyHtml: t('selectedAnyHtml', '{0}'),
            selectedAllHtml: t('selectedAllHtml', '{0}'),
            selectedNoneHtml: t('selectedNoneHtml', '{0}'),
          },
        },
        filters: @_tagsAsFilters(),
        selection: @_paramsAsSelection(),
        onSelect: (selection) => @onSelect(selection),
      })
      @_initialRender()

    # Render tags, the way FilterView understands
    _tagsAsFilters: ->
      filters = @tags.models
        .map((tag) => { id: String(tag.id), name: tag.get('name'), color: tag.get('color') })

      filters.push({ id: 'tagged', name: t('tagged'), color: '#dddddd' })

      filters

    # Render selection, the way FilterView understands
    _paramsAsSelection: ->
      params = @model

      ids = (params.get('tags') || []).map(String)
      if params.get('tagged') == true
        ids.push('tagged')
      else if params.get('tagged') == false
        ids.push('untagged')

      operation = params.get('tagOperation') || 'any'

      { ids: ids, operation: operation }

    sendTagsToFilterView: ->
      @filterView.setFilters(@_tagsAsFilters())

    sendSelectionToFilterView: ->
      @filterView.setSelection(@_paramsAsSelection())

    onSelect: (selection) ->
      # We'll call refineDocumentListParams. We need to pass at least
      # { tags: null }, because refineDocumentListParams({}) is a no-op;
      # we want refineDocumentListParams({ tags: null }) when no tags are
      # selected
      params = { tags: null }

      if selection.ids.length > 0
        params.tags = selection.ids.filter((id) => id != 'tagged' && id != 'untagged').map(parseFloat)
        params.tagOperation = selection.operation
        if selection.ids.indexOf('tagged') != -1
          params.tagged = true
        else if selection.ids.indexOf('untagged') != -1
          params.tagged = false

      @state.refineDocumentListParams(params)

    _initialRender: ->
      @el.appendChild(@filterView.el)
      @filterView.attachEventListeners()
