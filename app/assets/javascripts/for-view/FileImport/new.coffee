define [ 'jquery', 'apps/MassUploadForm/app' ], ($, MassUploadApp) ->
  $ ->
    $form = $('form.file-import')

    options =
      baseUrl: '/files'
      csrfToken: window.csrfToken
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
      onlyOptions: [ 'name', 'lang', 'split_documents' ]

    # rather than split new.coffee and edit.coffee into separate bundles, we
    # use this simple if-statement.
    isReallyEdit = !/\/finish$/.test($form.attr('action'))
    if isReallyEdit
      options.onlyOptions = [ 'lang', 'split_documents' ]

    app = new MassUploadApp(options)
    $form.append(app.el)
