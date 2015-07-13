define [
  'jquery'
  'underscore'
  'backbone'
  './views/JsonView'
  './views/AddFieldView'
  'i18n'
], ($, _, Backbone, JsonView, AddFieldView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.App')

  class DocumentMetadataApp extends Backbone.View
    initialize: (options) ->
      throw 'Must specify options.documentSet, a Backbone.Model with metadataFields' if !options.documentSet

      @documentSet = options.documentSet
      @addFieldView = new AddFieldView(documentSet: @documentSet)
      @document = null
      @documentMetadataFetched = false

      @initialRender()

    initialRender: ->
      @$loading = $('<div class="loading"><i class="icon icon-spinner icon-spin"/></div>')
      @$loading.append(_.escape(t('loading')))

      @jsonView = null

      @render()

    render: ->
      @$el.empty()

      if @jsonView
        @$el.append(@jsonView.el)
        @$el.append(@addFieldView.el)
      else if @document?
        @$el.append(@$loading)

      @

    setDocument: (document) ->
      @jsonView?.remove()
      @jsonView = null

      @document = document
      @documentMetadataFetched = false

      if @document?
        @document.fetch
          success: =>
            return if @document != document # stale response

            @documentMetadataFetched = true
            @jsonView = new JsonView(documentSet: @documentSet, document: @document)

            @render()

      @render()
