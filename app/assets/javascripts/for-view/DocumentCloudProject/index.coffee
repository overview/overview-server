define [ 'jquery', 'dcimport/import_project_with_login', 'apps/ImportOptions/app' ], ($, import_project_with_login, OptionsApp) ->
  MinDocumentCount = 3

  $ ->
    $dcImportDiv = $('.documentcloud-projects')

    import_project_with_login($dcImportDiv[0])

    $dcImportDiv.on 'submit', '.projects form', (e) ->
      countString = $('[data-file-count]', e.target.parentNode).attr('data-file-count')
      count = parseInt(countString, 10)
      tooFewDocuments = count < MinDocumentCount
      OptionsApp.interceptSubmitEvent e,
        onlyOptions: [ 'lang', 'split_documents', 'important_words', 'supplied_stop_words' ]
        supportedLanguages: window.supportedLanguages
        defaultLanguageCode: window.defaultLanguageCode
        tooFewDocuments: tooFewDocuments
