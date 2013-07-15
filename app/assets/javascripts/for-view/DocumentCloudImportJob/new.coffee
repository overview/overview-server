define [ 'jquery', 'apps/DocumentCloudImportForm/app' ], ($, App) ->
  $ ->
    el = document.getElementById('document-cloud-import-job')

    query = el.getAttribute('data-query')
    submitUrl = el.getAttribute('data-submit-url')
    app = new App(query, submitUrl, {
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
    })

    el.appendChild(app.el)
