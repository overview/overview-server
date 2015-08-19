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
      if 'documentSet' not of options
        throw new Error('Must set options.documentSet, a DocumentSet')
      if 'state' not of options
        throw new Error('Must set options.state, a Backbone.Model with a `setDocumentListParams()` method and a `documentList` _attribute_ that has a `params` _property_ (which, in turn, has a `toJSON()` method) and a `length` _attribute_ which is null when loading')

      @documentSet = options.documentSet
      @tags = @documentSet.tags

      @model = new DocumentListParamsModel
        documentSet: @documentSet
        view: @state?.get('documentList')?.params?.view

      @_refreshModel()
      @_initialRender()

    # Updates the model with whatever's in the state.
    _refreshModel: ->
      @model.set(@state.get('documentList')?.params?.toJSON(), refreshing: true)

    _refreshState: ->
      @state.setDocumentList(@model.toDocumentListParams(), refreshing: true)

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

      new NDocumentsView(model: @state, el: @ui.nDocuments)
      new SelectViewView(model: @model, state: @state, el: @ui.selectViewOrObjects)
      #new SelectQUnsetView(model: @model, el: @ui.q.unset)
      #new SelectQSetView(model: @model, el: @ui.q.set)
      #new SelectTagsUnsetView(model: @model, tags: @tags, el: @ui.tags.unset)
      #new SelectTagsSetView(model: @model, tags: @tags, el: @ui.tags.set)
