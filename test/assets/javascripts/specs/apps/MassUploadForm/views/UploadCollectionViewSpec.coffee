define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/UploadCollectionView'
  'i18n'
], ($, Backbone, UploadCollectionView, i18n) ->
  describe 'apps/MassUploadForm/views/UploadCollectionView', ->
    collection = undefined
    view = undefined

    class MockUpload extends Backbone.Model
      initialize: ->
        @id = @cid

    class MockUploadView extends Backbone.View
      tagName: 'li'
      attributes:
        style: 'height: 30px'

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.drop_target': 'drop_target'
        'views.DocumentSet._massUploadForm.minimum_files': 'minimum_files'

      # sinon.spy() causes a big slowdown here. Emulate it.
      @oldRender = MockUploadView.prototype.render
      @nRenders = 0
      MockUploadView.prototype.render = => @nRenders += 1
      collection = new Backbone.Collection
      view = new UploadCollectionView(collection: collection, uploadViewClass: MockUploadView)
      view.render()

    afterEach ->
      @sandbox.restore()
      view.remove()

    it 'shows an empty <ul>', ->
      expect(view.$('ul').length).to.eq(1)

    it 'shows a drop target', ->
      expect(view.$('li').text()).to.contain('drop_target')

    it 'renders an uploadView when a file is added', ->
      collection.add(new MockUpload())
      collection.trigger('add-batch', collection.models)
      expect(@nRenders).to.eq(1)

    it 'deletes the drop target when another li is added', ->
      collection.add(new MockUpload())
      collection.trigger('add-batch', collection.models)
      expect(view.$('li').text()).not.to.contain('drop_target')
      expect(view.$('li').length).to.eq(1)

    it 'should not crash when the collection emits a "change" event for an upload before an "add" event', ->
      collection.trigger('change', new MockUpload())
      expect(@nRenders).to.eq(0)

    describe 'with lots of files', ->
      beforeEach ->
        # 20 files; 30px per file; 200px visible
        view.$el.css
          height: '200px'
          overflow: 'auto'

        $('body').append(view.$el)
        for i in [ 0 ... 20 ]
          collection.add(new MockUpload())
        collection.trigger('add-batch', collection.models)

      afterEach ->
        view.$el.remove()

      it 'should set the <ul> min-height appropriately', ->
        expect(view.$('ul').css('min-height')).to.eq('600px')

      it 'should only render() the visible uploads', ->
        # The intent is that we don't initialize the view at all; testing
        # whether it's rendered is a quick hack so we don't need to mock
        # the constructor.
        expect(@nRenders).to.eq(7)

      it 'should render() more uploads as the user scrolls', ->
        view.$el.scrollTop(100)
        view.$el.scroll() # call the event
        expect(@nRenders).to.eq(10)

      it 'should scroll to the most-recently-changed element', ->
        collection.at(11).set(foo: 'bar')
        expect(view.$el.scrollTop()).to.eq(190)
        expect(@nRenders).to.eq(13)

      it 'should empty on reset', ->
        collection.reset()
        $li = view.$('li')
        expect($li.length).to.eq(1)
        expect($li.text()).to.contain('drop_target')

      it 'should remove the drop target after a non-empty reset', ->
        collection.reset([ new MockUpload, new MockUpload ])
        expect(view.$('li').length).to.eq(2)
        expect(view.$('ul').text()).not.to.contain('drop_target')
