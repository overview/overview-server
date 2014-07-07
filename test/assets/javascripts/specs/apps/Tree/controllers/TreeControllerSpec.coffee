define [
  'underscore'
  'backbone'
  'apps/Tree/controllers/TreeController'
], (_, Backbone, TreeController) ->
  describe 'apps/Tree/controllers/TreeController', ->
    class MockState extends Backbone.Model

    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeTimers: true)

      @params =
        equals: -> true
        reset:
          byNode: => @params

      @state = new MockState
        document: 'document'
        documentListParams: @params
        oneDocumentSelected: false

      @log = sinon.spy()
      @focus =
        animateNode: sinon.spy()
        animatePanAndZoom: sinon.spy()
        setPanAndZoom: sinon.spy()
      @tree =
        unloadNodeChildren: sinon.spy()
        demandNode: sinon.spy()
        getRoot: sinon.stub()
        getNode: sinon.stub()
        id_tree:
          children: {}
          is_id_ancestor_of_id: sinon.stub().returns(false)
      @view =
        needsUpdate: sinon.stub()
        update: sinon.spy()
        setHoverNode: sinon.spy()
        nodeid_below: sinon.stub()
        nodeid_above: sinon.stub()
        nodeid_left: sinon.stub()
        nodeid_right: sinon.stub()

      _.extend(@view, Backbone.Events)

    afterEach ->
      @sandbox.restore()
      @subject?.stopListening()

    describe 'when it needs an update on init', ->
      beforeEach ->
        @view.needsUpdate.returns(true)

        @subject = new TreeController
          state: @state
          log: @log
          focus: @focus
          tree: @tree
          view: @view
          requestAnimationFrame: (f) -> window.setTimeout(f, 1)
        @sandbox.clock.tick(1)

      it 'should update', ->
        expect(@view.update).to.have.been.called

      it 'should keep updating until needsUpdate is false', ->
        @sandbox.clock.tick(1)
        expect(@view.update).to.have.been.calledTwice
        @view.needsUpdate.returns(false)
        @sandbox.clock.tick(1)
        expect(@view.update).not.to.have.been.calledThrice

      it 'should not double-animate if the view triggers needs-update', ->
        @sandbox.clock.tick(1)
        expect(@view.update).to.have.been.calledTwice
        @view.trigger('needs-update')
        @sandbox.clock.tick(1)
        expect(@view.update).to.have.callCount(3)

    describe 'normally', ->
      beforeEach ->
        @subject = new TreeController
          state: @state
          log: @log
          focus: @focus
          tree: @tree
          view: @view
          requestAnimationFrame: (f) -> window.setTimeout(f, 1)

      it 'should not update when it does not need an update on init', ->
        @sandbox.clock.tick(1)
        expect(@view.update).not.to.have.been.called

      it 'should animate from when the tree triggers needs-update until needsUpdate returns false', ->
        @sandbox.clock.tick(1)
        expect(@view.update).not.to.have.been.called
        @view.needsUpdate.returns(true)
        @view.trigger('needs-update')
        @sandbox.clock.tick(1)
        expect(@view.update).to.have.been.called
        @sandbox.clock.tick(1)
        expect(@view.update).to.have.been.calledTwice
        @view.needsUpdate.returns(false)
        @sandbox.clock.tick(1)
        expect(@view.update).not.to.have.been.calledThrice

      describe 'on expand', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }
          @view.trigger('expand', @node)

        it 'should log the expand', -> expect(@log).to.have.been.calledWith('expanded node', @node.id)
        it 'should expand the node', -> expect(@tree.demandNode).to.have.been.calledWith(@node.id)

      describe 'on collapse', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }

        it 'should log the collapse', ->
          @view.trigger('collapse', @node)
          expect(@log).to.have.been.calledWith('collapsed node', @node.id)

        it 'should unload the node children', ->
          @view.trigger('collapse', @node)
          expect(@tree.unloadNodeChildren).to.have.been.calledWith(@node.id)

        it 'should change the document list params, normally', ->
          @state.on('change:documentListParams', spy = sinon.spy())
          @view.trigger('collapse', @node)
          expect(spy).not.to.have.been.called

        it 'should change the document list params if a node is being unloaded', ->
          @params.node = { id: 6 }
          @params.reset.byNode = (node) -> { node: node }
          @tree.id_tree.is_id_ancestor_of_id.returns(true)

          @view.trigger('collapse', @node)

          expect(@tree.id_tree.is_id_ancestor_of_id).to.have.been.calledWith(3, 6)
          expect(@state.get('documentListParams').node).to.eq(@node)

      describe 'when clicking a node', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }

        it 'should log the click', ->
          @view.trigger('click', @node)
          expect(@log).to.have.been.calledWith('clicked node', 3)

        it 'should expand the node when its children are undefined', ->
          @view.trigger('click', @node)
          expect(@tree.demandNode).to.have.been.calledWith(@node.id)

        it 'should deselect a selected document when clicking on a selected node', ->
          params1 =
            equals: (x) -> x == params1
            reset:
              byNode: -> params1
          @state.set
            documentListParams: params1
            document: 'foo'
            oneDocumentSelected: true
          @view.trigger('click', @node)
          expect(@state.get('document')).to.be.null
          expect(@state.get('oneDocumentSelected')).to.be.false

        it 'should change parameters when changing nodes', ->
          params1 =
            reset:
              byNode: -> { foo: 'bar', equals: -> false }
          @state.set
            documentListParams: params1
            document: 'foo'
            oneDocumentSelected: true
          @view.trigger('click', @node)
          expect(@state.get('document')).to.be.null
          expect(@state.get('documentListParams').foo).to.eq('bar')
          expect(@state.get('oneDocumentSelected')).to.be.true
