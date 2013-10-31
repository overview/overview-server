define [ 'apps/MassUploadForm/views/AbstractProgressView' ], (AbstractProgressView) ->
  # When listing files, shows a progress bar
  AbstractProgressView.extend
    className: 'upload-progress'
    progressProperty: 'uploadProgress'
    errorProperty: 'uploadErrors'
    preamble: 'Synchronization progress'

    getError: -> @model.get('uploadErrors')?[0]?.error || null
