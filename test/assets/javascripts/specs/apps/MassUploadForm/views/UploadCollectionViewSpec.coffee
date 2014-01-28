define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadCollectionView'
  'i18n'
], ($, Backbone, UploadCollectionView, i18n) ->
  describe 'apps/MassUploadForm/views/UploadCollectionView', ->
    collection = undefined
    view = undefined
    uploadViewRenderSpy = undefined

    class MockUploadView extends Backbone.View
      tagName: 'li'
      attributes:
        style: 'height: 30px'

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
      collection.trigger('add-batch', collection.models)
      expect(uploadViewRenderSpy).toHaveBeenCalled()

    it 'deletes the drop target when another li is added', ->
      collection.add(new Backbone.Model)
      collection.trigger('add-batch', collection.models)
      expect(view.$('li').text()).not.toMatch('drop_target')
      expect(view.$('li').length).toEqual(1)

    it 'should not crash when the collection emits a "change" event for an upload before an "add" event', ->
      collection.trigger('change', new Backbone.Model)
      expect(uploadViewRenderSpy).not.toHaveBeenCalled()

    describe 'with lots of files', ->
      beforeEach ->
        # 20 files; 30px per file; 200px visible
        view.$el.css
          height: '200px'
          overflow: 'auto'

        $('body').append(view.$el)
        for i in [ 0 ... 20 ]
          collection.add(new Backbone.Model)
        collection.trigger('add-batch', collection.models)

      afterEach ->
        view.$el.remove()

      it 'should set the <ul> height appropriately', ->
        expect(view.$('ul').css('height')).toEqual('600px')

      it 'should only render() the visible uploads', ->
        # The intent is that we don't initialize the view at all; testing
        # whether it's rendered is a quick hack so we don't need to mock
        # the constructor.
        expect(uploadViewRenderSpy.calls.length).toEqual(7)

      it 'should render() more uploads as the user scrolls', ->
        view.$el.scrollTop(100)
        view.$el.scroll() # call the event
        expect(uploadViewRenderSpy.calls.length).toEqual(10)

      it 'should scroll to the most-recently-changed element', ->
        collection.at(11).set(foo: 'bar')
        expect(view.$el.scrollTop()).toEqual(190)
        expect(uploadViewRenderSpy.calls.length).toEqual(13)
