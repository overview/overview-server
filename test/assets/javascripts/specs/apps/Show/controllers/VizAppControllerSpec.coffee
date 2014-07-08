define [
  'jquery'
  'backbone'
  'apps/Show/controllers/VizAppController'
], ($, Backbone, VizAppController) ->
  class MockState extends Backbone.Model

  class MockViz extends Backbone.Model

  describe 'apps/Show/controllers/VizAppController', ->
    beforeEach ->
      @tags = 'tags'
      @searchResults = 'searchResults'

      @documentSet = new Backbone.Model # hack! testing VizAppClient can listen to it
      @documentSet.tags = @tags
      @documentSet.searchResults = @searchResults

      @jobViz = new MockViz(type: 'job')
      @treeViz = new MockViz(type: 'tree')

      @state = new MockState
        documentListParams: 'documentListParams'
        document: 'document'
        taglikeCid: 'taglikeCid'
        viz: @jobViz

      @transactionQueue = 'transactionQueue'

      @jobVizApp =
        onDocumentChanged: sinon.spy() # hack! testing the implementation uses VizAppClient
        remove: sinon.spy()

      @treeVizApp =
        onDocumentChanged: sinon.spy()
        remove: sinon.spy()

      @vizAppConstructors =
        job: sinon.stub().returns(@jobVizApp)
        tree: sinon.stub().returns(@treeVizApp)

      @el = document.createElement('div')

      @keyboardController = {}

      @init = =>
        @subject = new VizAppController
          state: @state
          documentSet: @documentSet
          transactionQueue: @transactionQueue
          keyboardController: @keyboardController
          vizAppConstructors: @vizAppConstructors
          el: @el

    afterEach ->
      @subject?.stopListening()

    it 'should give each VizApp a new el', ->
      @state.set(viz: @jobViz)
      @init()
      args1 = @vizAppConstructors.job.lastCall.args[0]
      expect(args1.el.parentNode).to.eq(@el)
      @state.set(viz: @treeViz)
      args2 = @vizAppConstructors.tree.lastCall.args[0]
      expect(args2.el).not.to.eq(args1.el)
      expect(args2.el.parentNode).to.eq(@el)

    describe 'starting with a null viz', ->
      it 'should not crash', ->
        @state.set(viz: null)
        expect(=> @init()).not.to.throw()

    describe 'starting with a job viz', ->
      beforeEach ->
        @state.set(viz: @jobViz)
        @init()

      it 'should construct a vizApp', -> expect(@vizAppConstructors.job).to.have.been.called
      it 'should set state.vizApp', -> expect(@state.get('vizApp')).to.eq(@jobVizApp)

      it 'should pass viz to the vizApp', ->
        expect(@vizAppConstructors.job).to.have.been.calledWithMatch
          viz: @jobViz

      it 'should pass state variables to the vizApp', ->
        expect(@vizAppConstructors.job).to.have.been.calledWithMatch
          documentListParams: 'documentListParams'
          document: 'document'
          taglikeCid: 'taglikeCid'

      it 'should pass transactionQueue to the vizApp', ->
        expect(@vizAppConstructors.job).to.have.been.calledWithMatch
          transactionQueue: @transactionQueue

      it 'should pass keyboardController to the vizApp', ->
        expect(@vizAppConstructors.job).to.have.been.calledWithMatch
          keyboardController: @keyboardController

      it 'should pass a DocumentSet and State to the vizApp', ->
        # These parameters won't work across iframes. We should deprecate them.
        options = @vizAppConstructors.job.lastCall.args[0]
        expect(options.documentSet).to.eq(@documentSet)
        expect(options.state).to.eq(@state)

      it 'should pass an app facade to the vizApp', ->
        app = @vizAppConstructors.job.lastCall.args[0].app
        expect(app).not.to.be.undefined
        expect(app).to.respondTo('resetDocumentListParams')
        expect(app).to.respondTo('getTag')
        expect(app).to.respondTo('getSearchResult')

      it 'should use VizAppClient to notify the vizApp of changes', ->
        @state.set(document: 'document2')
        expect(@jobVizApp.onDocumentChanged).to.have.been.calledWith('document2')

      describe 'when a new Viz is set', ->
        beforeEach -> @state.set(viz: @treeViz)

        it 'should call .remove() on the old vizApp', -> expect(@jobVizApp.remove).to.have.been.called
        it 'should construct the new vizApp', -> expect(@vizAppConstructors.tree).to.have.been.called
        it 'should set state.vizApp', -> expect(@state.get('vizApp')).to.eq(@treeVizApp)

        it 'should use VizAppClient to notify the new vizApp of changes', ->
          @state.set(document: 'document2')
          expect(@treeVizApp.onDocumentChanged).to.have.been.calledWith('document2')

        it 'should stop notifying the original VizAppClient of changes', ->
          @state.set(document: 'document2')
          expect(@jobVizApp.onDocumentChanged).not.to.have.been.called

      describe 'when the Viz changes type', ->
        # This should do the same stuff as "when a new Viz is set"
        beforeEach -> @jobViz.set(type: 'tree')

        it 'should call .remove() on the old vizApp', -> expect(@jobVizApp.remove).to.have.been.called
        it 'should construct the new vizApp', -> expect(@vizAppConstructors.tree).to.have.been.called
        it 'should set state.vizApp', -> expect(@state.get('vizApp')).to.eq(@treeVizApp)

      describe 'when the viz changes to null', ->
        beforeEach -> @state.set(viz: null)

        it 'should call .remove() on the old vizApp', -> expect(@jobVizApp.remove).to.have.been.called
        it 'should set state.vizApp to null', -> expect(@state.get('vizApp')).to.be.null
