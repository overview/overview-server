define [
  'jquery'
  'backbone'
  'i18n'
  'apps/MassUploadForm/views/UploadProgressView'
], ($, Backbone, i18n, UploadProgressView) ->
  describe 'apps/MassUploadForm/views/UploadProgressView', ->
    model = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet._uploadProgress.uploading': 'uploading'

      model = new Backbone.Model()
      view = new UploadProgressView({model: model})

    describe 'getError', ->
      it 'returns the error from the model', ->
        model.set('uploadErrors', [{error: 'an error'}])
        expect(view.getError()).to.eq('an error')
