define [ 'jquery', 'dcimport/import_project_with_login', 'apps/ImportOptions/app' ], ($, import_project_with_login, OptionsApp) ->
  $ ->
    $dcImportDiv = $('#import-from-documentcloud-account .import-pane-contents')

    show = -> import_project_with_login($dcImportDiv[0])

    $('#import-from-documentcloud-account').one('activate', show)

    $dcImportDiv.on 'submit', '.projects form', (e) ->
      OptionsApp.interceptSubmitEvent(e, {
        supportedLanguages: window.supportedLanguages
        defaultLanguageCode: window.defaultLanguageCode
        excludeOptions: ['name']
      })
