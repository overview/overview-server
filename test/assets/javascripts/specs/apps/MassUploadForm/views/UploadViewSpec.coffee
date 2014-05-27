define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadView'
], ($, Backbone, UploadView) ->
  describe 'apps/MassUploadForm/views/UploadView', ->
    model = undefined
    view = undefined

    init = (attrs) ->
      class Model extends Backbone.Model
        defaults:
          isFullyUploaded: false
          uploading: false
        isFullyUploaded: -> @get('isFullyUploaded')
      model = new Model($.extend({ id: 'foo.pdf' }, attrs ? {}))
      view = new UploadView(model: model)
      view.render()

    it 'shows the filename', ->
      init()
      expect(view.$el.find('.filename').text()).to.eq('foo.pdf')

    describe 'waiting', ->
      beforeEach -> init()
      it 'has class waiting', -> expect(view.$el).to.have.class('waiting')
      it 'has a wait icon', -> expect(view.$el).to.have('i.icon-time')

      describe 'transitioning to uploading', ->
        beforeEach -> model.set(uploading: true)
        it 'has class uploading', -> expect(view.$el).to.have.class('uploading')
        it 'has a spinner', -> expect(view.$el).to.have('i.icon-spin.icon-spinner')

    describe 'uploading', ->
      beforeEach -> init(uploading: true)
      it 'has class uploading', -> expect(view.$el).to.have.class('uploading')
      it 'has a spinner', -> expect(view.$el).to.have('i.icon-spin.icon-spinner')

    describe 'fully uploaded', ->
      beforeEach -> init(isFullyUploaded: true)
      it 'has class uploaded', -> expect(view.$el).to.have.class('uploaded')
      it 'has an ok icon', -> expect(view.$el).to.have('i.icon-ok')
