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
      initialize: (i) ->
        @id = "file#{i}.txt"
        @fileInfo = { name: "file#{i}.txt" }

      isFullyUploaded: -> false

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.drop_target': 'drop_target'
        'views.DocumentSet._massUploadForm.drop_here': 'drop_here'
        'views.DocumentSet._massUploadForm.skipped': 'skipped'

      collection = new Backbone.Collection
      view = new UploadCollectionView(collection: collection)
      view.render()

    afterEach ->
      @sandbox.restore()
      view.remove()

    it 'shows an empty <ul>', ->
      expect(view.$('ul').length).to.eq(1)

    it 'renders an uploadView when a file is added', ->
      collection.add(new MockUpload(0))
      collection.trigger('add-batch', collection.models)
      expect(view.$('li').length).to.eq(1)

    it 'should not crash when the collection emits a "change" event for an upload before an "add" event', ->
      collection.trigger('change', new MockUpload(0))
      expect(view.$('li').length).to.eq(0)

    describe 'with lots of files', ->
      beforeEach ->
        $('body').append(view.$el)
        for i in [ 0 ... 20 ]
          collection.add(new MockUpload(i))
        collection.trigger('add-batch', collection.models)

      afterEach ->
        view.$el.remove()

      it 'shows the filename', ->
        expect(view.$el.find('.filename:eq(0)').text()).to.eq('file0.txt')

      it 'shows waiting', ->
        $li = view.$('li:eq(0)')
        expect($li).to.have.class('waiting')
        expect($li.find('i.icon-clock-o')).to.exist

      it 'shows uploading', ->
        collection.models[0].set(uploading: true)
        $li = view.$('li:eq(0)')
        expect($li).to.have.class('uploading')
        expect($li.find('i.icon.icon-spinner.icon-spin')).to.exist

      it 'shows skipped', ->
        collection.models[0].set(skippedBecauseAlreadyInDocumentSet: true)
        $li = view.$('li:eq(0)')
        expect($li).to.have.class('skipped')
        expect($li.find('i.icon.icon-check')).to.exist
        expect($li.find('.message').text()).to.eq('skipped')

      it 'shows uploaded', ->
        collection.models[0].isFullyUploaded = => true
        collection.trigger('change', collection.models[0])
        $li = view.$('li:eq(0)')
        expect($li).to.have.class('uploaded')
        expect($li.find('i.icon.icon-check')).to.exist
