define [ 'jquery', 'apps/MassUploadForm/app' ], ($, MassUploadApp) ->
  $ ->
    $('#import-from-mass-upload').one 'activate', ->
      $form = $('#import-from-mass-upload').find('form')
      baseUrl = $form.attr('action').match(/// ^(.*)/[^/]*$ ///)[1]
      app = new MassUploadApp(baseUrl, window.csrfToken)
      $form.append(app.el)
