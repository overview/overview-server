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

      @initialRender()

    initialRender: ->
      @$title = $(_.template('<h4><a href="#" class="expand-metadata"><span><%- title %></span></a></h4>')(title: t('title')))

      @jsonView = new JsonView(model: @model)
      @addFieldView = new AddFieldView(model: @model)

      @jsonView.render()
      @addFieldView.render()

      @$el.append(@$title)
      @$el.append(@jsonView.el)
      @$el.append(@addFieldView.el)

      @jsonView.delegateEvents()
      @addFieldView.delegateEvents()

      @render()

    remove: ->
      @jsonView?.remove()
      @addFieldView?.remove()
      Backbone.View.prototype.remove.apply(@)

    render: ->
      @

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
    new DocumentMetadataApp(_.extend({
      fields: [],
      saveFields: (fields, done) -> done(null)
    }, options))

  DocumentMetadataApp
