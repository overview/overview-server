define [ 'jquery', 'apps/MassUploadForm/app' ], ($, MassUploadApp) ->
  $ ->
    $form = $('form.file-import')
    app = new MassUploadApp
      baseUrl: '/files'
      csrfToken: window.csrfToken
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
    $form.append(app.el)
