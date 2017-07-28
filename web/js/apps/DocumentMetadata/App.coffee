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
      throw 'Must specify options.fields, an Array of Strings to start with' if !options.fields
      throw 'Must specify options.saveFields, a NodeJS-style async function accepting an Array of Strings' if !options.saveFields

      @model = new Backbone.Model
        fields: options.fields
        json: {}

      @listenTo @model, 'change:fields', (__, newFields) ->
        options.saveFields newFields, (err) ->
          throw err if err? # We don't handle errors.

      @document = null

      @listenTo @model, 'change:json', (__, newJson, options) =>
        if options.cause == 'userEntry'
          @document?.save({ metadata: newJson }, patch: true)

      globalExpanded = options.expanded if options.expanded? # help unit tests start with a clean slate
      @$el.addClass('expanded') if globalExpanded
      @$el.toggleClass('expanded', globalExpanded)

      @initialRender()

    events:
      'click .expand-metadata': '_onClickExpand'

    initialRender: ->
      @$title = $(_.template('<h4><a href="#" class="expand-metadata"><span><%- title %></span></a></h4>')(title: t('title')))

      @jsonView = null
      @addFieldView = null

      @render()

    render: ->
      @$el.empty()

      if @jsonView && @addFieldView
        @$el.append(@$title)
        @$el.append(@jsonView.el)
        @$el.append(@addFieldView.el)

        @jsonView.delegateEvents()
        @addFieldView.delegateEvents()
      else if @document?
        @$el.append(@$title)

      @

    # Specifies that we want to edit JSON, but not for any particular document.
    #
    # You can call `getJson()` to access the JSON.
    setNoDocument: ->
      @stopListening(@document) if @document?
      @document = null
      @jsonView?.remove()
      @addFieldView?.remove()

      @model.set(json: {})
      @jsonView = new JsonView(model: @model)
      @addFieldView = new AddFieldView(model: @model)
      @render()

    # Specifies that we want to edit a Document's metadata.
    #
    # Shows a spinner, loads the document metadata, and then presents an edit
    # interface. Any edit will trigger a
    # `document.save({ metadata: {...} }, patch: true)`.
    #
    # setDocument(null) will hide the interface.
    setDocument: (document) ->
      @jsonView?.remove()
      @jsonView = null

      # It'd be nice to create a single AddFieldView and keep it forever. But
      # when we remove it from the document its jQuery events go with it.
      # Rather than mess with reattaching, let's just build a new one.
      @addFieldView?.remove()
      @addFieldView = null

      @stopListening(@document) if @document?
      @document = document

      if @document?
        @model.set({ json: @document.get('metadata') }, cause: 'newDocument')
        @listenTo(@document, 'change:metadata', (__, metadata) => @model.set({ json: metadata }, cause: 'documentChange'))
        @jsonView = new JsonView(model: @model)
        @addFieldView = new AddFieldView(model: @model)

      @render()

    _onClickExpand: (e) ->
      e.preventDefault()
      e.stopPropagation() # Prevent redirect confirmation when in MassUpload dialog
      globalExpanded = !globalExpanded
      @$el.toggleClass('expanded', globalExpanded)
      e.target.blur() # Workaround: the link stays underlined as it animates away on Firefox and Chrome

  DocumentMetadataApp.forDocumentSet = (documentSet, options={}) ->
    new DocumentMetadataApp(_.extend({
      fields: documentSet.get('metadataFields')

      saveFields: (fields, done) ->
        documentSet.patchMetadataFields fields,
          success: done(null)
          error: (model, response, options) ->
            console.warn(response)
            done(new Error("Failed to save metadata fields"))
    }, options))

  DocumentMetadataApp.forNoDocumentSet = (options={}) ->
    app = new DocumentMetadataApp(_.extend({
      fields: [],
      saveFields: (fields, done) -> done(null)
    }, options))
    app.setNoDocument()
    app

  DocumentMetadataApp
