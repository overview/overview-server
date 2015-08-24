define [
  'underscore'
  'backbone'
  './views/ObjectsOrDocumentSetView'
  './views/SearchView'
  './views/SelectTagsView'
], (_, Backbone, ObjectsOrDocumentSetView, SearchView, SelectTagsView) ->
  class App extends Backbone.View
    template: _.template('''
      <div class="objects-or-document-set"></div>
      <div class="search"></div>
      <div class="tags"></div>
    ''')

    initialize: (options) ->
      if 'documentSet' not of options
        throw new Error('Must set options.documentSet, a DocumentSet')
      if 'state' not of options
        throw new Error('Must set options.state, a Backbone.Model with a `refineDocumentListParams()` method and a `documentList` _attribute_ that has a `params` _property_ (which, in turn, has a `toJSON()` method) and a `length` _attribute_ which is null when loading')

      @documentSet = options.documentSet
      @tags = @documentSet.tags
      @state = options.state

      @model = new Backbone.Model(@state.documentList?.params || {})

      @_refreshModel()
      @_initialRender()

      @listenTo(@state, 'change:documentList', @_refreshModel)

    # Updates the model with whatever's in the state.
    _refreshModel: ->
      @model.set(@state.get('documentList')?.params || {})

    _initialRender: ->
      @$el.html(@template())

      @ui =
        objectsOrDocumentSet: @$('.objects-or-document-set')
        search: @$('.search')
        tags: @$('.tags')

      new ObjectsOrDocumentSetView(model: @model, state: @state, el: @ui.objectsOrDocumentSet)
      new SearchView(model: @model, state: @state, el: @ui.search)
      new SelectTagsView(model: @model, state: @state, tags: @tags, el: @ui.tags)
