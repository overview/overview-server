define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadProgressView'
], ($, Backbone, UploadProgressView) ->
  describe 'apps/MassUploadForm/views/UploadProgressView', ->
    model = undefined
    view = undefined

    beforeEach ->
      model = new Backbone.Model()
      view = new UploadProgressView({model: model})

    describe 'getError', ->
      it 'returns the error from the model', ->
        model.set('uploadErrors', [{error: 'an error'}])
        expect(view.getError()).toEqual('an error')
