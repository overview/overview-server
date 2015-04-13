define [
  'jquery'
  'backbone'
  'apps/Show/controllers/ViewAppController'
], ($, Backbone, ViewAppController) ->
  class MockState extends Backbone.Model

  class MockView extends Backbone.Model

  describe 'apps/Show/controllers/ViewAppController', ->
    beforeEach ->
      @tags = 'tags'

      @jobView = new MockView(type: 'job')
      @treeView = new MockView(type: 'tree')

      @state = new MockState
        highlightedDocumentListParams: 'highlightedDocumentListParams'
        documentListParams: 'documentListParams'
        document: 'document'
        view: @jobView
      @state.tags = @tags

      @transactionQueue = 'transactionQueue'

      @jobViewApp =
        onDocumentChanged: sinon.spy() # hack! testing the implementation uses ViewAppClient
        remove: sinon.spy()

      @treeViewApp =
        onDocumentChanged: sinon.spy()
        remove: sinon.spy()

      @viewAppConstructors =
        job: sinon.stub().returns(@jobViewApp)
        tree: sinon.stub().returns(@treeViewApp)

      @el = document.createElement('div')

      @keyboardController = {}

      @init = =>
        @subject = new ViewAppController
          state: @state
          transactionQueue: @transactionQueue
          keyboardController: @keyboardController
          viewAppConstructors: @viewAppConstructors
          el: @el

    afterEach ->
      @subject?.stopListening()

    it 'should give each ViewApp a new el', ->
      @state.set(view: @jobView)
      @init()
      args1 = @viewAppConstructors.job.lastCall.args[0]
      expect(args1.el.parentNode).to.eq(@el)
      @state.set(view: @treeView)
      args2 = @viewAppConstructors.tree.lastCall.args[0]
      expect(args2.el).not.to.eq(args1.el)
      expect(args2.el.parentNode).to.eq(@el)

    describe 'starting with a null view', ->
      it 'should not crash', ->
        @state.set(view: null)
        expect(=> @init()).not.to.throw()

    describe 'starting with a job view', ->
      beforeEach ->
        @state.set(view: @jobView)
        @init()

      it 'should construct a viewApp', -> expect(@viewAppConstructors.job).to.have.been.called
      it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@jobViewApp)

      it 'should pass view to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          view: @jobView

      it 'should pass state variables to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          documentListParams: 'documentListParams'
          document: 'document'
          highlightedDocumentListParams: 'highlightedDocumentListParams'

      it 'should pass transactionQueue to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          transactionQueue: @transactionQueue

      it 'should pass keyboardController to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          keyboardController: @keyboardController

      it 'should pass a State to the viewApp', ->
        # These parameters won't work across iframes. We should deprecate them.
        options = @viewAppConstructors.job.lastCall.args[0]
        expect(options.state).to.eq(@state)

      it 'should pass an app facade to the viewApp', ->
        app = @viewAppConstructors.job.lastCall.args[0].app
        expect(app).not.to.be.undefined
        expect(app).to.respondTo('resetDocumentListParams')
        expect(app).to.respondTo('getTag')

      it 'should use ViewAppClient to notify the viewApp of changes', ->
        @state.set(document: 'document2')
        expect(@jobViewApp.onDocumentChanged).to.have.been.calledWith('document2')

      describe 'when a new View is set', ->
        beforeEach -> @state.set(view: @treeView)

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should construct the new viewApp', -> expect(@viewAppConstructors.tree).to.have.been.called
        it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@treeViewApp)

        it 'should use ViewAppClient to notify the new viewApp of changes', ->
          @state.set(document: 'document2')
          expect(@treeViewApp.onDocumentChanged).to.have.been.calledWith('document2')

        it 'should stop notifying the original ViewAppClient of changes', ->
          @state.set(document: 'document2')
          expect(@jobViewApp.onDocumentChanged).not.to.have.been.called

      describe 'when the View changes type', ->
        # This should do the same stuff as "when a new View is set"
        beforeEach -> @jobView.set(type: 'tree')

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should construct the new viewApp', -> expect(@viewAppConstructors.tree).to.have.been.called
        it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@treeViewApp)

      describe 'when the view changes to null', ->
        beforeEach -> @state.set(view: null)

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should set state.viewApp to null', -> expect(@state.get('viewApp')).to.be.null
