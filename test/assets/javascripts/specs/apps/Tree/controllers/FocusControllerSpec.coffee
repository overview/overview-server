define [
  'underscore'
  'backbone'
  'apps/Tree/controllers/FocusController'
], (_, Backbone, FocusController) ->
  describe 'apps/Tree/controllers/FocusController', ->
    beforeEach ->
      @animatedTree =
        getAnimatedNode: sinon.stub()

      @state = new Backbone.Model
        documentListParams: {}

      @focus =
        animateNode: sinon.spy()
        setPanAndZoom: sinon.spy()
        animatePanAndZoom: sinon.spy()

      @treeView = {}
      _.extend(@treeView, Backbone.Events)

      @focusView = {}
      _.extend(@focusView, Backbone.Events)

      @subject = new FocusController
        state: @state
        animatedTree: @animatedTree
        focus: @focus
        treeView: @treeView
        focusView: @focusView

    afterEach ->
      @subject?.stopListening()

    describe 'when selecting a node', ->
      it 'should animate focus to the selected node parent', ->
        parent = {}
        node = { parent: parent }
        @animatedTree.getAnimatedNode.withArgs(4).returns(node)
        @state.set(documentList: { params: { params: { nodes: [4] }}})
        expect(@focus.animateNode).to.have.been.calledWith(parent)

      it 'should animate focus to the root node', ->
        root = {} # no parent
        @animatedTree.getAnimatedNode.withArgs(4).returns(root)
        @state.set(documentList: { params: { params: { nodes: [4] }}})
        expect(@focus.animateNode).to.have.been.calledWith(root)

    describe 'on TreeView zoom-pan', ->
      it 'should animate pan+zoom when options.animate', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3}, { animate: true })
        expect(@focus.animatePanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should set pan+zoom', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        expect(@focus.setPanAndZoom).to.have.been.calledWith(0.1, 0.3)

    describe 'on FocusView zoom-pan', ->
      it 'should animate pan+zoom when options.animate', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3}, { animate: true })
        expect(@focus.animatePanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should set pan+zoom', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        expect(@focus.setPanAndZoom).to.have.been.calledWith(0.1, 0.3)
