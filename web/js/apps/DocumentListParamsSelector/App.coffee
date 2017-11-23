define [
  'underscore'
  'backbone'
  './views/ObjectsOrDocumentSetView'
  './views/SearchView'
  './views/SelectTagsView'
  './views/FiltersView'
], (_, Backbone, ObjectsOrDocumentSetView, SearchView, SelectTagsView, FiltersView) ->
  class App extends Backbone.View
    template: _.template('''
      <div class="objects-or-document-set"></div>
      <div class="search"></div>
      <div class="tags"></div>
      <div class="view-filters"></div>
    ''')

    initialize: (options) ->
      throw new Error('Must set options.documentSet, a DocumentSet') if !options.documentSet
      throw new Error('Must pass options.state, a Backbone.Model with a `refineDocumentListParams()` method and a `documentList` attribute that has a `params` property and a `length` attribute which is null when loading') if !options.state
      throw new Error('Must pass options.globalActions, an Object with functions') if !options.globalActions

      @documentSet = options.documentSet
      @tags = @documentSet.tags
      @state = options.state
      @globalActions = options.globalActions

      @model = new Backbone.Model(@state.documentList?.params || {})

      @_refreshModel()
      @_initialRender()

      @listenTo(@state, 'change:documentList', @_refreshModel)

    # Updates the model with whatever's in the state.
    _refreshModel: ->
      params = @state.get('documentList')?.params || {}
      @model.set
        q: params.q
        title: params.title
        tags: params.tags
        tagOperation: params.tagOperation
        tagged: params.tagged

    _initialRender: ->
      @$el.html(@template())

      @ui =
        objectsOrDocumentSet: @$('.objects-or-document-set')
        search: @$('.search')
        tags: @$('.tags')
        filters: @$('.view-filters')

      new ObjectsOrDocumentSetView(model: @model, state: @state, el: @ui.objectsOrDocumentSet)
      new SearchView(model: @model, state: @state, el: @ui.search, globalActions: @globalActions)
      new SelectTagsView(model: @model, state: @state, tags: @tags, el: @ui.tags)
      new FiltersView(collection: @documentSet.views, state: @state, el: @ui.filters)
