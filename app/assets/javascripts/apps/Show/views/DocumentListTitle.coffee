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
    id: 'document-list-title'
    tagName: 'h3'

    initialize: ->
      throw new Error('Must supply options.state, a State') if !@options.state

      @state = @options.state
      @listenTo(@state, 'change:documentList', @_attachDocumentList)

      @_attachDocumentList()

    _attachDocumentList: ->
      documentList = @state.get('documentList')

      if documentList != @documentList
        @stopListening(@documentList) if @documentList
        @documentList = documentList
        @listenTo(@documentList, 'change:length', @render) if @documentList
        @render()

    render: ->
      length = @documentList?.get('length')

      if length?
        @$el.html(t('title_html', length))
      else
        @$el.text(t('loading'))
