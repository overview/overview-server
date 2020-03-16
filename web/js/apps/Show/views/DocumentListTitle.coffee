define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListTitle')

  # Shows what's currently selected
  #
  # Usage:
  #
  #   state = new State(...)
  #   view = new DocumentListTitleView(state: state)
  #
  # Listens to State's `change:document-list`. Doesn't call anything or emit
  # any events.
  class DocumentListTitleView extends Backbone.View
    events:
      'click a[data-sort-by-metadata-field]': 'onClickSortByMetadataField'
      'change input[name=reverse]': 'onChangeReverse'

    templates:
      main: _.template('''
        <h3><%= t('title_html', length) %></h3>
        <div class="sort-by"><%= t('sort_by_FIELD').replace('FIELD', sortByFieldHtml) %></div>
      ''')

      loading: _.template('''
        <h3><%= t('loading') %></h3>
        <progress <%= (progress ? ('value="' + progress + '"') : "") %>></progress>
      ''')

      sortByField: _.template('''
        <div class="sort-by-field dropdown">
          <a id="DocumentListTitle-sort-by-field" data-target="#" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false"><%- currentSortBy %></a>
          <ul class="dropdown-menu" role="menu" aria-labelledby="DocumentListTitle-sort-by-field">
            <li><a href="#" data-sort-by-metadata-field=""><%- sortByTitle %></a></li>
            <% metadataFields.forEach(function(field) { %>
              <li><a href="#" data-sort-by-metadata-field="<%- field %>"><%- field %></a></li>
            <% }) %>
          </ul>
        </div>
      ''')

      sortReverse: _.template('''
        <label class="sort-reverse">
          <input type="checkbox" name="reverse" <%= reverse ? "checked" : "" %>>
          <span class="sort-display"></span>
        </label>
      ''')

    initialize: (options) ->
      throw new Error('Must supply options.state, a State') if !options.state

      @state = options.state
      @documentSet = @state.documentSet
      @listenTo(@documentSet, 'change:metadataFields', @render)
      @listenTo(@state, 'change:documentList', @_attachDocumentList)

      @_attachDocumentList()

    _attachDocumentList: ->
      documentList = @state.get('documentList')

      if documentList != @documentList
        @stopListening(@documentList) if @documentList
        @documentList = documentList
        @listenTo(@documentList, 'change:length change:progress', @render) if @documentList
        @render()

    render: ->
      length = @documentList?.get('length')

      @el.setAttribute('data-n-documents', length || 0)

      if length?
        fields = @documentSet.get('metadataFields')
        sortByTitle = t('sort_by.title')
        sortByFieldHtml = if fields?.length
          @templates.sortByField(
            currentSortBy: @documentList?.params?.sortByMetadataField || sortByTitle
            sortByTitle: sortByTitle
            metadataFields: fields
          ) + @templates.sortReverse(reverse: @documentList?.reverse || false)
        else
          _.escape(sortByTitle) + @templates.sortReverse(reverse: @documentList?.reverse || false)
        @$el.html(@templates.main(t: t, length: length, sortByFieldHtml: sortByFieldHtml))
      else
        @$el.html(@templates.loading(t: t, progress: @documentList?.get('progress')))

    onClickSortByMetadataField: (ev) ->
      ev.preventDefault()

      oldParams = @documentList?.params
      return if !oldParams
      @state.refineDocumentListParams
        sortByMetadataField: ev.target.getAttribute('data-sort-by-metadata-field') || null

    onChangeReverse: ->
      oldParams = @documentList?.params
      return if !oldParams

      reverse = @$('input[name=reverse]').prop('checked')
      @state.refineDocumentListParams({}, reverse)
