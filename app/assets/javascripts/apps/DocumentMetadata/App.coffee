define [
  'jquery'
  'underscore'
  'backbone'
  './views/JsonView'
  './views/AddFieldView'
  'i18n'
], ($, _, Backbone, JsonView, AddFieldView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.App')

  # There may be several DocumentMetadataApps created in the lifetime of a
  # single page load. (That's the reality. Yup, it's icky.) We want each one to
  # start expanded/collapsed if the previous one was expanded/collapsed.
  globalExpanded = false

  class DocumentMetadataApp extends Backbone.View
    className: 'document-metadata'

    initialize: (options) ->
      throw 'Must specify options.documentSet, a Backbone.Model with metadataFields' if !options.documentSet

      @documentSet = options.documentSet
      @document = null
      @documentMetadataFetched = false

      globalExpanded = options.expanded if options.expanded? # help unit tests start with a clean slate
      @$el.addClass('expanded') if globalExpanded
      @$el.toggleClass('expanded', globalExpanded)

      @initialRender()

    events:
      'click .expand-metadata': '_onClickExpand'

    initialRender: ->
      @$title = $(_.template('<h4><a href="#" class="expand-metadata"><%- title %></a></h4>')(title: t('title')))
      @$loading = $(_.template('<div class="loading"><i class="icon icon-spinner icon-spin"/><%- loading %></div>')(loading: t('loading')))

      @jsonView = null
      @addFieldView = null

      @render()

    render: ->
      @$el.empty()

      if @jsonView && @addFieldView
        @$el.append(@$title)
        @$el.append(@jsonView.el)
        @$el.append(@addFieldView.el)
      else if @document?
        @$el.append(@$title)
        @$el.append(@$loading)

      @

    setDocument: (document) ->
      @jsonView?.remove()
      @jsonView = null

      # It'd be nice to create a single AddFieldView and keep it forever. But
      # when we remove it from the document its jQuery events go with it.
      # Rather than mess with reattaching, let's just build a new one.
      @addFieldView?.remove()
      @addFieldView = null

      @document = document
      @documentMetadataFetched = false

      if @document?
        Backbone.ajax
          type: 'GET'
          url: _.result(@document, 'url')
          dataType: 'json'
          success: (data) =>
            return if @document != document # stale response
            @document.set(metadata: data.metadata)
            @jsonView = new JsonView(documentSet: @documentSet, document: @document)
            @addFieldView = new AddFieldView(documentSet: @documentSet)

            @render()

      @render()

    _onClickExpand: (e) ->
      e.preventDefault()
      globalExpanded = !globalExpanded
      @$el.toggleClass('expanded', globalExpanded)
      e.target.blur() # Workaround: the link stays underlined as it animates away on Firefox and Chrome
