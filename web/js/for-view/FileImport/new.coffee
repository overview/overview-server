define [
  'jquery'
  'apps/MassUploadForm/app'
  'apps/Show/models/DocumentSet'
], ($, MassUploadApp, DocumentSet) ->
  $ ->
    $form = $('form.file-import')

    options =
      baseUrl: '/files'
      csrfToken: window.csrfToken
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
      onlyOptions: [ 'name', 'lang', 'split_documents', 'ocr', 'metadata_json' ]

    go = ->
      app = new MassUploadApp(options)
      $form.append(app.el)

    # rather than split new.coffee and edit.coffee into separate bundles, we
    # use this simple if-statement.
    isReallyEdit = !/\/finish$/.test($form.attr('action'))
    if isReallyEdit
      options.onlyOptions = [ 'lang', 'split_documents', 'ocr', 'metadata_json' ]
      documentSetId = $form.attr('action').split('/').pop()
      options.uniqueCheckUrlPrefix = "/documentsets/#{documentSetId}/files"
      options.documentSet = new DocumentSet(id: documentSetId)
      options.documentSet.fetch()
      options.documentSet.once('sync', go)
    else
      go()
