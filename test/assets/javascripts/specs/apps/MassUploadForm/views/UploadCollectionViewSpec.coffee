define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadCollectionView'
  'i18n'
], ($, Backbone, UploadCollectionView, i18n) ->
  describe 'apps/MassUploadForm/views/UploadCollectionView', ->
    collection = undefined
    view = undefined
    class MockUploadView extends Backbone.View
      tagName: 'li'
    uploadViewRenderSpy = undefined

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.drop_target': 'drop_target'
        'views.DocumentSet._massUploadForm.minimum_files': 'minimum_files'

      uploadViewRenderSpy = spyOn(MockUploadView.prototype, 'render').andCallThrough()
      collection = new Backbone.Collection
      view = new UploadCollectionView(collection: collection, uploadViewClass: MockUploadView)
      view.render()

    afterEach ->
      view.remove()

    it 'shows an empty <ul>', ->
      expect(view.$('ul').length).toEqual(1)

    it 'shows a drop target', ->
      expect(view.$('li').text()).toMatch('drop_target')

    it 'renders an uploadView when a file is added', ->
      collection.add(new Backbone.Model)
      expect(uploadViewRenderSpy).toHaveBeenCalled()

    it 'deletes the drop target when another li is added', ->
      collection.add(new Backbone.Model)
      expect(view.$('li').text()).not.toMatch('drop_target')
      expect(view.$('li').length).toEqual(1)
