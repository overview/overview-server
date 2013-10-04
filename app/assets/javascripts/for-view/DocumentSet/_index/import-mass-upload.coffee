define [ 'jquery', 'apps/MassUploadForm/app' ], ($, MassUploadApp) ->
  $ ->
    $('#import-from-mass-upload').one 'activate', ->
      $form = $('#import-from-mass-upload').find('form')
      baseUrl = $form.attr('action').match(/// ^(.*)/[^/]*$ ///)[1]
      app = new MassUploadApp(
        baseUrl: baseUrl,
        csrfToken: window.csrfToken,
        supportedLanguages: window.supportedLanguages,
        defaultLanguageCode: window.defaultLanguageCode
      )
      $form.append(app.el)
