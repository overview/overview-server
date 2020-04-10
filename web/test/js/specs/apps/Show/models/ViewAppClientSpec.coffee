define [
  'backbone'
  'apps/Show/models/ViewAppClient'
], (Backbone, ViewAppClient) ->
  class MockDocumentList extends Backbone.Model
    initialize: ->
      @params = 'foo'
      @documents = new Backbone.Collection([])

    defaults:
      length: 3

  class MockState extends Backbone.Model
    defaults:
      document: null

  describe 'apps/Show/models/ViewAppClient', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub(console, 'log') # some of these tests cause spurious messages
      @state = new MockState()
      @state.documentSet = new Backbone.Model(foo: 'bar')
      @state.set('documentList', new MockDocumentList())
      @state.set('document', new Backbone.Model({ bar: 'baz' }))

      @globalActions =
        openMetadataSchemaEditor: sinon.spy()
        goToNextDocument: sinon.spy()
        goToPreviousDocument: sinon.spy()
        beginCreatePdfNote: sinon.spy()
        goToPdfNote: sinon.spy()

      @anInterestingDocument = new Backbone.Model({
        id: 123,
        title: 'foo',
        snippet: 'ba--snip--r',
        pageNumber: null,
        url: 'http://example.org',
        metadata: { foo: 'bar' },
        pdfNotes: [],
        isFromOcr: true,
      })

    afterEach ->
      @sandbox.restore()

    describe 'with a complete viewApp', ->
      beforeEach ->
        @viewApp =
          onDocumentListParamsChanged: sinon.spy()
          onDocumentListChanged: sinon.spy()
          onDocumentSetChanged: sinon.spy()
          onDocumentChanged: sinon.spy()
          notifyDocumentListParams: sinon.spy()
          notifyDocumentList: sinon.spy()
          notifyDocumentSet: sinon.spy()
          notifyDocument: sinon.spy()
          postMessageToPluginIframes: sinon.spy()
          setRightPane: sinon.spy()
          setModalDialog: sinon.spy()
          setDocumentDetailLink: sinon.spy()
          setViewFilter: sinon.spy()
          setViewFilterChoices: sinon.spy()
          setTitle: sinon.spy()
          onTag: sinon.spy()
          onUntag: sinon.spy()
          remove: sinon.spy()
          view: { id: 'view-1234' }

        @subject = new ViewAppClient
          globalActions: @globalActions
          state: @state
          viewApp: @viewApp

      afterEach -> @subject.stopListening()

      it 'should invoke onDocumentListParamsChanged', ->
        newList = new MockDocumentList()
        newList.params = 'baz'
        @state.set(documentList: newList)
        expect(@viewApp.onDocumentListParamsChanged).to.have.been.calledWith('baz')

      it 'should invoke onDocumentListChanged on changes to existing documentList', ->
        @state.get('documentList').set(length: 6)
        expect(@viewApp.onDocumentListChanged).to.have.been.calledWith(length: 6)

      it 'should invoke onDocumentListChanged when state is set to new documentList', ->
        @state.set('documentList', new MockDocumentList())
        expect(@viewApp.onDocumentListChanged).to.have.been.calledWith(length: 3)

      it 'should invoke onDocumentChanged', ->
        @state.get('documentList').documents.add([ {}, {}, @anInterestingDocument, {}, {} ])
        @state.set(document: @anInterestingDocument)
        expect(@viewApp.onDocumentChanged).to.have.been.calledWith(Object.assign({}, @anInterestingDocument.attributes, {
          indexInDocumentList: 2,
        }))

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

      it 'should notify documentList', ->
        @subject._onMessage(origin: '', data: { call: 'notifyDocumentList' })
        expect(@viewApp.notifyDocumentList).to.have.been.calledWith(length: 3)

      it 'should notify documentSet', ->
        @subject._onMessage(origin: '', data: { call: 'notifyDocumentSet' })
        expect(@viewApp.notifyDocumentSet).to.have.been.calledWith(@state.documentSet)

      it 'should notify document', ->
        @state.set('document', @anInterestingDocument)
        @state.get('documentList').documents.add([ {}, {}, @anInterestingDocument, {}, {} ])
        @viewApp.notifyDocument = sinon.spy() # Ignore any previous messages
        @subject._onMessage(origin: '', data: { call: 'notifyDocument' })
        expect(@viewApp.notifyDocument).to.have.been.calledWith(Object.assign({}, @anInterestingDocument.attributes, {
          indexInDocumentList: 2,
        }))

      it 'should postMessageToPluginIframes', ->
        @subject._onMessage(origin: '', data: { call: 'postMessageToPluginIframes', message: 'hello, world' })
        expect(@viewApp.postMessageToPluginIframes).to.have.been.calledWith('hello, world')

      it 'should setRightPane', ->
        @subject._onMessage(origin: '', data: { call: 'setRightPane', args: [ { url: 'http://example.com' } ] })
        expect(@viewApp.setRightPane).to.have.been.calledWith({ url: 'http://example.com' })

      it 'should setModalDialog', ->
        @subject._onMessage(origin: '', data: { call: 'setModalDialog', args: [ { url: 'http://example.com' } ] })
        expect(@viewApp.setModalDialog).to.have.been.calledWith({ url: 'http://example.com' })

      it 'should setViewTitle', ->
        @subject._onMessage(origin: '', data: { call: 'setViewTitle', args: [ { title: 'bar' } ] })
        expect(@viewApp.setTitle).to.have.been.calledWith('bar')

      it 'should setDocumentDetailLink', ->
        @subject._onMessage(origin: '', data: { call: 'setDocumentDetailLink', args: [ { foo: 'bar' } ] })
        expect(@viewApp.setDocumentDetailLink).to.have.been.calledWith({ foo: 'bar' })

      it 'should setViewFilter', ->
        @subject._onMessage(origin: '', data: { call: 'setViewFilter', args: [ { foo: 'bar' } ] })
        expect(@viewApp.setViewFilter).to.have.been.calledWith({ foo: 'bar' })

      it 'should setViewFilterChoices', ->
        choices = [ { id: 'foo', name: 'Foo', color: '#abcdef' } ]
        @subject._onMessage(origin: '', data: { call: 'setViewFilterChoices', args: [ choices ] })
        expect(@viewApp.setViewFilterChoices).to.have.been.calledWith(choices)

      it 'should setViewFilterSelection', ->
        selection = { ids: [ 'foo', 'bar' ], operation: 'any' }
        @state.setViewFilterSelection = sinon.spy()
        @subject._onMessage(origin: '', data: { call: 'setViewFilterSelection', args: [ selection ] })
        expect(@state.setViewFilterSelection).to.have.been.calledWith('view-1234', selection) # TODO make it '1234', not 'view-1234'

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
            documentList: new MockDocumentList()
            document: new Backbone.Model(foo: 'bar2')
          @state.trigger('tag', 'foo', 'bar')
          @state.trigger('untag', 'foo', 'bar')

          expect(@viewApp.onDocumentListParamsChanged).not.to.have.been.called
          expect(@viewApp.onDocumentChanged).not.to.have.been.called
          expect(@viewApp.onTag).not.to.have.been.called
          expect(@viewApp.onUntag).not.to.have.been.called

        [ 'openMetadataSchemaEditor', 'goToNextDocument', 'goToPreviousDocument', 'beginCreatePdfNote' ].forEach (action) =>
          it "should invoke globalActions.#{action}()", ->
            @subject._onMessage(origin: '', data: { call: action })
            expect(@globalActions[action]).to.have.been.called

        it 'should invoke globalActions.goToPdfNote', ->
          @subject._onMessage(origin: '', data: { call: 'goToPdfNote', args: [ { pdfNote: 'TEST' } ] })
          expect(@globalActions.goToPdfNote).to.have.been.calledWith('TEST')

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
          documentList: new MockDocumentList()
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
