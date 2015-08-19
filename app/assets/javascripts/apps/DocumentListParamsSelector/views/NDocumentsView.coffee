define [
  'backbone'
  'i18n'
], (Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListParamsSelector.NDocumentsView')

  # Displays "3 documents" or "loading...".
  #
  # Pass it a model which is a State object. The model will trigger
  # 'change:documentList' whenever its DocumentList changes. The DocumentList,
  # in turn, will trigger 'change:length' when it finishes loading.
  class NDocumentsView extends Backbone.View
    initialize: (options) ->
      throw new Error('Must set options.model, a State object') if 'model' not of options

      @documentList = null
      @_setDocumentList(@model.get('documentList'))

      @listenTo(@model, 'change:documentList', (model, value) => @_setDocumentList(value))

    # Detaches from whatever DocumentList we were listening to, and attaches to
    # the given one instead. Renders.
    _setDocumentList: (documentList) ->
      if @documentList?
        @stopListening(@documentList)

      @documentList = documentList

      if @documentList?
        @listenTo(@documentList, 'change:length', @render)

      @render()

    render: ->
      nDocuments = @documentList?.get('length')
      text = if nDocuments?
        t('nDocuments', nDocuments)
      else
        t('loading')
      @$el.text(text)
