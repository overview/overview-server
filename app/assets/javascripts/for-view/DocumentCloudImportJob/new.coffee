define [ 'jquery', 'apps/DocumentCloudImportForm/app' ], ($, App) ->
  $ ->
    el = document.getElementById('document-cloud-import-job')

    projectId = el.getAttribute('data-document-cloud-project-id')
    submitUrl = el.getAttribute('data-submit-url')
    app = new App(projectId, submitUrl)

    el.appendChild(app.el)
