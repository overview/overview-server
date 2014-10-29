define [
  'apps/Show/controllers/node_form_controller'
  'apps/Show/models/observable'
  'backbone'
], (node_form_controller, observable, Backbone) ->
  class MockNodeFormView
    observable(this)

    constructor: (@node) ->
    close: () -> @_notify('closed')
    change: (new_node) -> @_notify('change', new_node)

  # Mostly copy/pasted from tag_form_controller-spec
  describe 'controllers/node_form_controller', ->
    describe 'node_form_controller', ->
      view = undefined

      options = {
        create_form: (node) -> view = new MockNodeFormView(node)
      }

      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true)
        @node = { id: 1, description: 'node', color: '#abcdef' }
        @onDemandTree =
          saveNode: sinon.spy()
        # XXX hack: 
        @state =
          get: sinon.stub().withArgs('viewApp').returns
            onDemandTree: @onDemandTree
        @controller = node_form_controller(@node, @state, options)

      afterEach ->
        @sandbox.restore()

      it 'should create a view when called', ->
        expect(view).not.to.be.undefined

      it 'should call onDemandTree.saveNode on change', ->
        attrs = { description: 'node2' }
        view.change(attrs)
        expect(@onDemandTree.saveNode).to.have.been.calledWith(@node, attrs)
