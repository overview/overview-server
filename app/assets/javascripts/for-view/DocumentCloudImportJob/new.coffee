define [ 'jquery', 'apps/DocumentCloudImportForm/app', 'apps/ImportOptions/app' ], ($, ImportApp, OptionsApp) ->
  MinDocumentCount = 3

  $ ->
    el = document.getElementById('document-cloud-import-job')
    query = el.getAttribute('data-query')
    submitUrl = el.getAttribute('data-submit-url')

    optionsApp = new OptionsApp
      onlyOptions: [ 'lang', 'split_documents', 'important_words', 'supplied_stop_words' ]
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode

    importApp = new ImportApp query, submitUrl,
      extraOptionsEl: optionsApp.el

    importApp.query.on 'change:document_count', (model) ->
      nFiles = importApp.query.get('document_count')
      tooFewDocuments = nFiles < MinDocumentCount
      optionsApp.view.setTooFewDocuments(tooFewDocuments)

    el.appendChild(importApp.el)
