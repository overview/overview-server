define [ 'jquery', 'apps/ImportOptions/app' ], ($, OptionsApp) ->
  appOptions =
    supportedLanguages: window.supportedLanguages
    defaultLanguageCode: window.defaultLanguageCode
    excludeOptions: [ 'name', 'split_documents' ]

  $('.document-sets').on 'submit', 'form.create-tree', (e) ->
    OptionsApp.interceptSubmitEvent(e, appOptions)
