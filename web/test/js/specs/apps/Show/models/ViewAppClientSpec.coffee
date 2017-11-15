define [
  'backbone'
  'apps/Show/models/ViewAppClient'
], (Backbone, ViewAppClient) ->
  class MockState extends Backbone.Model
    defaults:
      documentList: { params: 'foo' }
      document: 'bar'

  describe 'apps/Show/models/ViewAppClient', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub(console, 'log')
      @state = new MockState()
      @state.documentSet = new Backbone.Model(foo: 'bar')

      @globalActions =
        openMetadataSchemaEditor: sinon.spy()


    afterEach ->
      @sandbox.restore()

    describe 'with a complete viewApp', ->
      beforeEach ->
        @viewApp =
          onDocumentListParamsChanged: sinon.spy()
          onDocumentSetChanged: sinon.spy()
          onDocumentChanged: sinon.spy()
          notifyDocumentListParams: sinon.spy()
          notifyDocumentSet: sinon.spy()
          notifyDocument: sinon.spy()
          postMessageToPluginIframes: sinon.spy()
          setRightPane: sinon.spy()
          setModalDialog: sinon.spy()
          onTag: sinon.spy()
          onUntag: sinon.spy()
          remove: sinon.spy()

        @subject = new ViewAppClient
          globalActions: @globalActions
          state: @state
          viewApp: @viewApp

      afterEach -> @subject.stopListening()

      it 'should invoke onDocumentListParamsChanged', ->
        @state.set(documentList: { params: 'baz' })
        expect(@viewApp.onDocumentListParamsChanged).to.have.been.calledWith('baz')

      it 'should invoke onDocumentChanged', ->
        document = new Backbone.Model(foo: 'bar')
        @state.set(document: document)
        expect(@viewApp.onDocumentChanged).to.have.been.calledWith(document)

      it 'should invoke onDocumentChanged when the document attributes change', ->
        document = new Backbone.Model(foo: 'bar')
        @state.set(document: document)
        @state.get('document').set(foo: 'baz')
        expect(@viewApp.onDocumentChanged).to.have.been.called.twice

      it 'should invoke onDocumentSetChanged', ->
        @state.documentSet.set(foo: 'baz')
        expect(@viewApp.onDocumentSetChanged).to.have.been.calledWith(@state.documentSet)

      it 'should invoke onTag', ->
        @state.trigger('tag', 'foo', 'bar')
        expect(@viewApp.onTag).to.have.been.calledWith('foo', 'bar')

      it 'should invoke onUntag', ->
        @state.trigger('untag', 'foo', 'bar')
        expect(@viewApp.onUntag).to.have.been.calledWith('foo', 'bar')

      it 'should notify documentListParams', ->
        @subject._onMessage(origin: '', data: { call: 'notifyDocumentListParams' })
        expect(@viewApp.notifyDocumentListParams).to.have.been.calledWith(@state.get('documentList').params)

      it 'should notify documentSet', ->
        @subject._onMessage(origin: '', data: { call: 'notifyDocumentSet' })
        expect(@viewApp.notifyDocumentSet).to.have.been.calledWith(@state.documentSet)

      it 'should notify document', ->
        @subject._onMessage(origin: '', data: { call: 'notifyDocument' })
        expect(@viewApp.notifyDocument).to.have.been.calledWith(@state.get('document'))

      it 'should postMessageToPluginIframes', ->
        @subject._onMessage(origin: '', data: { call: 'postMessageToPluginIframes', message: 'hello, world' })
        expect(@viewApp.postMessageToPluginIframes).to.have.been.calledWith('hello, world')

      it 'should setRightPane', ->
        @subject._onMessage(origin: '', data: { call: 'setRightPane', args: [ { url: 'http://example.com' } ] })
        expect(@viewApp.setRightPane).to.have.been.calledWith({ url: 'http://example.com' })

      it 'should setModalDialog', ->
        @subject._onMessage(origin: '', data: { call: 'setModalDialog', args: [ { url: 'http://example.com' } ] })
        expect(@viewApp.setModalDialog).to.have.been.calledWith({ url: 'http://example.com' })

      it 'should set metadata on document', ->
        document = new Backbone.Model(id: 2, metadata: { foo: 'bar' })
        document.save = sinon.spy()
        @state.set(document: document)
        @subject._onMessage(origin: '', data: { call: 'patchDocument', args: [ { id: 2, metadata: { foo: 'baz' } } ] })
        expect(document.save).to.have.been.calledWith({ metadata: { foo: 'baz' } }, patch: true)

      it 'should refuse to set metadata on the wrong document', ->
        document = new Backbone.Model(id: 2, metadata: { foo: 'bar' })
        document.save = sinon.spy()
        @state.set(document: document)
        @subject._onMessage(origin: '', data: { call: 'patchDocument', args: [ { id: 1, metadata: { foo: 'baz' } } ] })
        expect(document.save).not.to.have.been.called

      describe 'on remove', ->
        beforeEach -> @subject.remove()

        it 'should invoke remove', -> expect(@viewApp.remove).to.have.been.called

        it 'should stop listening to the viewApp', ->
          @subject.remove()

          @state.set
            documentList: 'foo2'
            document: new Backbone.Model(foo: 'bar2')
          @state.trigger('tag', 'foo', 'bar')
          @state.trigger('untag', 'foo', 'bar')

          expect(@viewApp.onDocumentListParamsChanged).not.to.have.been.called
          expect(@viewApp.onDocumentChanged).not.to.have.been.called
          expect(@viewApp.onTag).not.to.have.been.called
          expect(@viewApp.onUntag).not.to.have.been.called

        it 'should invoke globalActions.openMetadataSchemaEditor()', ->
          @subject._onMessage(origin: '', data: { call: 'openMetadataSchemaEditor' })
          expect(@globalActions.openMetadataSchemaEditor).to.have.been.called

    describe 'with an viewApp missing methods', ->
      beforeEach ->
        @viewApp =
          remove: sinon.spy()

        @subject = new ViewAppClient
          globalActions: @globalActions
          state: @state
          viewApp: @viewApp

      afterEach -> @subject.stopListening()

      it 'should do nothing', ->
        @state.set
          documentList: 'foo2'
          document: new Backbone.Model(foo: 'bar2')
        @state.documentSet.set(foo: 'baz')
        @state.trigger('tag', 'foo', 'bar')
        @state.trigger('untag', 'foo', 'bar')
        expect(true).to.be.true # really, we're just testing nothing crashes

    it 'should throw an error if the viewApp has no remove function', ->
      expect(=>
        new ViewAppClient
          globalActions: @globalActions
          state: @state
          viewApp: {}
      ).to.throw('options.viewApp needs a remove() method which removes all traces of the view')
