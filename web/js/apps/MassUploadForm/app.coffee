define [
  'jquery'
  './views/MassUploadForm'
  './models/MassUploadTransport'
  './views/UploadCollectionView'
  './views/RedirectConfirmer'
  'mass-upload'
], ($, MassUploadForm, MassUploadTransport, UploadCollectionView, RedirectConfirmer, MassUpload) ->
  class App
    constructor: (options) ->
      throw 'Must pass options.baseUrl, a String like "/files"' if !options.baseUrl?
      throw 'Must pass options.csrfToken, a String' if !options.csrfToken?
      # *May* pass options.uniqueCheckUrlPrefix, a String like "/documentsets/2/files"

      transport = MassUploadTransport
        url: options.baseUrl
        uniqueCheckUrlPrefix: options.uniqueCheckUrlPrefix
        csrfToken: options.csrfToken

      model = new MassUpload(transport)

      view = new MassUploadForm
        model: model
        uploadCollectionViewClass: UploadCollectionView
        supportedLanguages: options.supportedLanguages
        defaultLanguageCode: options.defaultLanguageCode
        onlyOptions: options.onlyOptions
        documentSet: options.documentSet

      view.render()

      redirectConfirmer = new RedirectConfirmer
        model: model
        redirectFunctions:
          href: (x) -> window.location.href = x
          hash: (x) -> window.location.hash = x
      redirectConfirmer.render()

      # Make the cancel button work
      view.on('cancel', -> redirectConfirmer.tryPromptAndRedirect(hash: ''))

      @el = view.el
