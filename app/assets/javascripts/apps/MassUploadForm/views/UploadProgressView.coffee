define [
  'i18n'
  'apps/MassUploadForm/views/AbstractProgressView'
], (i18n, AbstractProgressView) ->
  t = (m, args...) -> i18n("views.DocumentSet._uploadProgress.#{m}", args...)

  AbstractProgressView.extend
    className: 'upload-progress'
    progressProperty: 'uploadProgress'
    errorProperty: 'uploadErrors'
    preamble: -> t('uploading')

    getError: -> @model.get('uploadErrors')?[0]?.error || null
