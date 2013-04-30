define [
  'jquery'
  'apps/DocumentCloudImportForm/models/DocumentCloudProject'
  'apps/DocumentCloudImportForm/models/DocumentCloudProjectFetcher'
  'apps/DocumentCloudImportForm/views/View'
], ($, DocumentCloudProject, DocumentCloudProjectFetcher, View) ->
  class App
    # Create a new DocumentCloudImportForm.
    #
    # Callers should access the "el" property to insert it into the page.
    # That element will update itself as needed, and it will finish with
    # a form submission.
    constructor: (projectId, submitUrl, options) ->
      @project = new DocumentCloudProject({ id: projectId })
      @projectFetcher = new DocumentCloudProjectFetcher({ project: @project })

      view = new View({ model: @projectFetcher, submitUrl: submitUrl })

      @el = options?.el || document.createElement('div')
      @el.appendChild(view.el)
