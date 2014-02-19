define [ 'jquery', 'apps/ImportOptions/app' ], ($, OptionsApp) ->
  appOptions =
    supportedLanguages: window.supportedLanguages
    defaultLanguageCode: window.defaultLanguageCode
    onlyOptions: [ 'tree_title', 'lang', 'supplied_stop_words', 'important_words' ]

  $('.document-sets').on 'submit', 'form.create-tree', (e) ->
    OptionsApp.interceptSubmitEvent(e, appOptions)
