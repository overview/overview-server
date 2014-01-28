define [
  'jquery'
  './views/MassUploadForm'
  './models/MassUploadTransport'
  './views/UploadCollectionView'
  'mass-upload'
], ($, MassUploadForm, MassUploadTransport, UploadCollectionView, MassUpload) ->
  class App
    constructor: (options) ->
      model = new MassUpload( MassUploadTransport({url: options.baseUrl, csrfToken: options.csrfToken}) )
      view = new MassUploadForm(
        model: model,
        uploadCollectionViewClass: UploadCollectionView,
        supportedLanguages: options.supportedLanguages,
        defaultLanguageCode: options.defaultLanguageCode
      )
      view.render()
      @el = view.el
