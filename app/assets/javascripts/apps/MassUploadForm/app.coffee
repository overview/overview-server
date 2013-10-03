define [
  'jquery'
  './views/MassUploadForm'
  './models/MassUploadTransport'
  './views/UploadView'
  'mass-upload'
], ($, MassUploadForm, MassUploadTransport, UploadView, MassUpload) ->
  class App
    constructor: (url, csrfToken) ->
      model = new MassUpload( MassUploadTransport({url: url, csrfToken: csrfToken}) )
      view = new MassUploadForm(model: model, uploadViewClass: UploadView)
      view.render()
      @el = view.el
