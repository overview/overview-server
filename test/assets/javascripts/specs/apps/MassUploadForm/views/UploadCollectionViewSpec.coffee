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
        @fileInfo = { name: 'file.txt' }

      isFullyUploaded: -> false

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.drop_target': 'drop_target'
        'views.DocumentSet._massUploadForm.skipped': 'skipped'

      collection = new Backbone.Collection
      view = new UploadCollectionView(collection: collection)
      view.render()

    afterEach ->
      @sandbox.restore()
      view.remove()

    it 'shows an empty <ul>', ->
      expect(view.$('ul').length).to.eq(1)

    it 'shows a drop target', ->
      expect(view.$el.text()).to.contain('drop_target')

    it 'renders an uploadView when a file is added', ->
      collection.add(new MockUpload())
      collection.trigger('add-batch', collection.models)
      expect(view.$('li').length).to.eq(1)

    it 'deletes the drop target when another li is added', ->
      collection.add(new MockUpload())
      collection.trigger('add-batch', collection.models)
      expect(view.$el.text()).not.to.contain('drop_target')

    it 'should not crash when the collection emits a "change" event for an upload before an "add" event', ->
      collection.trigger('change', new MockUpload())
      expect(view.$('li').length).to.eq(0)

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

      it 'should set the <ul> height appropriately', ->
        expect(view.$('ul').height()).to.eq(320)

      it 'should empty on reset', ->
        collection.reset()
        expect(view.$('li').length).to.eq(0)
        expect(view.$el.text()).to.contain('drop_target')

      it 'should remove the drop target after a non-empty reset', ->
        collection.reset([ new MockUpload, new MockUpload ])
        expect(view.$('li').length).to.eq(2)
        expect(view.$el.text()).not.to.contain('drop_target')

      it 'shows the filename', ->
        expect(view.$el.find('.filename:eq(0)').text()).to.eq('file.txt')

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
