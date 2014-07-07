define [
  'underscore'
  'backbone'
  'apps/Tree/controllers/FocusController'
], (_, Backbone, FocusController) ->
  describe 'apps/Tree/controllers/FocusController', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeTimers: true)

      @animatedTree =
        getAnimatedNode: sinon.stub()

      @log = sinon.spy()

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
        log: @log
        state: @state
        animatedTree: @animatedTree
        focus: @focus
        treeView: @treeView
        focusView: @focusView

    afterEach ->
      @sandbox.restore()
      @subject?.stopListening()

    describe 'when selecting a node', ->
      it 'should animate focus to the selected node parent', ->
        parent = {}
        node = { parent: parent }
        @animatedTree.getAnimatedNode.withArgs(4).returns(node)
        @state.set(documentListParams: { node: { id: 4 } })
        expect(@focus.animateNode).to.have.been.calledWith(parent)

      it 'should animate focus to the root node', ->
        root = {} # no parent
        @animatedTree.getAnimatedNode.withArgs(4).returns(root)
        @state.set(documentListParams: { node: { id: 4 } })
        expect(@focus.animateNode).to.have.been.calledWith(root)

    describe 'on TreeView zoom-pan', ->
      it 'should animate pan+zoom when options.animate', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3}, { animate: true })
        expect(@focus.animatePanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should set pan+zoom', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        expect(@focus.setPanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should log', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        @sandbox.clock.tick(2000)
        expect(@log).to.have.been.calledWith('zoomed/panned', 'zoom 0.3, pan 0.1')

      it 'should throttle logs', ->
        @treeView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        @treeView.trigger('zoom-pan', { pan: 0.11, zoom: 0.3 })
        @treeView.trigger('zoom-pan', { pan: 0.12, zoom: 0.3 })
        @sandbox.clock.tick(2000)
        expect(@log).to.have.been.calledWith('zoomed/panned', 'zoom 0.3, pan 0.12')
        expect(@log).to.have.callCount(1)

    describe 'on FocusView zoom-pan', ->
      it 'should animate pan+zoom when options.animate', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3}, { animate: true })
        expect(@focus.animatePanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should set pan+zoom', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        expect(@focus.setPanAndZoom).to.have.been.calledWith(0.1, 0.3)

      it 'should log', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        @sandbox.clock.tick(2000)
        expect(@log).to.have.been.calledWith('zoomed/panned', 'zoom 0.3, pan 0.1')

      it 'should throttle logs', ->
        @focusView.trigger('zoom-pan', { pan: 0.1, zoom: 0.3 })
        @focusView.trigger('zoom-pan', { pan: 0.11, zoom: 0.3 })
        @focusView.trigger('zoom-pan', { pan: 0.12, zoom: 0.3 })
        @sandbox.clock.tick(2000)
        expect(@log).to.have.been.calledWith('zoomed/panned', 'zoom 0.3, pan 0.12')
        expect(@log).to.have.callCount(1)
