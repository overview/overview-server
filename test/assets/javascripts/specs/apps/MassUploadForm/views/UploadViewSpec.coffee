define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadView'
], ($, Backbone, UploadView) ->
  describe 'apps/MassUploadForm/views/UploadView', ->
    model = undefined
    view = undefined

    describe 'initialize', ->
      it 'responds to the model change by rendering', ->
        model = new Backbone.Model
        renderSpy = spyOn(UploadView.prototype, 'render')
        view = new UploadView(model: model)
        model.trigger('change')

        expect(renderSpy).toHaveBeenCalled()

    describe 'render', ->
      model = undefined
      view = undefined

      init = (isFullyUploaded) ->
        Model = Backbone.Model.extend
          isFullyUploaded: -> isFullyUploaded
        model = new Model(id: 'foo.pdf')
        view = new UploadView(model: model)
        view.render()

      it 'shows the filename', ->
        init()
        expect(view.$el.find('.filename').text()).toEqual('foo.pdf')

      it 'displays a queued but not uploading file, with an icon', ->
        init()
        expect(view.$el).toHaveClass('waiting')
        expect(view.$el).toContain('i.icon-time')

      it 'displays an uploading file', ->
        init()
        model.set('uploading', true)
        expect(view.$el).toHaveClass('uploading')
        expect(view.$el).toContain('i.icon-spinner.icon-spin')

      it 'displays an uploaded file', ->
        init(true)
        model.trigger('change')
        expect(view.$el).toHaveClass('uploaded')
        expect(view.$el).toContain('i.icon-ok')



