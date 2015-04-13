define [
  'backbone'
  'apps/Show/models/ViewAppClient'
], (Backbone, ViewAppClient) ->
  class MockState extends Backbone.Model
    defaults:
      documentListParams: 'foo'
      document: 'bar'
      highlightedDocumentListParams: 'baz'

  describe 'apps/Show/models/ViewAppClient', ->
    beforeEach ->
      @state = new MockState()

    describe 'with a complete viewApp', ->
      beforeEach ->
        @viewApp =
          onDocumentListParamsChanged: sinon.spy()
          onDocumentChanged: sinon.spy()
          onHighlightedDocumentListParamsChanged: sinon.spy()
          onTag: sinon.spy()
          onUntag: sinon.spy()
          remove: sinon.spy()

        @subject = new ViewAppClient
          state: @state
          viewApp: @viewApp

      afterEach -> @subject.stopListening()

      it 'should invoke onDocumentListParamsChanged', ->
        @state.set(documentListParams: 'baz')
        expect(@viewApp.onDocumentListParamsChanged).to.have.been.calledWith('baz')

      it 'should invoke onDocumentChanged', ->
        @state.set(document: 'baz')
        expect(@viewApp.onDocumentChanged).to.have.been.calledWith('baz')

      it 'should invoke onHighlightedDocumentListParamsChanged', ->
        @state.set(highlightedDocumentListParams: 'baz2')
        expect(@viewApp.onHighlightedDocumentListParamsChanged).to.have.been.calledWith('baz2')

      it 'should invoke onTag', ->
        @state.trigger('tag', 'foo', 'bar')
        expect(@viewApp.onTag).to.have.been.calledWith('foo', 'bar')

      it 'should invoke onUntag', ->
        @state.trigger('untag', 'foo', 'bar')
        expect(@viewApp.onUntag).to.have.been.calledWith('foo', 'bar')

      describe 'on remove', ->
        beforeEach -> @subject.remove()

        it 'should invoke remove', -> expect(@viewApp.remove).to.have.been.called

        it 'should stop listening to the viewApp', ->
          @subject.remove()

          @state.set
            documentListParams: 'foo2'
            document: 'bar2'
            highlightedDocumentListParams: 'baz2'
          @state.trigger('tag', 'foo', 'bar')
          @state.trigger('untag', 'foo', 'bar')

          expect(@viewApp.onDocumentListParamsChanged).not.to.have.been.called
          expect(@viewApp.onDocumentChanged).not.to.have.been.called
          expect(@viewApp.onHighlightedDocumentListParamsChanged).not.to.have.been.called
          expect(@viewApp.onTag).not.to.have.been.called
          expect(@viewApp.onUntag).not.to.have.been.called

    describe 'with an viewApp missing methods', ->
      beforeEach ->
        @viewApp =
          remove: sinon.spy()

        @subject = new ViewAppClient
          state: @state
          viewApp: @viewApp

      afterEach -> @subject.stopListening()

      it 'should do nothing', ->
        @state.set
          documentListParams: 'foo2'
          document: 'bar2'
          highlightedDocumentListParams: 'baz2'
        @state.trigger('tag', 'foo', 'bar')
        @state.trigger('untag', 'foo', 'bar')
        expect(true).to.be.true # really, we're just testing nothing crashes

    it 'should throw an error if the viewApp has no remove function', ->
      expect(=>
        new ViewAppClient
          state: @state
          viewApp: {}
      ).to.throw('options.viewApp needs a remove() method which removes all traces of the view')
