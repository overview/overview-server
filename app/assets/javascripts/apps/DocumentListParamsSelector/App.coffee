define [
  'underscore'
  'backbone'
], (Backbone) ->
  class App extends Backbone.View
    template: _.template('''
      <div class="max-one-line">
        <div class="n-documents"></div>
        <div class="select-view-or-objects"></div>
      </div>
      <div class="q-if-set"></div>
      <div class="tags-if-set"></div>
      <div class="q-if-unset"></div>
      <div class="tags-if-unset"></div>
    ''')

    initialize: (options) ->
      if 'documentSetId' not of options
        throw new Error('Must set options.documentSetId, a String')
      if 'tags' not of options
        throw new Error('Must set options.tags, a Backbone.Collection of Models with `id` and `name`')
      if 'views' not of options
        throw new Error('Must set options.views, a Backbone.Collection of Models with `id` and `name`')
      if 'state' not of options
        throw new Error('Must set options.state, a Backbone.Model with a `setDocumentListParams()` method and a `documentList` _attribute_ that has a `params` _property_ (which, in turn, has a `toJSON()` method) and a `length` _attribute_ which is null when loading')

      @documentSetId = options.documentSetId
      @tags = options.tags
      @views = options.views
      @state = options.state

      @model = new DocumentListParamsModel()

      @_refreshModel()
      @_initialRender()

    # Updates the model with whatever's in the state.
    _refreshModel: ->
      @model.set(@state.get('documentList')?.params?.toJSON())

    _initialRender: ->
      @$el.html(@template())

      @ui =
        nDocuments: @$('.n-documents')
        selectViewOrObjects: @$('.select-view-or-objects')
        q:
          set: @$('.q-if-set')
          unset: @$('.q-if-unset')
        tags:
          set: @$('.q-if-set')
          unset: @$('.q-if-unset')

      @nDocumentsView = new NDocumentsView(model: @state, el: @ui.nDocuments)
      @selectViewView = new SelectViewView(model: @model, views: @views, el: @ui.selectViewOrObjects)
      @selectQView = new SelectQView(model: @model)
      @selectTagsView = new SelectTagsView(model: @model, tags: @tags)

      @nDocumentsView.render()
      @selectViewView.render()
      @selectQView.render()
      @selectTagsView.render()

      @ui.q.unset.append(@selectQView.el)
      @ui.tags.unset.append(@selectTagsView.el)
