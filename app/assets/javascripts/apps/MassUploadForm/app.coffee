define [
  'jquery'
  './views/MassUploadForm'
  './models/MassUploadTransport'
  './views/UploadView'
  'mass-upload'
], ($, MassUploadForm, MassUploadTransport, UploadView, MassUpload) ->
  class App
    constructor: (options) ->
      model = new MassUpload( MassUploadTransport({url: options.baseUrl, csrfToken: options.csrfToken}) )
      view = new MassUploadForm(
        model: model,
        uploadViewClass: UploadView,
        supportedLanguages: options.supportedLanguages,
        defaultLanguageCode: options.defaultLanguageCode
      )
      view.render()
      @el = view.el
