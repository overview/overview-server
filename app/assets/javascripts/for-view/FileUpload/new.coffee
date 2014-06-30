define [ 'jquery', 'apps/MassUploadForm/app' ], ($, MassUploadApp) ->
  $ ->
    $form = $('form.pdf-upload')
    baseUrl = $form.attr('action').match(/// ^(.*)/[^/]*$ ///)[1]
    app = new MassUploadApp
      baseUrl: baseUrl
      csrfToken: window.csrfToken
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
    $form.append(app.el)
