define [
  'backbone'
  'apps/Show/models/VizAppClient'
], (Backbone, VizAppClient) ->
  class MockState extends Backbone.Model
    defaults:
      documentListParams: 'foo'
      document: 'bar'
      taglikeCid: 'c1'

  class DocumentSet extends Backbone.Model

  describe 'apps/Show/models/VizAppClient', ->
    beforeEach ->
      @state = new MockState()
      @documentSet = new DocumentSet()

    describe 'with a complete vizApp', ->
      beforeEach ->
        @vizApp =
          onDocumentListParamsChanged: sinon.spy()
          onDocumentChanged: sinon.spy()
          onTaglikeCidChanged: sinon.spy()
          onTag: sinon.spy()
          onUntag: sinon.spy()
          remove: sinon.spy()

        @subject = new VizAppClient
          state: @state
          documentSet: @documentSet
          vizApp: @vizApp

      afterEach -> @subject.stopListening()

      it 'should invoke onDocumentListParamsChanged', ->
        @state.set(documentListParams: 'baz')
        expect(@vizApp.onDocumentListParamsChanged).to.have.been.calledWith('baz')

      it 'should invoke onDocumentChanged', ->
        @state.set(document: 'baz')
        expect(@vizApp.onDocumentChanged).to.have.been.calledWith('baz')

      it 'should invoke onTaglikeCidChanged', ->
        @state.set(taglikeCid: 'c2')
        expect(@vizApp.onTaglikeCidChanged).to.have.been.calledWith('c2')

      it 'should invoke onTag', ->
        @documentSet.trigger('tag', 'foo', 'bar')
        expect(@vizApp.onTag).to.have.been.calledWith('foo', 'bar')

      it 'should invoke onUntag', ->
        @documentSet.trigger('untag', 'foo', 'bar')
        expect(@vizApp.onUntag).to.have.been.calledWith('foo', 'bar')

      describe 'on remove', ->
        beforeEach -> @subject.remove()

        it 'should invoke remove', -> expect(@vizApp.remove).to.have.been.called

        it 'should stop listening to the vizApp', ->
          @subject.remove()

          @state.set
            documentListParams: 'baz'
            document: 'baz'
            taglikeCid: 'c2'
          @documentSet.trigger('tag', 'foo', 'bar')
          @documentSet.trigger('untag', 'foo', 'bar')

          expect(@vizApp.onDocumentListParamsChanged).not.to.have.been.called
          expect(@vizApp.onDocumentChanged).not.to.have.been.called
          expect(@vizApp.onTaglikeCidChanged).not.to.have.been.called
          expect(@vizApp.onTag).not.to.have.been.called
          expect(@vizApp.onUntag).not.to.have.been.called

    describe 'with an vizApp missing methods', ->
      beforeEach ->
        @vizApp =
          remove: sinon.spy()

        @subject = new VizAppClient
          state: @state
          documentSet: @documentSet
          vizApp: @vizApp

      afterEach -> @subject.stopListening()

      it 'should do nothing', ->
        @state.set
          documentListParams: 'baz'
          document: 'baz'
          taglikeCid: 'c2'
        @documentSet.trigger('tag', 'foo', 'bar')
        @documentSet.trigger('untag', 'foo', 'bar')
        expect(true).to.be.true # really, we're just testing nothing crashes

    it 'should throw an error if the vizApp has no remove function', ->
      expect(=>
        new VizAppClient
          state: @state
          documentSet: @documentSet
          vizApp: {}
      ).to.throw('options.vizApp needs a remove() method which removes all traces of the viz')
