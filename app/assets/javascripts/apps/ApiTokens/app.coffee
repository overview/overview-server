define [
  './collections/ApiTokens'
  './views/FormView'
  './views/TableView'
], (ApiTokens, FormView, TableView) ->
  class ApiTokensApp extends Backbone.View
    initialize: (options) ->
      throw 'Must pass options.documentSetId, a Number' if !options.documentSetId

      @documentSetId = options.documentSetId

      @tokens = new ApiTokens([], documentSetId: @documentSetId)

      @tableView = new TableView(collection: @tokens)
      @formView = new FormView(collection: @tokens)

      @render()

      @tokens.fetch()

    render: ->
      @tableView.render()
      @formView.render()

      @$el.append(@tableView.el)
      @$el.append(@formView.el)
