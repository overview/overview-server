define [
  'jquery'
  'underscore'
  'backbone'
  '../models/ShowAppFacade'
  '../models/ViewAppClient'
], ($, _, Backbone, ShowAppFacade, ViewAppClient) ->
  class ViewAppController
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.el, an HTMLElement' if !options.el
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet
      throw 'Must pass options.keyboardController, a KeyboardController' if !options.keyboardController
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue
      throw 'Must pass options.viewAppConstructors, an Object mapping view type to an App' if !options.viewAppConstructors

      @el = options.el
      @$el = $(@el)
      @state = options.state
      @documentSet = options.documentSet
      @keyboardController = options.keyboardController
      @transactionQueue = options.transactionQueue
      @viewAppConstructors = options.viewAppConstructors

      @facade = new ShowAppFacade
        state: @state
        tags: @documentSet.tags
        searchResults: @documentSet.searchResults

      @_setView(@state.get('view'))
      @listenTo(@state, 'change:view', (__, view) => @_setView(view))

    _setView: (view) ->
      if @viewAppClient?
        @stopListening(@view)
        @viewAppClient.remove()
        @viewAppClient = null

      viewApp = null

      @$el.empty()

      if view?
        type = view.get('type')

        el = $('<div class="view"></div>').appendTo(@el)[0]

        viewApp = new @viewAppConstructors[type]
          app: @facade
          documentSetId: @documentSet.id
          view: view
          transactionQueue: @transactionQueue
          keyboardController: @keyboardController
          documentListParams: @state.attributes.documentListParams
          document: @state.attributes.document
          taglikeCid: @state.attributes.taglikeCid
          el: el
          documentSet: @documentSet
          state: @state

        @viewAppClient = new ViewAppClient
          viewApp: viewApp
          state: @state
          documentSet: @documentSet

        @view = view
        # When changing from "job" to "tree", reset everything
        @listenTo(@view, 'change:type', @_setView)

      @state.set('viewApp', viewApp)
