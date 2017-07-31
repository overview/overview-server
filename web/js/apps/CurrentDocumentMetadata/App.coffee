define [
  'backbone'
  './api'
  './view'
  'escape-html'
  'i18n'
], (Backbone, Api, View, escapeHtml, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentMetadata.App')

  # There may be several DocumentMetadataApps created in the lifetime of a
  # single page load. (That's the reality. Yup, it's icky.) We want each one to
  # start expanded/collapsed if the previous one was expanded/collapsed.
  globalExpanded = false

  class CurrentDocumentMetadataApp extends Backbone.View
    className: 'document-metadata'

    initialize: (options) ->
      throw 'Must specify options.api, an Api' if !options.api

      @api = options.api

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

    remove: ->
      @api.destroy()
      @view.remove()
      Backbone.View.prototype.remove.call(@)

    initialRender: ->
      @el.innerHTML = [
        '<h4><a href="#" class="expand-metadata"><span>',
        escapeHtml(t('title')),
        '</span></h4><div class="overview-plugin-metadata-app"></div>'
      ].join('')

      @view = new View(@el.querySelector('.overview-plugin-metadata-app'), @api)

      @render()

    render: ->
      @

    _onClickExpand: (e) ->
      e.preventDefault()
      e.stopPropagation() # Prevent redirect confirmation when in MassUpload dialog
      globalExpanded = !globalExpanded
      @$el.toggleClass('expanded', globalExpanded)
      e.target.blur() # Workaround: the link stays underlined as it animates away on Firefox and Chrome

  CurrentDocumentMetadataApp.create = (apiOptions, options={}) ->
    throw 'Must specify apiOptions.state, a State' if !apiOptions.state
    throw 'Must specify apiOptions.globalActions, an Object of Functions' if !apiOptions.globalActions

    api = new Api(apiOptions)
    new CurrentDocumentMetadataApp(Object.assign({ api: api }, options))

  CurrentDocumentMetadataApp
