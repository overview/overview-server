define [
  'jquery'
  'apps/DocumentCloudImportForm/models/DocumentCloudQuery'
  'apps/DocumentCloudImportForm/models/DocumentCloudQueryFetcher'
  'apps/DocumentCloudImportForm/views/View'
], ($, DocumentCloudQuery, DocumentCloudQueryFetcher, View) ->
  class App
    # Create a new DocumentCloudImportForm.
    #
    # Callers should access the "el" property to insert it into the page.
    # That element will update itself as needed, and it will finish with
    # a form submission.
    constructor: (query, submitUrl, options) ->
      throw 'Must pass options.extraOptionsEl, an HTML element' if !options.extraOptionsEl

      @query = new DocumentCloudQuery({ id: query })
      @queryFetcher = new DocumentCloudQueryFetcher({ query: @query })

      view = new View({
        model: @queryFetcher
        submitUrl: submitUrl
        extraOptionsEl: options.extraOptionsEl
      })

      @el = options?.el || document.createElement('div')
      @el.appendChild(view.el)
