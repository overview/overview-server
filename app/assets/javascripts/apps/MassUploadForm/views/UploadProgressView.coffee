define [
  'i18n'
  'apps/MassUploadForm/views/AbstractProgressView'
], (i18n, AbstractProgressView) ->
  t = i18n.namespaced('views.DocumentSet._uploadProgress')

  AbstractProgressView.extend
    className: 'upload-progress'
    progressProperty: 'uploadProgress'
    errorProperty: 'uploadErrors'
    preamble: -> t('uploading')

    getError: -> @model.get('uploadErrors')?[0]?.error || null
